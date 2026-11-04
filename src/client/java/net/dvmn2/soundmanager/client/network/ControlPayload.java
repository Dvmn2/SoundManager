package net.dvmn2.soundmanager.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C {@code dvmn2:sm_ctl}: control sounds that are already playing.
 * <pre>
 * VarInt  protocol
 * VarInt  action       (STOP, PAUSE, RESUME, SEEK, VOLUME, PITCH; see constants)
 * String  sound        (namespace:path, max 256 bytes; empty string = all managed sounds)
 * Long    valueMs      (SEEK: position, milliseconds)
 * Float   value        (VOLUME / PITCH: target value)
 * VarInt  durationMs   (STOP: fade-out length; VOLUME / PITCH: transition length; milliseconds)
 * </pre>
 */
public record ControlPayload(int protocol, int action, String sound, long valueMs, float value,
                             int durationMs) implements CustomPayload {

    public static final int STOP = 0;
    public static final int PAUSE = 1;
    public static final int RESUME = 2;
    public static final int SEEK = 3;
    public static final int VOLUME = 4;
    public static final int PITCH = 5;

    private static final int MAX_SOUND_BYTES = 256;

    public static final CustomPayload.Id<ControlPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "sm_ctl"));

    public static final PacketCodec<PacketByteBuf, ControlPayload> CODEC = PacketCodec.of(
            (p, buf) -> {
                buf.writeVarInt(p.protocol());
                buf.writeVarInt(p.action());
                buf.writeString(p.sound());
                buf.writeLong(p.valueMs());
                buf.writeFloat(p.value());
                buf.writeVarInt(p.durationMs());
            },
            buf -> new ControlPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readString(MAX_SOUND_BYTES),
                    buf.readLong(),
                    buf.readFloat(),
                    buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
