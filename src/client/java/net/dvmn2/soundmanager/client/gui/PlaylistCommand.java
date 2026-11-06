package net.dvmn2.soundmanager.client.gui;

import net.dvmn2.soundmanager.client.preset.PresetDto;

/**
 * Builds the {@code /soundmanager playlist ...} commands the Playlists tab sends. Stateless; the commands have no
 * leading slash. Playlist names are always quoted, so names with spaces work.
 */
final class PlaylistCommand {

    private static final String PREFIX = "soundmanager playlist ";
    private static final String QUOTE = "\"";
    private static final String UNKNOWN_LENGTH = "-1";

    private PlaylistCommand() {
    }

    static String create(String name) {
        return PREFIX + "create " + quote(name);
    }

    static String delete(String name) {
        return PREFIX + "delete " + quote(name);
    }

    /** {@code verb} is one of {@code stop}, {@code next}, {@code pause}, {@code resume}. */
    static String control(String verb, String name) {
        return PREFIX + verb + ' ' + quote(name);
    }

    static String start(String name, String targets) {
        return PREFIX + "start " + quote(name) + ' ' + targets.strip();
    }

    /** {@code option} is {@code loop} or {@code shuffle}. */
    static String set(String name, String option, boolean value) {
        return PREFIX + "set " + quote(name) + ' ' + option + ' ' + value;
    }

    /** Removes a track by its one-based number. */
    static String remove(String name, int number) {
        return PREFIX + "remove " + quote(name) + ' ' + number;
    }

    /** Moves a track between one-based numbers. */
    static String move(String name, int from, int to) {
        return PREFIX + "move " + quote(name) + ' ' + from + ' ' + to;
    }

    /**
     * Adds a preset as a track. The preset must not use {@code playon}.
     *
     * @param lengthSec length of the whole sound file in seconds, or a value below zero if it is not needed
     *                  (the preset has an end) or not known
     */
    static String add(String name, PresetDto preset, double lengthSec) {
        PlayForm form = new PlayForm();
        form.apply(preset);
        boolean positional = form.mode == PlayForm.Mode.PLAY_AT;
        StringBuilder text = new StringBuilder(PREFIX).append("add ").append(quote(name))
                .append(' ').append(positional ? PlayForm.Mode.PLAY_AT.literal() : PlayForm.Mode.PLAY.literal())
                .append(' ').append(form.sound == null ? "" : form.sound.toString());
        if (positional) {
            text.append(' ').append(Numbers.format(form.x))
                    .append(' ').append(Numbers.format(form.y))
                    .append(' ').append(Numbers.format(form.z));
        }
        text.append(' ').append(lengthSec > 0 ? Numbers.format(lengthSec) : UNKNOWN_LENGTH);
        PlayCommand.appendOptions(text, form);
        return text.toString();
    }

    private static String quote(String name) {
        return QUOTE + name.replace("\\", "\\\\").replace(QUOTE, "\\" + QUOTE) + QUOTE;
    }
}
