package net.dvmn2.soundmanager.client.gui;

import net.dvmn2.soundmanager.client.SoundManagerConfig;
import net.dvmn2.soundmanager.client.preset.PresetDto;
import net.dvmn2.soundmanager.client.sound.PlayParams;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Editable values of the GUI: every argument of {@code /soundmanager play|playat|playon}.
 * <p>
 * Times are in seconds, as in the commands. Used on the client thread only. Fields are package-private on purpose:
 * the screen edits them directly and calls {@code onFormChanged()} afterwards.
 */
final class PlayForm {

    /** Upper bound of the volume the command accepts. */
    static final float MAX_VOLUME = 10.0f;
    /** Upper bound of start and end offsets: 24 hours, seconds. */
    static final double MAX_TIME_SEC = 24.0 * 60 * 60;
    /** Upper bound of fades: 10 minutes, seconds. */
    static final double MAX_FADE_SEC = 10.0 * 60;
    /** Largest coordinate that fits in the world, blocks. */
    static final double MAX_COORDINATE = 30_000_000.0;
    private static final double DEFAULT_END_SEC = 10.0;
    private static final long NOT_SET = -1L;

    /** The command variant: where the sound is heard. */
    enum Mode {
        PLAY("play"), PLAY_AT("playat"), PLAY_ON("playon");

        private final String literal;

        Mode(String literal) {
            this.literal = literal;
        }

        String literal() {
            return literal;
        }

        Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        static Mode fromLiteral(String literal) {
            for (Mode mode : values()) {
                if (mode.literal.equals(literal)) {
                    return mode;
                }
            }
            return PLAY;
        }
    }

    /** Why the form cannot be previewed or sent. */
    enum Issue { NONE, NO_SOUND, NO_TARGETS, NO_ENTITY, BAD_RANGE, TOO_LONG }

    /** An entity of the client world picked for {@code playon}; the selector is its UUID. */
    record EntityRef(int id, String uuid, String label, double x, double y, double z, double distance) {
    }

    Mode mode = Mode.PLAY;
    String targets = "@s";
    @Nullable
    Identifier sound;
    SoundCategory category = SoundCategory.MASTER;
    float volume = 1.0f;
    float pitch = 1.0f;
    double startSec;
    double endSec = DEFAULT_END_SEC;
    boolean toEnd = true;
    double fadeInSec;
    double fadeOutSec;
    double x;
    double y;
    double z;
    @Nullable
    EntityRef entity;

    /**
     * Checks the form. Preview needs a sound, a valid range and, for {@code playon}, an entity; sending also needs
     * targets and a command that fits in a chat command.
     *
     * @param forSend       true to check the requirements of sending as well
     * @param commandLength length of the command text, characters (only used when {@code forSend} is true)
     */
    Issue check(boolean forSend, int commandLength) {
        if (sound == null) {
            return Issue.NO_SOUND;
        }
        if (mode == Mode.PLAY_ON && entity == null) {
            return Issue.NO_ENTITY;
        }
        if (!toEnd && Numbers.toMillis(endSec) <= Numbers.toMillis(startSec)) {
            return Issue.BAD_RANGE;
        }
        if (forSend && targets.isBlank()) {
            return Issue.NO_TARGETS;
        }
        if (forSend && commandLength > PlayCommand.MAX_LENGTH) {
            return Issue.TOO_LONG;
        }
        return Issue.NONE;
    }

    /**
     * Parameters for the local preview, with the volume limited by the player's own cap.
     * The form must have a sound ({@link #check} returned no issue).
     */
    PlayParams toParams() {
        if (sound == null) {
            throw new IllegalStateException("No sound selected");
        }
        float cap = Math.min(SoundManagerConfig.get().maxVolume, SoundManagerConfig.MAX_VOLUME);
        PlayParams.Mode playMode = PlayParams.Mode.GLOBAL;
        double px = 0.0;
        double py = 0.0;
        double pz = 0.0;
        int entityId = 0;
        if (mode == Mode.PLAY_AT) {
            playMode = PlayParams.Mode.POSITION;
            px = x;
            py = y;
            pz = z;
        } else if (mode == Mode.PLAY_ON && entity != null) {
            playMode = PlayParams.Mode.ENTITY;
            px = entity.x();
            py = entity.y();
            pz = entity.z();
            entityId = entity.id();
        }
        return new PlayParams(sound, category,
                Math.min(volume, cap),
                Math.max(PlayParams.MIN_PITCH, Math.min(PlayParams.MAX_PITCH, pitch)),
                playMode, px, py, pz, entityId,
                Numbers.toMillis(startSec),
                toEnd ? NOT_SET : Numbers.toMillis(endSec),
                fadeMs(fadeInSec),
                fadeMs(fadeOutSec));
    }

    /** Snapshot for saving. The entity is not saved: it only exists in the current world. */
    PresetDto toPreset(String name) {
        PresetDto dto = new PresetDto();
        dto.name = name;
        dto.mode = mode.literal();
        dto.targets = targets;
        dto.sound = sound == null ? "" : sound.toString();
        dto.category = category.getName();
        dto.volume = volume;
        dto.pitch = pitch;
        dto.startSec = startSec;
        dto.endSec = toEnd ? -1.0 : endSec;
        dto.fadeInSec = fadeInSec;
        dto.fadeOutSec = fadeOutSec;
        dto.x = x;
        dto.y = y;
        dto.z = z;
        return dto;
    }

    /** Loads a preset, replacing every value except the entity. Values from the file are limited to valid ranges. */
    void apply(PresetDto dto) {
        mode = Mode.fromLiteral(dto.mode.toLowerCase(Locale.ROOT));
        if (mode != Mode.PLAY_ON) {
            entity = null;
        }
        targets = dto.targets;
        sound = Identifier.tryParse(dto.sound);
        category = parseCategory(dto.category);
        volume = (float) Numbers.clamp(dto.volume, 0.0, MAX_VOLUME);
        pitch = (float) Numbers.clamp(dto.pitch, PlayParams.MIN_PITCH, PlayParams.MAX_PITCH);
        startSec = Numbers.clamp(dto.startSec, 0.0, MAX_TIME_SEC);
        toEnd = dto.endSec < 0 || Double.isNaN(dto.endSec);
        endSec = toEnd ? Math.max(DEFAULT_END_SEC, startSec + 1.0) : Numbers.clamp(dto.endSec, 0.0, MAX_TIME_SEC);
        fadeInSec = Numbers.clamp(dto.fadeInSec, 0.0, MAX_FADE_SEC);
        fadeOutSec = Numbers.clamp(dto.fadeOutSec, 0.0, MAX_FADE_SEC);
        x = Numbers.clamp(dto.x, -MAX_COORDINATE, MAX_COORDINATE);
        y = Numbers.clamp(dto.y, -MAX_COORDINATE, MAX_COORDINATE);
        z = Numbers.clamp(dto.z, -MAX_COORDINATE, MAX_COORDINATE);
    }

    /** The next sound category in the order of the game. */
    void cycleCategory() {
        SoundCategory[] all = SoundCategory.values();
        category = all[(category.ordinal() + 1) % all.length];
    }

    private static SoundCategory parseCategory(String name) {
        for (SoundCategory candidate : SoundCategory.values()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        return SoundCategory.MASTER;
    }

    private static int fadeMs(double seconds) {
        return (int) Math.min(PlayParams.MAX_FADE_MS, Numbers.toMillis(seconds));
    }
}
