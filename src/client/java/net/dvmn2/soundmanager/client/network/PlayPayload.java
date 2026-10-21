package net.dvmn2.soundmanager.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload от плагина (канал {@code dvmn2:sm_play}): запустить звук.
 * <pre>
 * VarInt  protocol
 * String  sound        (namespace:path, как в sounds.json)
 * String  category     (master/music/record/weather/block/hostile/neutral/player/ambient/voice/ui)
 * Float   volume
 * Float   pitch
 * VarInt  mode         (0 = глобальный, 1 = в точке, 2 = привязан к сущности)
 * Double  x, y, z      (mode 1: позиция; mode 2: стартовая позиция сущности; иначе 0)
 * VarInt  entityId     (mode 2: сетевой id сущности; иначе 0)
 * Long    startMs      (с какого места начать, мс)
 * Long    endMs        (на каком месте закончить, мс; -1 = играть до конца)
 * VarInt  fadeInMs
 * VarInt  fadeOutMs    (затухание ПЕРЕД endMs)
 * </pre>
 */
public record PlayPayload(int protocol, String sound, String category, float volume, float pitch,
                          int mode, double x, double y, double z, int entityId,
                          long startMs, long endMs, int fadeInMs, int fadeOutMs) implements CustomPayload {

    public static final CustomPayload.Id<PlayPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "sm_play"));

    public static final PacketCodec<PacketByteBuf, PlayPayload> CODEC = PacketCodec.of(
            (p, buf) -> {
                buf.writeVarInt(p.protocol());
                buf.writeString(p.sound());
                buf.writeString(p.category());
                buf.writeFloat(p.volume());
                buf.writeFloat(p.pitch());
                buf.writeVarInt(p.mode());
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeVarInt(p.entityId());
                buf.writeLong(p.startMs());
                buf.writeLong(p.endMs());
                buf.writeVarInt(p.fadeInMs());
                buf.writeVarInt(p.fadeOutMs());
            },
            buf -> new PlayPayload(
                    buf.readVarInt(),
                    buf.readString(256),
                    buf.readString(32),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
