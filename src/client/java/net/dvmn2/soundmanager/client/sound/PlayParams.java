package net.dvmn2.soundmanager.client.sound;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** Immutable description of a sound start. All times are in milliseconds. */
public record PlayParams(
        Identifier sound, SoundCategory category, float volume, float pitch,
        Mode mode, double x, double y, double z, int entityId,
        long startMs, long endMs, int fadeInMs, int fadeOutMs) {

    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2.0f;
    /** Upper bound for fade and transition lengths, milliseconds. */
    public static final int MAX_FADE_MS = 600_000;

    public enum Mode {
        GLOBAL, POSITION, ENTITY;

        public static Mode fromId(int id) {
            return switch (id) {
                case 1 -> POSITION;
                case 2 -> ENTITY;
                default -> GLOBAL;
            };
        }
    }

    public PlayParams withStart(long newStartMs) {
        return new PlayParams(sound, category, volume, pitch, mode, x, y, z, entityId,
                newStartMs, endMs, fadeInMs, fadeOutMs);
    }

    public PlayParams withVolume(float newVolume) {
        return new PlayParams(sound, category, newVolume, pitch, mode, x, y, z, entityId,
                startMs, endMs, fadeInMs, fadeOutMs);
    }

    public PlayParams withPitch(float newPitch) {
        return new PlayParams(sound, category, volume, newPitch, mode, x, y, z, entityId,
                startMs, endMs, fadeInMs, fadeOutMs);
    }
}
