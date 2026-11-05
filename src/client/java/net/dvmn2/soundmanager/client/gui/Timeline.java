package net.dvmn2.soundmanager.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * Draws the timeline of the GUI: the sound range, fades, start and end markers and the preview position.
 * <p>
 * Display only. Labels are cached and rebuilt only when their value changes. Client thread only.
 */
final class Timeline {

    static final int HEIGHT = 14;
    /** Space the timeline needs below the bar for its time labels, pixels. */
    static final int LABEL_HEIGHT = 12;

    private static final long DEFAULT_TOTAL_MS = 60_000L;
    private static final int LABEL_GAP = 2;
    private static final long TENTH_MS = 100L;
    private static final long MS_PER_SECOND = 1000L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private static final int COLOR_BACKGROUND = 0xFF202020;
    private static final int COLOR_BORDER = 0xFF606060;
    private static final int COLOR_RANGE = 0xFF2F5F2F;
    private static final int COLOR_FADE_IN = 0xFF6FCF6F;
    private static final int COLOR_FADE_OUT = 0xFFCFA06F;
    private static final int COLOR_START = 0xFF55FF55;
    private static final int COLOR_END = 0xFFFF5555;
    private static final int COLOR_PLAYHEAD = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFAAAAAA;

    private long labelDurationMs = Long.MIN_VALUE;
    private String totalLabel = "";
    private long labelTenths = Long.MIN_VALUE;
    private String positionLabel = "";

    /**
     * Draws the bar at the given place with labels below it.
     *
     * @param durationMs length of the sound, or a value <= 0 if unknown
     * @param playheadMs preview position, or a negative value if there is no preview
     */
    void draw(DrawContext context, TextRenderer textRenderer, int x, int y, int width,
              PlayForm form, long durationMs, long playheadMs) {
        long startMs = Numbers.toMillis(form.startSec);
        long fadeInMs = Numbers.toMillis(form.fadeInSec);
        long fadeOutMs = Numbers.toMillis(form.fadeOutSec);
        long explicitEndMs = Numbers.toMillis(form.endSec);
        long total = totalMs(durationMs, startMs, form.toEnd ? 0 : explicitEndMs);
        long endMs = form.toEnd ? total : explicitEndMs;

        context.fill(x, y, x + width, y + HEIGHT, COLOR_BACKGROUND);
        int startX = position(startMs, total, x, width);
        int endX = position(endMs, total, x, width);
        if (endX > startX) {
            context.fill(startX, y + 1, endX, y + HEIGHT - 1, COLOR_RANGE);
            int fadeInX = Math.min(endX, position(startMs + fadeInMs, total, x, width));
            context.fill(startX, y + HEIGHT / 2, fadeInX, y + HEIGHT - 1, COLOR_FADE_IN);
            int fadeOutX = Math.max(startX, position(endMs - fadeOutMs, total, x, width));
            context.fill(fadeOutX, y + HEIGHT / 2, endX, y + HEIGHT - 1, COLOR_FADE_OUT);
        }
        context.fill(startX, y, startX + 2, y + HEIGHT, COLOR_START);
        context.fill(Math.max(x, endX - 1), y, endX + 1, y + HEIGHT, COLOR_END);
        if (playheadMs >= 0) {
            int playX = position(playheadMs, total, x, width);
            context.fill(playX, y, playX + 1, y + HEIGHT, COLOR_PLAYHEAD);
        }
        drawBorder(context, x, y, width);

        updateLabels(durationMs, playheadMs);
        int labelY = y + HEIGHT + LABEL_GAP;
        context.drawTextWithShadow(textRenderer, formatTime(0), x, labelY, COLOR_LABEL);
        context.drawTextWithShadow(textRenderer, totalLabel, x + width - textRenderer.getWidth(totalLabel),
                labelY, COLOR_LABEL);
        if (playheadMs >= 0) {
            context.drawTextWithShadow(textRenderer, positionLabel,
                    x + (width - textRenderer.getWidth(positionLabel)) / 2, labelY, COLOR_PLAYHEAD);
        }
    }

    /** One-pixel frame made of four filled strips, so that it does not depend on a version-specific method. */
    private static void drawBorder(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, COLOR_BORDER);
        context.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, COLOR_BORDER);
        context.fill(x, y, x + 1, y + HEIGHT, COLOR_BORDER);
        context.fill(x + width - 1, y, x + width, y + HEIGHT, COLOR_BORDER);
    }

    /** Text like {@code 1:05.3} for a time in milliseconds. */
    static String formatTime(long ms) {
        long safe = Math.max(0L, ms);
        long minutes = safe / (MS_PER_SECOND * SECONDS_PER_MINUTE);
        long seconds = safe / MS_PER_SECOND % SECONDS_PER_MINUTE;
        long tenths = safe % MS_PER_SECOND / TENTH_MS;
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths);
    }

    private void updateLabels(long durationMs, long playheadMs) {
        if (durationMs != labelDurationMs) {
            labelDurationMs = durationMs;
            totalLabel = durationMs > 0 ? formatTime(durationMs) : "?";
        }
        long tenths = playheadMs / TENTH_MS;
        if (tenths != labelTenths) {
            labelTenths = tenths;
            positionLabel = playheadMs >= 0 ? formatTime(playheadMs) : "";
        }
    }

    /** The scale of the bar: the sound length, or a guess that always fits the markers when it is unknown. */
    private static long totalMs(long durationMs, long startMs, long endMs) {
        if (durationMs > 0) {
            return durationMs;
        }
        return Math.max(DEFAULT_TOTAL_MS, Math.max(startMs, endMs) * 5 / 4);
    }

    private static int position(long ms, long total, int x, int width) {
        long clamped = Math.max(0L, Math.min(ms, total));
        return x + (int) (clamped * (width - 1) / total);
    }
}
