package net.dvmn2.soundmanager.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C {@code dvmn2:sm_play}: start a sound.
 * <pre>
 * VarInt  protocol
 * String  sound        (namespace:path as in sounds.json, max 256 bytes)
 * String  category     (master/music/record/weather/block/hostile/neutral/player/ambient/voice/ui, max 32 bytes)
 * Float   volume
 * Float   pitch
 * VarInt  mode         (0 = global, 1 = at a position, 2 = attached to an entity)
 * Double  x, y, z      (mode 1: position; mode 2: initial entity position; otherwise 0)
 * VarInt  entityId     (mode 2: network entity id; otherwise 0)
 * Long    startMs      (offset to start from, milliseconds)
 * Long    endMs        (offset to stop at, milliseconds; -1 = play to the end)
 * VarInt  fadeInMs
 * VarInt  fadeOutMs    (fade that ends exactly at endMs)
 * </pre>
 */
public record PlayPayload(int protocol, String sound, String category, float volume, float pitch,
                          int mode, double x, double y, double z, int entityId,
                          long startMs, long endMs, int fadeInMs, int fadeOutMs) implements CustomPayload {

    private static final int MAX_SOUND_BYTES = 256;
    private static final int MAX_CATEGORY_BYTES = 32;

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
                    buf.readString(MAX_SOUND_BYTES),
                    buf.readString(MAX_CATEGORY_BYTES),
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
                    buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
