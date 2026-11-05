package net.dvmn2.soundmanager.client.gui;

import java.math.BigDecimal;

/** Number helpers shared by the GUI widgets and the command formatter. */
final class Numbers {

    private static final double MILLIS_PER_SECOND = 1000.0;

    private Numbers() {
    }

    /** Text of a number rounded to three decimals without trailing zeros and without an exponent. */
    static String format(double value) {
        double rounded = Math.round(value * MILLIS_PER_SECOND) / MILLIS_PER_SECOND;
        return BigDecimal.valueOf(rounded).stripTrailingZeros().toPlainString();
    }

    /** Parses a number that may use a comma as the decimal separator; returns NaN if the text is not a number. */
    static double parse(String text) {
        try {
            return Double.parseDouble(text.strip().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Double.NaN; // half-typed input such as "-" or "."
        }
    }

    /** Limits the value to the range; NaN becomes the minimum. */
    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Seconds to whole milliseconds. */
    static long toMillis(double seconds) {
        return Math.round(seconds * MILLIS_PER_SECOND);
    }
}
