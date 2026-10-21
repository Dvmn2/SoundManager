package net.dvmn2.soundmanager.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload от плагина (канал {@code dvmn2:sm_ctl}): управление уже играющим звуком.
 * <pre>
 * VarInt  protocol
 * VarInt  action       (см. константы ниже)
 * String  sound        (namespace:path; пустая строка = все звуки)
 * Long    valueMs      (SEEK: позиция, мс)
 * Float   value        (VOLUME / PITCH: целевое значение)
 * VarInt  durationMs   (STOP: длительность затухания; VOLUME / PITCH: время плавного перехода)
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
                    buf.readString(256),
                    buf.readLong(),
                    buf.readFloat(),
                    buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
