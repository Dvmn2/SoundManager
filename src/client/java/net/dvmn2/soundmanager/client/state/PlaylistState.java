package net.dvmn2.soundmanager.client.state;

import net.dvmn2.soundmanager.client.network.PlaylistInfo;
import net.dvmn2.soundmanager.client.network.PlaylistPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The playlists of the server, as last reported by the plugin. Shown by the GUI only (visual state): the server
 * decides what plays and who may edit.
 * <p>
 * Changed on the client thread only. The list is an immutable snapshot replaced as a whole, so the GUI may read it
 * at any time; {@link #getVersion()} changes with every replacement and lets the GUI skip redrawing.
 */
public final class PlaylistState {

    private static volatile List<PlaylistInfo> playlists = List.of();
    private static volatile int version;

    private PlaylistState() {
    }

    /** Applies a packet of the server. Packets with another protocol or an unknown action are ignored. */
    public static void onPayload(PlaylistPayload payload) {
        if (payload.protocol() != PlaylistPayload.PROTOCOL) {
            return;
        }
        PlaylistInfo info = payload.playlist();
        switch (payload.action()) {
            case PlaylistPayload.RESET -> replace(List.of());
            case PlaylistPayload.SET -> {
                if (info != null) {
                    set(info);
                }
            }
            case PlaylistPayload.REMOVE -> remove(payload.name());
            case PlaylistPayload.STATE -> {
                if (info != null) {
                    setState(payload.name(), info);
                }
            }
            default -> {
                // IGNORED or from a newer plugin: nothing to apply.
            }
        }
    }

    /** All playlists in the order the server sent them. */
    public static List<PlaylistInfo> getAll() {
        return playlists;
    }

    /** The playlist with this name (case-insensitive, as on the server), or null. */
    @Nullable
    public static PlaylistInfo find(String name) {
        for (PlaylistInfo info : playlists) {
            if (info.name().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }

    /** Changes with every update; compare with the value seen last to know that the lists need a redraw. */
    public static int getVersion() {
        return version;
    }

    /** Forgets everything (disconnect). */
    public static void clear() {
        replace(List.of());
    }

    private static void set(PlaylistInfo info) {
        List<PlaylistInfo> next = new ArrayList<>(playlists);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).name().equalsIgnoreCase(info.name())) {
                next.set(i, info);
                replace(next);
                return;
            }
        }
        next.add(info);
        replace(next);
    }

    private static void remove(String name) {
        List<PlaylistInfo> next = new ArrayList<>(playlists);
        next.removeIf(info -> info.name().equalsIgnoreCase(name));
        replace(next);
    }

    private static void setState(String name, PlaylistInfo update) {
        List<PlaylistInfo> next = new ArrayList<>(playlists);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).name().equalsIgnoreCase(name)) {
                next.set(i, next.get(i).withState(update.state(), update.current()));
                replace(next);
                return;
            }
        }
    }

    private static void replace(List<PlaylistInfo> next) {
        playlists = List.copyOf(next);
        version++;
    }
}
