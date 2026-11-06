package net.dvmn2.soundmanager.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C {@code dvmn2:sm_plist}: the playlists of the server, for the window of the mod.
 * <pre>
 * VarInt  protocol
 * VarInt  action        (RESET = 0, SET = 1, REMOVE = 2, STATE = 3)
 * String  name          (playlist name, max 160 bytes; empty for RESET)
 * </pre>
 * SET adds or replaces the playlist with this name:
 * <pre>
 * Boolean loop
 * Boolean shuffle
 * VarInt  state         (0 = stopped, 1 = playing, 2 = paused)
 * VarInt  current       (index of the playing track in the stored order; -1 if none)
 * VarInt  trackCount    (at most {@link #MAX_TRACKS})
 * per track:
 *   VarInt  mode        (0 = global, 1 = at a position)
 *   String  sound       (max 256 bytes)
 *   String  category    (max 32 bytes)
 *   Float   volume
 *   Float   pitch
 *   Double  x, y, z
 *   Long    startMs
 *   Long    endMs       (-1 = to the end)
 *   Long    lengthMs    (length of the sound file; -1 = unknown)
 *   VarInt  fadeInMs
 *   VarInt  fadeOutMs
 * </pre>
 * STATE changes the run state of a playlist without its tracks:
 * <pre>
 * VarInt  state
 * VarInt  current
 * </pre>
 * RESET and REMOVE carry no further fields. RESET makes the mod forget every playlist; the server sends it before
 * the SET packets that follow a join, so the new state replaces the old one entirely.
 * <p>
 * A packet with another protocol version or an impossible size is decoded as an inert packet (action
 * {@link #IGNORED}) and the rest of its bytes are skipped, so that a mismatch never disconnects the player.
 *
 * @param playlist the playlist for SET (with its tracks) and STATE (without tracks); null for other actions
 */
public record PlaylistPayload(int protocol, int action, String name,
                              @Nullable PlaylistInfo playlist) implements CustomPayload {

    /** Protocol version of the playlist packets; bumped on any format change. */
    public static final int PROTOCOL = 1;
    public static final int RESET = 0;
    public static final int SET = 1;
    public static final int REMOVE = 2;
    public static final int STATE = 3;
    /** Action of a packet that is decoded but must be ignored. */
    public static final int IGNORED = -1;
    /** Most tracks in one playlist; the server enforces the same limit. */
    public static final int MAX_TRACKS = 90;

    private static final int MAX_NAME_BYTES = 160;
    private static final int MAX_SOUND_BYTES = 256;
    private static final int MAX_CATEGORY_BYTES = 32;

    public static final CustomPayload.Id<PlaylistPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "sm_plist"));

    public static final PacketCodec<PacketByteBuf, PlaylistPayload> CODEC = PacketCodec.of(
            PlaylistPayload::write, PlaylistPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(PlaylistPayload p, PacketByteBuf buf) {
        buf.writeVarInt(p.protocol());
        buf.writeVarInt(p.action());
        buf.writeString(p.name());
        PlaylistInfo info = p.playlist();
        if (info == null) {
            return;
        }
        if (p.action() == SET) {
            buf.writeBoolean(info.loop());
            buf.writeBoolean(info.shuffle());
            buf.writeVarInt(info.state().getId());
            buf.writeVarInt(info.current());
            buf.writeVarInt(info.tracks().size());
            for (PlaylistInfo.TrackInfo track : info.tracks()) {
                writeTrack(buf, track);
            }
        } else if (p.action() == STATE) {
            buf.writeVarInt(info.state().getId());
            buf.writeVarInt(info.current());
        }
    }

    private static PlaylistPayload read(PacketByteBuf buf) {
        int protocol = buf.readVarInt();
        if (protocol != PROTOCOL) {
            return ignored(buf, protocol);
        }
        int action = buf.readVarInt();
        String name = buf.readString(MAX_NAME_BYTES);
        return switch (action) {
            case SET -> readPlaylist(buf, protocol, name);
            case STATE -> {
                PlaylistInfo.RunState state = PlaylistInfo.RunState.fromId(buf.readVarInt());
                int current = buf.readVarInt();
                yield new PlaylistPayload(protocol, STATE, name,
                        new PlaylistInfo(name, false, false, state, current, List.of()));
            }
            case RESET, REMOVE -> new PlaylistPayload(protocol, action, name, null);
            default -> ignored(buf, protocol);
        };
    }

    private static PlaylistPayload readPlaylist(PacketByteBuf buf, int protocol, String name) {
        boolean loop = buf.readBoolean();
        boolean shuffle = buf.readBoolean();
        PlaylistInfo.RunState state = PlaylistInfo.RunState.fromId(buf.readVarInt());
        int current = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TRACKS) {
            return ignored(buf, protocol);
        }
        List<PlaylistInfo.TrackInfo> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tracks.add(readTrack(buf));
        }
        return new PlaylistPayload(protocol, SET, name,
                new PlaylistInfo(name, loop, shuffle, state, current, List.copyOf(tracks)));
    }

    private static PlaylistPayload ignored(PacketByteBuf buf, int protocol) {
        buf.skipBytes(buf.readableBytes());
        return new PlaylistPayload(protocol, IGNORED, "", null);
    }

    private static void writeTrack(PacketByteBuf buf, PlaylistInfo.TrackInfo track) {
        buf.writeVarInt(track.positional() ? 1 : 0);
        buf.writeString(track.sound());
        buf.writeString(track.category());
        buf.writeFloat(track.volume());
        buf.writeFloat(track.pitch());
        buf.writeDouble(track.x());
        buf.writeDouble(track.y());
        buf.writeDouble(track.z());
        buf.writeLong(track.startMs());
        buf.writeLong(track.endMs());
        buf.writeLong(track.lengthMs());
        buf.writeVarInt(track.fadeInMs());
        buf.writeVarInt(track.fadeOutMs());
    }

    private static PlaylistInfo.TrackInfo readTrack(PacketByteBuf buf) {
        return new PlaylistInfo.TrackInfo(
                buf.readVarInt() == 1,
                buf.readString(MAX_SOUND_BYTES),
                buf.readString(MAX_CATEGORY_BYTES),
                buf.readFloat(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt());
    }
}
