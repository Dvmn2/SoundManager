package net.dvmn2.soundmanager.client.gui;

/**
 * Adds a {@code distance=..x} argument to an entity selector typed in the Target tab.
 * <p>
 * The text of the targets field is never changed: the argument is added only when the command is built. Plain
 * player names and {@code @s} are left alone, because a distance means nothing for them. Pure functions, any thread.
 */
final class TargetSelector {

    private static final String DISTANCE_KEY = "distance=";
    private static final String DISTANCE_PREFIX = DISTANCE_KEY + "..";
    private static final char SELECTOR_START = '@';
    /** Selector types that can match someone other than the sender: all, entities, nearest, random, nearest entity. */
    private static final String DISTANCE_TYPES = "aeprn";
    private static final int TYPE_INDEX = 1;
    private static final int TYPE_ONLY_LENGTH = 2;
    private static final double EPSILON = 1e-4;

    private TargetSelector() {
    }

    /**
     * True if a distance can be added to the targets: a selector of a suitable type that does not set
     * {@code distance} itself.
     */
    static boolean supportsDistance(String targets) {
        String text = targets.strip();
        if (text.length() < TYPE_ONLY_LENGTH || text.charAt(0) != SELECTOR_START
                || DISTANCE_TYPES.indexOf(text.charAt(TYPE_INDEX)) < 0) {
            return false;
        }
        String rest = text.substring(TYPE_ONLY_LENGTH);
        if (rest.isEmpty()) {
            return true;
        }
        return rest.charAt(0) == '[' && rest.endsWith("]") && !rest.contains(DISTANCE_KEY);
    }

    /** The targets with {@code distance=..blocks}; unchanged if the distance is 0 or the targets do not support it. */
    static String withDistance(String targets, double blocks) {
        String text = targets.strip();
        if (blocks <= EPSILON || !supportsDistance(text)) {
            return text;
        }
        String argument = DISTANCE_PREFIX + Numbers.format(blocks);
        String head = text.substring(0, TYPE_ONLY_LENGTH);
        String rest = text.substring(TYPE_ONLY_LENGTH);
        if (rest.isEmpty()) {
            return head + '[' + argument + ']';
        }
        String inner = rest.substring(1, rest.length() - 1).strip();
        return head + '[' + (inner.isEmpty() ? "" : inner + ',') + argument + ']';
    }
}
