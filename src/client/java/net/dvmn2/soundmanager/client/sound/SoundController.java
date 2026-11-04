package net.dvmn2.soundmanager.client.sound;

import net.dvmn2.soundmanager.client.SoundManagerConfig;
import net.dvmn2.soundmanager.client.mixin.SoundManagerAccessor;
import net.dvmn2.soundmanager.client.mixin.SoundSystemAccessor;
import net.dvmn2.soundmanager.client.network.ControlPayload;
import net.dvmn2.soundmanager.client.network.PlayPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry of the managed sounds of the client and handler of the plugin packets.
 * <p>
 * All methods except {@link #isSeekSupported()} and {@link #onSeekUnavailable(Throwable)} run on the client thread.
 */
public final class SoundController {

    /** Protocol version of the plugin-mod packets; bumped on any format change. */
    public static final int PROTOCOL = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");

    private static final int MAX_ACTIVE = 128;
    private static final int MAX_PAUSED = 64;
    /** Ticks after play() during which a sound may still be reported as "not playing". */
    private static final int SETTLE_TICKS = 10;

    private static final List<ManagedSound> ACTIVE = new ArrayList<>();
    /** Paused sounds: actually stopped, with the position to continue from. */
    private static final List<PlayParams> PAUSED = new ArrayList<>();
    private static final AtomicBoolean SEEK_FAILURE_LOGGED = new AtomicBoolean();

    private static volatile boolean seekSupported = true;

    private SoundController() {
    }

    // ---------- Packets ----------

    /** Handles a play packet. Invalid or unsupported packets are ignored. */
    public static void onPlay(PlayPayload p) {
        SoundManagerConfig config = SoundManagerConfig.get();
        if (!config.enabled || p.protocol() != PROTOCOL) {
            return;
        }
        Identifier id = Identifier.tryParse(p.sound());
        long start = Math.max(0L, p.startMs());
        long end = p.endMs() < 0 ? -1L : p.endMs();
        if (id == null || (end >= 0 && end <= start)) {
            LOGGER.debug("Ignored an invalid play packet for '{}'", p.sound());
            return;
        }
        float maxVolume = Math.min(config.maxVolume, SoundManagerConfig.MAX_VOLUME);
        start(new PlayParams(
                id,
                parseCategory(p.category()),
                clamp(p.volume(), 0f, maxVolume),
                clamp(p.pitch(), PlayParams.MIN_PITCH, PlayParams.MAX_PITCH),
                PlayParams.Mode.fromId(p.mode()),
                finiteOrZero(p.x()), finiteOrZero(p.y()), finiteOrZero(p.z()),
                p.entityId(),
                start,
                end,
                clampInt(p.fadeInMs(), 0, PlayParams.MAX_FADE_MS),
                clampInt(p.fadeOutMs(), 0, PlayParams.MAX_FADE_MS)));
    }

    /** Handles a control packet. Invalid or unsupported packets are ignored. */
    public static void onControl(ControlPayload c) {
        if (!SoundManagerConfig.get().enabled || c.protocol() != PROTOCOL) {
            return;
        }
        String filter = c.sound();
        int duration = clampInt(c.durationMs(), 0, PlayParams.MAX_FADE_MS);

        switch (c.action()) {
            case ControlPayload.STOP -> stop(filter, duration);
            case ControlPayload.PAUSE -> pause(filter);
            case ControlPayload.RESUME -> resume(filter);
            case ControlPayload.SEEK -> seek(filter, Math.max(0L, c.valueMs()));
            case ControlPayload.VOLUME -> setVolume(filter, c.value(), duration);
            case ControlPayload.PITCH -> setPitch(filter, c.value(), duration);
            default -> LOGGER.debug("Ignored an unknown control action {}", c.action());
        }
    }

    private static void stop(String filter, int fadeMs) {
        for (ManagedSound sound : new ArrayList<>(ACTIVE)) {
            if (matches(sound.soundId(), filter)) {
                sound.stopWithFade(fadeMs);
            }
        }
        PAUSED.removeIf(paused -> matches(paused.sound(), filter));
    }

    private static void pause(String filter) {
        for (ManagedSound sound : new ArrayList<>(ACTIVE)) {
            if (matches(sound.soundId(), filter) && PAUSED.size() < MAX_PAUSED) {
                PAUSED.add(sound.snapshot());
                sound.cancel();
                ACTIVE.remove(sound);
            }
        }
    }

    private static void resume(String filter) {
        List<PlayParams> toResume = new ArrayList<>();
        for (Iterator<PlayParams> it = PAUSED.iterator(); it.hasNext(); ) {
            PlayParams paused = it.next();
            if (matches(paused.sound(), filter)) {
                toResume.add(paused);
                it.remove();
            }
        }
        toResume.forEach(SoundController::start);
    }

    private static void seek(String filter, long positionMs) {
        if (!seekSupported) {
            return;
        }
        for (ManagedSound sound : ACTIVE) {
            if (matches(sound.soundId(), filter)) {
                sound.seek(positionMs);
            }
        }
        PAUSED.replaceAll(paused -> matches(paused.sound(), filter) ? paused.withStart(positionMs) : paused);
    }

    private static void setVolume(String filter, float requested, int rampMs) {
        float maxVolume = Math.min(SoundManagerConfig.get().maxVolume, SoundManagerConfig.MAX_VOLUME);
        float value = clamp(requested, 0f, maxVolume);
        for (ManagedSound sound : ACTIVE) {
            if (matches(sound.soundId(), filter)) {
                sound.setVolumeTarget(value, rampMs);
            }
        }
        PAUSED.replaceAll(paused -> matches(paused.sound(), filter) ? paused.withVolume(value) : paused);
    }

    private static void setPitch(String filter, float requested, int rampMs) {
        float value = clamp(requested, PlayParams.MIN_PITCH, PlayParams.MAX_PITCH);
        for (ManagedSound sound : ACTIVE) {
            if (matches(sound.soundId(), filter)) {
                sound.setPitchTarget(value, rampMs);
            }
        }
        PAUSED.replaceAll(paused -> matches(paused.sound(), filter) ? paused.withPitch(value) : paused);
    }

    // ---------- Lifecycle ----------

    private static void start(PlayParams params) {
        if (params.startMs() > 0 && !seekSupported) {
            return; // a start offset needs seeking, which is unavailable
        }
        if (ACTIVE.size() >= MAX_ACTIVE) {
            LOGGER.debug("Too many managed sounds ({}); skipped {}", MAX_ACTIVE, params.sound());
            return;
        }
        ManagedSound sound = new ManagedSound(params);
        ACTIVE.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }

    /** END_CLIENT_TICK: drops finished sounds. */
    public static void onTick(MinecraftClient client) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        SoundManager manager = client.getSoundManager();
        ACTIVE.removeIf(sound -> sound.isDone() || (sound.ticksAlive() > SETTLE_TICKS && !manager.isPlaying(sound)));
    }

    /** Stops everything and forgets paused sounds (disconnect, {@code /sm off}). */
    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            for (ManagedSound sound : ACTIVE) {
                sound.cancel();
            }
        }
        ACTIVE.clear();
        PAUSED.clear();
    }

    /** Number of managed sounds that are currently playing or starting. */
    public static int activeCount() {
        return ACTIVE.size();
    }

    // ---------- Seek support ----------

    /** False after the OpenAL access failed once; sounds that need a start offset are then skipped. */
    public static boolean isSeekSupported() {
        return seekSupported;
    }

    /** Disables seeking and logs the cause once. May be called from the sound engine thread. */
    static void onSeekUnavailable(Throwable cause) {
        seekSupported = false;
        if (SEEK_FAILURE_LOGGED.compareAndSet(false, true)) {
            LOGGER.error("Seeking is unavailable; sounds that need a start offset will be skipped", cause);
        }
    }

    /** The OpenAL source manager of a playing sound, or null if it does not exist yet. */
    @Nullable
    static Channel.SourceManager sourceFor(SoundInstance instance) {
        SoundManager manager = MinecraftClient.getInstance().getSoundManager();
        SoundSystem system = ((SoundManagerAccessor) (Object) manager).soundmanager$getSoundSystem();
        return ((SoundSystemAccessor) (Object) system).soundmanager$getSources().get(instance);
    }

    // ---------- Helpers ----------

    private static boolean matches(Identifier sound, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        Identifier parsed = Identifier.tryParse(filter);
        return parsed != null && parsed.equals(sound);
    }

    private static SoundCategory parseCategory(String name) {
        for (SoundCategory category : SoundCategory.values()) {
            if (category.getName().equals(name)) {
                return category;
            }
        }
        return SoundCategory.MASTER;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
