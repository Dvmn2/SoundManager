package net.dvmn2.soundmanager.client.sound;

import net.dvmn2.soundmanager.client.SoundManagerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import org.jetbrains.annotations.Nullable;

/**
 * Local preview of one sound for the GUI.
 * <p>
 * Uses the same {@link ManagedSound} as sounds sent by the server, so the preview matches what other players hear
 * (apart from their own volume sliders and volume cap). It is kept apart from {@link SoundController}: server
 * packets such as "stop everything" never touch the preview and the preview never counts as a managed sound.
 * All methods run on the client thread.
 */
public final class PreviewController {

    /** Ticks after start during which a sound may still be reported as "not playing". */
    private static final int SETTLE_TICKS = 10;

    @Nullable
    private static ManagedSound current;
    /** State to continue from after a pause; non-null only while paused. */
    @Nullable
    private static PlayParams paused;

    private PreviewController() {
    }

    /** Outcome of {@link #start(PlayParams)} and {@link #resume()}. */
    public enum StartResult { STARTED, DISABLED, SEEK_UNAVAILABLE, NOTHING_TO_RESUME }

    /** Stops the previous preview and starts a new one. The player's {@code enabled} switch is respected. */
    public static StartResult start(PlayParams params) {
        if (!SoundManagerConfig.get().enabled) {
            return StartResult.DISABLED;
        }
        if (params.startMs() > 0 && !SoundController.isSeekSupported()) {
            return StartResult.SEEK_UNAVAILABLE;
        }
        stop();
        ManagedSound sound = new ManagedSound(params);
        current = sound;
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(sound);
        PreviewMute.onTick(client);
        return StartResult.STARTED;
    }

    /** Pauses the preview; the position is kept for {@link #resume()}. Does nothing if nothing is playing. */
    public static void pause() {
        ManagedSound sound = current;
        if (sound == null) {
            return;
        }
        paused = sound.snapshot();
        sound.cancel();
        current = null;
    }

    /** Continues a paused preview from where it stopped. */
    public static StartResult resume() {
        PlayParams params = paused;
        if (params == null) {
            return StartResult.NOTHING_TO_RESUME;
        }
        paused = null;
        return start(params);
    }

    /** Stops the preview and forgets the paused position. */
    public static void stop() {
        ManagedSound sound = current;
        current = null;
        paused = null;
        if (sound != null) {
            sound.cancel();
        }
    }

    public static boolean isPlaying() {
        return current != null;
    }

    public static boolean isPaused() {
        return paused != null;
    }

    /** True while a preview plays or is paused, i.e. until it is stopped or finishes. */
    public static boolean isActive() {
        return current != null || paused != null;
    }

    /** True if the sound instance is the playing preview. */
    public static boolean isPreview(SoundInstance instance) {
        return instance == current;
    }

    /** Position on the sound timeline in milliseconds, or -1 if there is no preview. */
    public static long positionMs() {
        ManagedSound sound = current;
        if (sound != null) {
            return sound.positionMs();
        }
        PlayParams params = paused;
        return params != null ? params.startMs() : -1L;
    }

    /** END_CLIENT_TICK: forgets a preview that has finished. */
    public static void onTick(MinecraftClient client) {
        ManagedSound sound = current;
        if (sound == null) {
            return;
        }
        if (sound.isDone() || (sound.ticksAlive() > SETTLE_TICKS && !client.getSoundManager().isPlaying(sound))) {
            current = null;
        }
    }
}
