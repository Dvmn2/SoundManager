package net.dvmn2.soundmanager.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A list built from a fixed number of buttons that shows a window of the items and scrolls with the mouse wheel.
 * <p>
 * Made of ordinary buttons so that it needs no version-specific list widget. Used on the client thread only.
 *
 * @param <T> item type
 */
final class PagedButtonList<T> {

    private static final int SCROLL_ROWS = 2;
    private static final int LABEL_PADDING = 8;
    private static final int ROW_GAP = 2;

    private final TextRenderer textRenderer;
    private final int x;
    private final int y;
    private final int width;
    private final int rowHeight;
    private final int visibleRows;
    private final Function<T, Text> label;
    private final Consumer<T> onPick;
    private final List<ButtonWidget> buttons = new ArrayList<>();

    private List<T> items = List.of();
    private int offset;
    private String rangeText = "";

    PagedButtonList(TextRenderer textRenderer, int x, int y, int width, int rowHeight, int visibleRows,
                    Function<T, Text> label, Consumer<T> onPick) {
        this.textRenderer = textRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.rowHeight = rowHeight;
        this.visibleRows = visibleRows;
        this.label = label;
        this.onPick = onPick;
    }

    /** Creates the row buttons and hands each one to the adder (usually {@code Screen#addDrawableChild}). */
    void addTo(Consumer<ClickableWidget> adder) {
        for (int i = 0; i < visibleRows; i++) {
            int row = i;
            ButtonWidget button = ButtonWidget.builder(Text.empty(), pressed -> pick(row))
                    .dimensions(x, y + i * rowHeight, width, rowHeight - ROW_GAP)
                    .build();
            buttons.add(button);
            adder.accept(button);
        }
        refresh();
    }

    /** Replaces the items and scrolls to the top. */
    void setItems(List<T> newItems) {
        items = newItems;
        offset = 0;
        refresh();
    }

    /** Replaces the items but keeps the scroll position as far as it is still valid. */
    void replaceItems(List<T> newItems) {
        items = newItems;
        offset = Math.max(0, Math.min(offset, items.size() - visibleRows));
        refresh();
    }

    /** Redraws the labels, e.g. after the selection changed. */
    void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            ButtonWidget button = buttons.get(i);
            int index = offset + i;
            boolean present = index < items.size();
            button.visible = present;
            button.active = present;
            if (present) {
                String text = textRenderer.trimToWidth(label.apply(items.get(index)).getString(),
                        width - LABEL_PADDING);
                button.setMessage(Text.literal(text));
            }
        }
        int last = Math.min(offset + visibleRows, items.size());
        rangeText = items.isEmpty() ? "" : (offset + 1) + "-" + last + " / " + items.size();
    }

    /** Scrolls by mouse wheel; a positive amount scrolls up. */
    void scroll(double amount) {
        int maxOffset = Math.max(0, items.size() - visibleRows);
        int next = offset + (amount > 0 ? -SCROLL_ROWS : SCROLL_ROWS);
        offset = Math.max(0, Math.min(maxOffset, next));
        refresh();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + visibleRows * rowHeight;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    /** Text like {@code 1-8 / 120}; empty when there are no items. */
    String rangeText() {
        return rangeText;
    }

    private void pick(int row) {
        int index = offset + row;
        if (index < items.size()) {
            onPick.accept(items.get(index));
        }
    }
}
