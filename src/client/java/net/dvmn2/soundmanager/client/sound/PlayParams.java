package net.dvmn2.soundmanager.client.sound;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** Неизменяемое описание запуска звука. Времена — в миллисекундах. */
public record PlayParams(Identifier sound, SoundCategory category, float volume, float pitch,
                         Mode mode, double x, double y, double z, int entityId,
                         long startMs, long endMs, int fadeInMs, int fadeOutMs) {

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
