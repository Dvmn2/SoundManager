package net.dvmn2.soundmanager.client.gui;

/**
 * GUI values that outlive the screen, so that closing the window by accident does not lose the settings.
 * Client thread only.
 */
public final class GuiState {

    static final PlayForm FORM = new PlayForm();

    private GuiState() {
    }

    /** Drops what belongs to one server connection: the chosen entity does not exist in another world. */
    public static void clear() {
        FORM.entity = null;
        if (FORM.mode == PlayForm.Mode.PLAY_ON) {
            FORM.mode = PlayForm.Mode.PLAY;
        }
    }
}
