package net.dvmn2.soundmanager.client.sound;

import net.dvmn2.soundmanager.client.mixin.SourceAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * Звук с точным управлением: старт с места, конец, fade in/out, плавное изменение громкости/высоты, перемотка.
 * <p>
 * Как это работает:
 * <ul>
 *   <li>Есть собственная «шкала времени» {@link #posSec}: она идёт в реальном времени, умноженном на pitch.
 *       По ней проверяется конец и считаются fade'ы (точность — один клиентский тик, 50 мс).</li>
 *   <li>Старт с середины и перемотка делаются через {@code alSourcef(AL_SEC_OFFSET)} на OpenAL-источнике.
 *       Пока перемотка не выполнена, звук тихий (MIN_VOLUME), чтобы не было слышно начало с нуля.</li>
 * </ul>
 */
public final class ManagedSound extends MovingSoundInstance {

    /** Ненулевой минимум: при нулевой громкости SoundSystem вообще не запускает звук. */
    private static final float MIN_VOLUME = 0.0001f;
    /** Сколько тиков ждём успешной перемотки, потом сдаёмся и останавливаем звук. */
    private static final int SEEK_TIMEOUT_TICKS = 100;
    /** Сколько тиков звук привязанной сущности может «отсутствовать» на клиенте. */
    private static final int ENTITY_MISSING_TICKS = 40;

    private final PlayParams params;
    private final Ramp volumeRamp;
    private final Ramp pitchRamp;

    // --- шкала времени ---
    private double posSec;
    private long lastNs;
    private int ticksAlive;
    private int waitTicks;
    private int missingTicks;

    // --- перемотка (флаги пишутся и из потока звукового движка) ---
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
        this.posSec = params.startMs() / 1000.0;
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

        // Если стартуем с нуля — перемотка не нужна.
        this.ready = params.startMs() <= 0;
        if (this.ready) {
            this.fadeInStarted = true;
            this.fadeInStartNs = now;
        }
        this.volume = computeVolume(now);
    }

    // ------------------------------------------------------------------
    // Тик
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        long now = System.nanoTime();

        float p = clampPitch(pitchRamp.get(now));
        this.pitch = p;

        double dt = (now - lastNs) / 1_000_000_000.0;
        lastNs = now;
        posSec += dt * p;
        ticksAlive++;
        waitTicks++;

        if (params.mode() == PlayParams.Mode.ENTITY) {
            followEntity();
            if (isDone()) {
                return;
            }
        }

        long posMs = (long) (posSec * 1000.0);
        if (params.endMs() >= 0 && posMs >= params.endMs()) {
            setDone();
            return;
        }
        if (stopStartNs >= 0 && now - stopStartNs >= stopFadeNs) {
            setDone();
            return;
        }
        if (seekFailed) {
            // Позиция за пределами звука или OpenAL отказал.
            setDone();
            return;
        }

        if (!ready) {
            if (waitTicks > SEEK_TIMEOUT_TICKS) {
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

    private float computeVolume(long now) {
        if (!ready) {
            return MIN_VOLUME;
        }
        float v = volumeRamp.get(now);
        if (params.fadeInMs() > 0 && fadeInStarted) {
            v *= (float) clamp01((now - fadeInStartNs) / (params.fadeInMs() * 1_000_000.0));
        }
        if (params.endMs() >= 0 && params.fadeOutMs() > 0) {
            double posMs = posSec * 1000.0;
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

    // ------------------------------------------------------------------
    // Перемотка через OpenAL
    // ------------------------------------------------------------------

    private void trySeek() {
        if (seekInFlight) {
            return;
        }
        Channel.SourceManager manager = SoundController.sourceFor(this);
        if (manager == null) {
            return; // SoundSystem ещё не создал источник
        }
        final int generation = seekGeneration;
        final float target = (float) posSec;
        seekInFlight = true;
        // run() исполняется в потоке звукового движка — там есть OpenAL-контекст.
        manager.run(source -> {
            try {
                int pointer = ((SourceAccessor) (Object) source).soundmanager$getPointer();
                if (AL10.alGetSourcei(pointer, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    return; // буфер ещё грузится — попробуем на следующем тике
                }
                AL10.alGetError(); // сбросить старую ошибку
                AL10.alSourcef(pointer, AL11.AL_SEC_OFFSET, target);
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    seekFailed = true;
                } else if (generation == seekGeneration) {
                    ready = true;
                }
            } finally {
                seekInFlight = false;
            }
        });
    }

    // ------------------------------------------------------------------
    // Управление (вызывается из основного потока клиента)
    // ------------------------------------------------------------------

    /** Перемотать на позицию (мс). Звук на мгновение тихий, пока OpenAL не выполнит перемотку. */
    public void seek(long positionMs) {
        posSec = Math.max(0, positionMs) / 1000.0;
        seekGeneration++;
        ready = false;
        waitTicks = 0;
        this.volume = MIN_VOLUME;
    }

    /** Плавно (или сразу, если rampMs = 0) привести громкость к значению. */
    public void setVolumeTarget(float target, int rampMs) {
        volumeRamp.retarget(target, rampMs, System.nanoTime());
    }

    public void setPitchTarget(float target, int rampMs) {
        pitchRamp.retarget(clampPitch(target), rampMs, System.nanoTime());
    }

    /** Остановить с затуханием (0 = сразу). */
    public void stopWithFade(int fadeMs) {
        if (fadeMs <= 0) {
            cancel();
            return;
        }
        if (stopStartNs < 0) {
            stopStartNs = System.nanoTime();
            stopFadeNs = fadeMs * 1_000_000L;
        }
    }

    /** Немедленная остановка. */
    public void cancel() {
        setDone();
        MinecraftClient.getInstance().getSoundManager().stop(this);
    }

    /** Состояние для возобновления после паузы: старт = текущая позиция, без fade-in. */
    public PlayParams snapshot() {
        return new PlayParams(params.sound(), params.category(), volumeRamp.to, pitchRamp.to,
                params.mode(), this.x, this.y, this.z, params.entityId(),
                (long) (posSec * 1000.0), params.endMs(), 0, params.fadeOutMs());
    }

    public Identifier soundId() {
        return params.sound();
    }

    public int ticksAlive() {
        return ticksAlive;
    }

    // ------------------------------------------------------------------

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Линейный переход значения во времени. */
    private static final class Ramp {
        float from;
        float to;
        long startNs;
        long durNs;

        Ramp(float value) {
            this.from = value;
            this.to = value;
        }

        float get(long now) {
            if (durNs <= 0 || now >= startNs + durNs) {
                return to;
            }
            return from + (to - from) * ((now - startNs) / (float) durNs);
        }

        void retarget(float target, int durMs, long now) {
            this.from = get(now);
            this.to = target;
            this.startNs = now;
            this.durNs = Math.max(0, durMs) * 1_000_000L;
        }
    }
}
