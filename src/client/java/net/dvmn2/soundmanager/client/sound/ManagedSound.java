package net.dvmn2.soundmanager.client.sound;

import net.dvmn2.soundmanager.client.mixin.SourceAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.Source;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * Sound with precise control: start offset, end point, fade in/out, smooth volume and pitch changes, seeking.
 * <p>
 * Created and controlled on the client thread; only the seek flags are also written from the sound engine thread.
 * <ul>
 *   <li>The sound keeps its own timeline {@link #posSec}, advanced by real time multiplied by pitch. The end
 *       point and the fades are computed from it (accuracy: one client tick, 50 ms).</li>
 *   <li>Starting from the middle and seeking use {@code alSourcef(AL_SEC_OFFSET)} on the OpenAL source. Until the
 *       seek is done the sound stays almost silent ({@link #MIN_VOLUME}) so the beginning is not heard.</li>
 * </ul>
 */
public final class ManagedSound extends MovingSoundInstance {

    /** Non-zero minimum: the sound system does not start a sound with zero volume at all. */
    private static final float MIN_VOLUME = 0.0001f;
    /** Ticks to wait for a successful seek before giving up and stopping the sound. */
    private static final int SEEK_TIMEOUT_TICKS = 100;
    /** Ticks an entity-bound sound may stay without its entity on the client. */
    private static final int ENTITY_MISSING_TICKS = 40;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MS = 1_000_000.0;
    private static final double MS_PER_SECOND = 1000.0;

    private final PlayParams params;
    private final Ramp volumeRamp;
    private final Ramp pitchRamp;

    private double posSec;
    private long lastNs;
    private int ticksAlive;
    private int waitTicks;
    private int missingTicks;

    private volatile boolean ready;
    private volatile boolean seekFailed;
    private volatile boolean seekInFlight;
    private volatile int seekGeneration;

    private boolean fadeInStarted;
    private long fadeInStartNs;

    private long stopStartNs = -1;
    private long stopFadeNs;

    public ManagedSound(PlayParams params) {
        super(SoundEvent.of(params.sound()), params.category(), Random.create());
        this.params = params;

        long now = System.nanoTime();
        this.lastNs = now;
        this.posSec = params.startMs() / MS_PER_SECOND;
        this.volumeRamp = new Ramp(params.volume());
        this.pitchRamp = new Ramp(params.pitch());
        this.pitch = params.pitch();
        this.repeat = false;

        switch (params.mode()) {
            case GLOBAL -> {
                this.relative = true;
                this.attenuationType = SoundInstance.AttenuationType.NONE;
                this.x = 0;
                this.y = 0;
                this.z = 0;
            }
            case POSITION, ENTITY -> {
                this.relative = false;
                this.attenuationType = SoundInstance.AttenuationType.LINEAR;
                this.x = params.x();
                this.y = params.y();
                this.z = params.z();
            }
        }

        // Starting from zero needs no seek.
        this.ready = params.startMs() <= 0;
        if (this.ready) {
            this.fadeInStarted = true;
            this.fadeInStartNs = now;
        }
        this.volume = computeVolume(now);
    }

    // ---------- Tick ----------

    @Override
    public void tick() {
        long now = System.nanoTime();
        advanceTimeline(now);

        if (params.mode() == PlayParams.Mode.ENTITY) {
            followEntity();
        }
        if (isDone() || hasFinished(now)) {
            setDone();
            return;
        }

        if (!ready) {
            if (waitTicks > SEEK_TIMEOUT_TICKS || !SoundController.isSeekSupported()) {
                setDone();
                return;
            }
            trySeek();
        } else if (!fadeInStarted) {
            fadeInStarted = true;
            fadeInStartNs = now;
        }

        this.volume = computeVolume(now);
    }

    private void advanceTimeline(long now) {
        float currentPitch = clampPitch(pitchRamp.get(now));
        this.pitch = currentPitch;
        double dtSec = (now - lastNs) / NANOS_PER_SECOND;
        lastNs = now;
        posSec += dtSec * currentPitch;
        ticksAlive++;
        waitTicks++;
    }

    private boolean hasFinished(long now) {
        long posMs = (long) (posSec * MS_PER_SECOND);
        if (params.endMs() >= 0 && posMs >= params.endMs()) {
            return true;
        }
        if (stopStartNs >= 0 && now - stopStartNs >= stopFadeNs) {
            return true;
        }
        // The seek position is outside the sound or OpenAL refused it.
        return seekFailed;
    }

    private float computeVolume(long now) {
        if (!ready) {
            return MIN_VOLUME;
        }
        float v = volumeRamp.get(now);
        if (params.fadeInMs() > 0 && fadeInStarted) {
            v *= (float) clamp01((now - fadeInStartNs) / (params.fadeInMs() * NANOS_PER_MS));
        }
        if (params.endMs() >= 0 && params.fadeOutMs() > 0) {
            double posMs = posSec * MS_PER_SECOND;
            v *= (float) clamp01((params.endMs() - posMs) / params.fadeOutMs());
        }
        if (stopStartNs >= 0 && stopFadeNs > 0) {
            v *= (float) clamp01(1.0 - (now - stopStartNs) / (double) stopFadeNs);
        }
        return Math.max(MIN_VOLUME, v);
    }

    private void followEntity() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            setDone();
            return;
        }
        Entity entity = world.getEntityById(params.entityId());
        if (entity != null) {
            missingTicks = 0;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        } else if (++missingTicks > ENTITY_MISSING_TICKS) {
            setDone();
        }
    }

    // ---------- Seeking through OpenAL ----------

    private void trySeek() {
        if (seekInFlight) {
            return;
        }
        Channel.SourceManager manager;
        try {
            manager = SoundController.sourceFor(this);
        } catch (RuntimeException | LinkageError ex) {
            SoundController.onSeekUnavailable(ex);
            setDone();
            return;
        }
        if (manager == null) {
            return; // the sound system has not created the source yet
        }
        final int generation = seekGeneration;
        final float target = (float) posSec;
        seekInFlight = true;
        // run() executes on the sound engine thread, the only one with an OpenAL context.
        manager.run(source -> {
            try {
                seekSource(source, generation, target);
            } catch (RuntimeException | LinkageError ex) {
                SoundController.onSeekUnavailable(ex);
                seekFailed = true;
            } finally {
                seekInFlight = false;
            }
        });
    }

    private void seekSource(Source source, int generation, float targetSec) {
        int pointer = ((SourceAccessor) (Object) source).soundmanager$getPointer();
        if (AL10.alGetSourcei(pointer, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            return; // the buffer is still loading; try again on the next tick
        }
        AL10.alGetError(); // clear a stale error
        AL10.alSourcef(pointer, AL11.AL_SEC_OFFSET, targetSec);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            seekFailed = true;
        } else if (generation == seekGeneration) {
            ready = true;
        }
    }

    // ---------- Control (client thread) ----------

    /** Seeks to the position in milliseconds. The sound is almost silent until OpenAL performs the seek. */
    public void seek(long positionMs) {
        posSec = Math.max(0, positionMs) / MS_PER_SECOND;
        seekGeneration++;
        ready = false;
        waitTicks = 0;
        this.volume = MIN_VOLUME;
    }

    /** Moves the volume to the target, smoothly over {@code rampMs} (0 = immediately). */
    public void setVolumeTarget(float target, int rampMs) {
        volumeRamp.retarget(target, rampMs, System.nanoTime());
    }

    /** Moves the pitch to the target, smoothly over {@code rampMs} (0 = immediately). */
    public void setPitchTarget(float target, int rampMs) {
        pitchRamp.retarget(clampPitch(target), rampMs, System.nanoTime());
    }

    /** Stops with a fade-out of {@code fadeMs} (0 = immediately). */
    public void stopWithFade(int fadeMs) {
        if (fadeMs <= 0) {
            cancel();
            return;
        }
        if (stopStartNs < 0) {
            stopStartNs = System.nanoTime();
            stopFadeNs = fadeMs * (long) NANOS_PER_MS;
        }
    }

    /** Stops immediately. */
    public void cancel() {
        setDone();
        MinecraftClient.getInstance().getSoundManager().stop(this);
    }

    /** State for resuming after a pause: starts at the current position, without fade-in. */
    public PlayParams snapshot() {
        return new PlayParams(params.sound(), params.category(), volumeRamp.target(), pitchRamp.target(),
                params.mode(), this.x, this.y, this.z, params.entityId(),
                (long) (posSec * MS_PER_SECOND), params.endMs(), 0, params.fadeOutMs());
    }

    public Identifier soundId() {
        return params.sound();
    }

    public int ticksAlive() {
        return ticksAlive;
    }

    // ---------- Helpers ----------

    private static float clampPitch(float pitch) {
        return Math.max(PlayParams.MIN_PITCH, Math.min(PlayParams.MAX_PITCH, pitch));
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1);
    }
}
