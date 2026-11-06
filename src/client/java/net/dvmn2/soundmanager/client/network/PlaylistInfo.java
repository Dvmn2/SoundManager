package net.dvmn2.soundmanager.client.network;

import java.util.List;

/**
 * A playlist of the server as sent by {@code dvmn2:sm_plist}. Immutable; shown by the GUI only, the server decides
 * everything.
 *
 * @param current index of the playing track in the stored order, -1 if none
 */
public record PlaylistInfo(String name, boolean loop, boolean shuffle, RunState state, int current,
                           List<TrackInfo> tracks) {

    /** State of a playlist run. The {@code id} is the value in the packet. */
    public enum RunState {
        STOPPED(0), PLAYING(1), PAUSED(2);

        private final int id;

        RunState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static RunState fromId(int id) {
            for (RunState value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            return STOPPED;
        }
    }

    /** One track; times in milliseconds, {@code endMs = -1} means "to the end", {@code lengthMs = -1} unknown. */
    public record TrackInfo(boolean positional, String sound, String category, float volume, float pitch,
                            double x, double y, double z, long startMs, long endMs, long lengthMs,
                            int fadeInMs, int fadeOutMs) {
    }

    public PlaylistInfo withState(RunState newState, int newCurrent) {
        return new PlaylistInfo(name, loop, shuffle, newState, newCurrent, tracks);
    }
}
