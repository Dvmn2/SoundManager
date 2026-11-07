package net.dvmn2.soundmanager.client.gui;

import net.minecraft.sound.SoundCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@code /soundmanager play|playat|playon} command text from a {@link PlayForm}.
 * <p>
 * The options of the command are positional ({@code start end fadein fadeout volume pitch category}), so the text
 * ends at the last option that differs from its default and every earlier option is written out.
 */
final class PlayCommand {

    /** Longest command a client may send in chat, characters. */
    static final int MAX_LENGTH = 256;

    private static final String ROOT = "soundmanager";
    private static final double EPSILON = 1e-4;
    private static final String TO_END = "-1";

    private PlayCommand() {
    }

    /** The command without the leading slash. A missing sound is written as a placeholder. */
    static String format(PlayForm form) {
        StringBuilder text = new StringBuilder(ROOT)
                .append(' ').append(form.mode.literal())
                .append(' ').append(TargetSelector.withDistance(form.targets, form.distanceBlocks))
                .append(' ').append(form.sound == null ? "<sound>" : form.sound.toString());
        switch (form.mode) {
            case PLAY_AT -> text.append(' ').append(Numbers.format(form.x))
                    .append(' ').append(Numbers.format(form.y))
                    .append(' ').append(Numbers.format(form.z));
            case PLAY_ON -> text.append(' ').append(form.entity == null ? "<entity>" : form.entity.uuid());
            case PLAY -> {
                // The global variant has no position argument.
            }
        }
        appendOptions(text, form);
        return text.toString();
    }

    /** Appends the positional options up to the last one that differs from its default. */
    static void appendOptions(StringBuilder text, PlayForm form) {
        List<String> options = new ArrayList<>();
        options.add(Numbers.format(form.startSec));
        options.add(form.toEnd ? TO_END : Numbers.format(form.endSec));
        options.add(Numbers.format(form.fadeInSec));
        options.add(Numbers.format(form.fadeOutSec));
        options.add(Numbers.format(form.volume));
        options.add(Numbers.format(form.pitch));
        options.add(form.category.getName());

        boolean[] isDefault = {
                form.startSec <= EPSILON,
                form.toEnd,
                form.fadeInSec <= EPSILON,
                form.fadeOutSec <= EPSILON,
                Math.abs(form.volume - 1.0f) <= EPSILON,
                Math.abs(form.pitch - 1.0f) <= EPSILON,
                form.category == SoundCategory.MASTER
        };
        int last = -1;
        for (int i = 0; i < isDefault.length; i++) {
            if (!isDefault[i]) {
                last = i;
            }
        }
        for (int i = 0; i <= last; i++) {
            text.append(' ').append(options.get(i));
        }
    }
}
