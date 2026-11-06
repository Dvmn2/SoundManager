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
 * and (for sounds of the server) their timing. Sounds of the interface (button clicks) are left alone. Runs from
 * the client tick, so a sound that starts between two ticks may be heard for up to one tick. Client thread only.
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

    /** END_CLIENT_TICK: mutes while a preview is active, restores when it is over. */
    public static void onTick(MinecraftClient client) {
        if (failed) {
            return;
        }
        SoundManagerConfig config = SoundManagerConfig.get();
        boolean wanted = config.enabled && config.previewMuteOthers && PreviewController.isActive();
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
            // A sound that updates itself every tick gets its volume back from the game, so it is muted again
            // each tick; any other sound keeps the volume that is set once.
            boolean firstTime = MUTED.add(instance);
            if (firstTime || instance instanceof TickableSoundInstance) {
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
