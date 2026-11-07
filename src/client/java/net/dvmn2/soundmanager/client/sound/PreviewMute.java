package net.dvmn2.soundmanager.client.sound;

import net.dvmn2.soundmanager.client.SoundManagerConfig;
import net.dvmn2.soundmanager.client.mixin.SoundManagerAccessor;
import net.dvmn2.soundmanager.client.mixin.SoundSystemAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Makes every other sound of the game inaudible while the window previews a sound, and restores them afterwards.
 * <p>
 * The sounds are not stopped or paused: their volume is lowered to an inaudible level, so they keep their position
 * and (for sounds of the server) their timing. Sounds of the interface (button clicks) are left alone. Client
 * thread only.
 * <p>
 * Two mechanisms work together. A {@link ManagedSound} asks {@link #shouldSilence} while it computes its own
 * volume, so the game itself applies the quiet volume every tick and nothing is heard between ticks. Any other
 * sound gets its source volume lowered from the client tick; a sound that the game re-tunes every tick (a vanilla
 * tickable sound) may therefore still be heard for a moment, and so may a sound that starts between two ticks.
 * <p>
 * Reaching the sound sources uses the same accessors as seeking; if anything goes wrong, the feature switches
 * itself off for the rest of the session and logs once.
 */
public final class PreviewMute {

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");

    /** Volume of a muted source; not zero, because the game stops a source whose volume is zero. */
    private static final float MUTED_VOLUME = 0.0001f;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String UI_PATH_PREFIX = "ui.";

    /** Sounds muted so far (by identity); lets the restore touch only what was muted. */
    private static final Set<SoundInstance> MUTED = Collections.newSetFromMap(new IdentityHashMap<>());

    private static boolean failed;

    private PreviewMute() {
    }

    /**
     * True if the sound must be inaudible now because a preview is active. For sounds that set their own volume
     * (see {@link ManagedSound}); the preview itself and interface sounds are never silenced.
     */
    public static boolean shouldSilence(SoundInstance instance) {
        return !failed && isWanted() && !PreviewController.isPreview(instance) && !isInterfaceSound(instance);
    }

    /**
     * END_CLIENT_TICK: mutes while a preview is active, restores when it is over. Also called right after a preview
     * starts, so the other sounds do not stay audible until the next tick.
     */
    public static void onTick(MinecraftClient client) {
        if (failed) {
            return;
        }
        boolean wanted = isWanted();
        if (!wanted && MUTED.isEmpty()) {
            return;
        }
        try {
            Map<SoundInstance, Channel.SourceManager> sources = sourcesOf(client);
            MUTED.removeIf(instance -> !sources.containsKey(instance));
            if (wanted) {
                muteOthers(sources);
            } else {
                restore(client, sources);
            }
        } catch (RuntimeException | LinkageError ex) {
            failed = true;
            MUTED.clear();
            LOGGER.error("Muting other sounds during preview failed and is switched off", ex);
        }
    }

    private static void muteOthers(Map<SoundInstance, Channel.SourceManager> sources) {
        for (Map.Entry<SoundInstance, Channel.SourceManager> entry : sources.entrySet()) {
            SoundInstance instance = entry.getKey();
            if (PreviewController.isPreview(instance) || isInterfaceSound(instance)) {
                continue;
            }
            // A managed sound lowers its own volume (shouldSilence), so it needs only the first, immediate mute. Another
            // sound that updates itself every tick gets its volume back from the game and is muted again each
            // tick; any other sound keeps the volume that is set once.
            boolean firstTime = MUTED.add(instance);
            boolean retuned = instance instanceof TickableSoundInstance && !(instance instanceof ManagedSound);
            if (firstTime || retuned) {
                entry.getValue().run(source -> source.setVolume(MUTED_VOLUME));
            }
        }
    }

    private static void restore(MinecraftClient client, Map<SoundInstance, Channel.SourceManager> sources) {
        for (SoundInstance instance : MUTED) {
            Channel.SourceManager manager = sources.get(instance);
            if (manager == null || instance instanceof TickableSoundInstance) {
                continue; // tickable sounds set their own volume again on the next tick
            }
            float volume = restoredVolume(client, instance);
            manager.run(source -> source.setVolume(volume));
        }
        MUTED.clear();
    }

    /** The volume the game gives a sound: its own volume times the slider of its category (master is the listener). */
    private static float restoredVolume(MinecraftClient client, SoundInstance instance) {
        SoundCategory category = instance.getCategory();
        float slider = category == SoundCategory.MASTER ? 1.0f : client.options.getSoundVolume(category);
        return Math.max(0.0f, Math.min(1.0f, instance.getVolume() * slider));
    }

    private static boolean isWanted() {
        SoundManagerConfig config = SoundManagerConfig.get();
        return config.enabled && config.previewMuteOthers && PreviewController.isActive();
    }

    private static boolean isInterfaceSound(SoundInstance instance) {
        if (instance.getCategory() == SoundCategory.UI) {
            return true;
        }
        Identifier id = instance.getId();
        return MINECRAFT_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(UI_PATH_PREFIX);
    }

    private static Map<SoundInstance, Channel.SourceManager> sourcesOf(MinecraftClient client) {
        SoundSystem system = ((SoundManagerAccessor) (Object) client.getSoundManager()).soundmanager$getSoundSystem();
        return ((SoundSystemAccessor) (Object) system).soundmanager$getSources();
    }
}
