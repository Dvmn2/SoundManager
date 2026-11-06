package net.dvmn2.soundmanager.client.gui;

import net.dvmn2.soundmanager.client.catalog.SoundSort;

/**
 * GUI values that outlive the screen, so that closing the window by accident does not lose the settings.
 * Client thread only.
 */
public final class GuiState {

    static final PlayForm FORM = new PlayForm();

    /** Order of the sound list. */
    static SoundSort soundSort = SoundSort.PATH_ASC;
    /** True to hide the sounds of the {@code minecraft} namespace. */
    static boolean excludeMinecraft;
    /** Name of the playlist selected on the Playlists tab; empty if none. */
    static String selectedPlaylist = "";
    /** Index of the selected track of that playlist; -1 if none. */
    static int selectedTrack = -1;
    /** True while the tracks list shows the presets to add one to the playlist. */
    static boolean pickingPreset;

    private GuiState() {
    }

    /** Drops what belongs to one server connection: the chosen entity does not exist in another world. */
    public static void clear() {
        selectedPlaylist = "";
        selectedTrack = -1;
        pickingPreset = false;
        FORM.entity = null;
        if (FORM.mode == PlayForm.Mode.PLAY_ON) {
            FORM.mode = PlayForm.Mode.PLAY;
        }
    }
}
