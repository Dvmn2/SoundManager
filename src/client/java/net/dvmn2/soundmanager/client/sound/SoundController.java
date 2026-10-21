package net.dvmn2.soundmanager.client.sound;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Реестр управляемых звуков клиента + обработка команд от плагина. Всё — в основном потоке клиента. */
public final class SoundController {

    public static final int PROTOCOL = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("SoundManager");

    private static final int MAX_ACTIVE = 128;
    private static final int MAX_PAUSED = 64;

    private static final List<ManagedSound> ACTIVE = new ArrayList<>();
    /** Звуки на паузе: фактически остановлены, хранится позиция, с которой их продолжить. */
    private static final List<PlayParams> PAUSED = new ArrayList<>();

    private SoundController() {
    }

    // ------------------------------------------------------------------
    // Приём пакетов
    // ------------------------------------------------------------------

    public static void onPlay(PlayPayload p) {
        if (p.protocol() != PROTOCOL) {
            LOGGER.warn("sm_play: неподдерживаемая версия протокола {}", p.protocol());
            return;
        }
        Identifier id = Identifier.tryParse(p.sound());
        if (id == null) {
            LOGGER.warn("sm_play: некорректный id звука '{}'", p.sound());
            return;
        }
        long start = Math.max(0L, p.startMs());
        long end = p.endMs();
        if (end >= 0 && end <= start) {
            LOGGER.warn("sm_play: end ({}) <= start ({}) для {}", end, start, id);
            return;
        }
        PlayParams params = new PlayParams(
                id,
                parseCategory(p.category()),
                clamp(p.volume(), 0f, 10f),
                clamp(p.pitch(), 0.5f, 2.0f),
                PlayParams.Mode.fromId(p.mode()),
                p.x(), p.y(), p.z(),
                p.entityId(),
                start,
                end < 0 ? -1L : end,
                clampInt(p.fadeInMs(), 0, 600_000),
                clampInt(p.fadeOutMs(), 0, 600_000));
        start(params);
    }

    public static void onControl(ControlPayload c) {
        if (c.protocol() != PROTOCOL) {
            LOGGER.warn("sm_ctl: неподдерживаемая версия протокола {}", c.protocol());
            return;
        }
        String filter = c.sound();
        int duration = clampInt(c.durationMs(), 0, 600_000);

        switch (c.action()) {
            case ControlPayload.STOP -> {
                for (ManagedSound s : new ArrayList<>(ACTIVE)) {
                    if (matches(s.soundId(), filter)) {
                        s.stopWithFade(duration);
                    }
                }
                PAUSED.removeIf(pp -> matches(pp.sound(), filter));
            }
            case ControlPayload.PAUSE -> {
                for (ManagedSound s : new ArrayList<>(ACTIVE)) {
                    if (matches(s.soundId(), filter) && PAUSED.size() < MAX_PAUSED) {
                        PAUSED.add(s.snapshot());
                        s.cancel();
                        ACTIVE.remove(s);
                    }
                }
            }
            case ControlPayload.RESUME -> {
                List<PlayParams> toResume = new ArrayList<>();
                for (Iterator<PlayParams> it = PAUSED.iterator(); it.hasNext(); ) {
                    PlayParams pp = it.next();
                    if (matches(pp.sound(), filter)) {
                        toResume.add(pp);
                        it.remove();
                    }
                }
                toResume.forEach(SoundController::start);
            }
            case ControlPayload.SEEK -> {
                long pos = Math.max(0L, c.valueMs());
                for (ManagedSound s : ACTIVE) {
                    if (matches(s.soundId(), filter)) {
                        s.seek(pos);
                    }
                }
                PAUSED.replaceAll(pp -> matches(pp.sound(), filter) ? pp.withStart(pos) : pp);
            }
            case ControlPayload.VOLUME -> {
                float v = clamp(c.value(), 0f, 10f);
                for (ManagedSound s : ACTIVE) {
                    if (matches(s.soundId(), filter)) {
                        s.setVolumeTarget(v, duration);
                    }
                }
                PAUSED.replaceAll(pp -> matches(pp.sound(), filter) ? pp.withVolume(v) : pp);
            }
            case ControlPayload.PITCH -> {
                float v = clamp(c.value(), 0.5f, 2.0f);
                for (ManagedSound s : ACTIVE) {
                    if (matches(s.soundId(), filter)) {
                        s.setPitchTarget(v, duration);
                    }
                }
                PAUSED.replaceAll(pp -> matches(pp.sound(), filter) ? pp.withPitch(v) : pp);
            }
            default -> LOGGER.warn("sm_ctl: неизвестное действие {}", c.action());
        }
    }

    // ------------------------------------------------------------------
    // Жизненный цикл
    // ------------------------------------------------------------------

    private static void start(PlayParams params) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            LOGGER.warn("Слишком много управляемых звуков ({}), запуск {} пропущен", MAX_ACTIVE, params.sound());
            return;
        }
        ManagedSound sound = new ManagedSound(params);
        ACTIVE.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }

    /** END_CLIENT_TICK: выкидываем завершившиеся звуки. */
    public static void onTick(MinecraftClient client) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        SoundManager manager = client.getSoundManager();
        // Первые тики после play() звук ещё может числиться «не играющим» — даём запас.
        ACTIVE.removeIf(s -> s.isDone() || (s.ticksAlive() > 10 && !manager.isPlaying(s)));
    }

    /** Остановить всё и забыть паузы (отключение от сервера, команда /smstop). */
    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            SoundManager manager = client.getSoundManager();
            for (ManagedSound s : ACTIVE) {
                s.cancel();
                manager.stop(s);
            }
        }
        ACTIVE.clear();
        PAUSED.clear();
    }

    /** Менеджер OpenAL-источника играющего звука (или null, если его ещё нет). */
    public static Channel.SourceManager sourceFor(SoundInstance instance) {
        SoundManager manager = MinecraftClient.getInstance().getSoundManager();
        SoundSystem system = ((SoundManagerAccessor) (Object) manager).soundmanager$getSoundSystem();
        return ((SoundSystemAccessor) (Object) system).soundmanager$getSources().get(instance);
    }

    // ------------------------------------------------------------------

    private static boolean matches(Identifier sound, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Identifier f = Identifier.tryParse(filter);
        return f != null && f.equals(sound);
    }

    private static SoundCategory parseCategory(String name) {
        for (SoundCategory c : SoundCategory.values()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return SoundCategory.MASTER;
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
