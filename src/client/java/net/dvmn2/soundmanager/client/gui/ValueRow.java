package net.dvmn2.soundmanager.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

/**
 * A number edited by a slider and a text field that stay in sync.
 * <p>
 * The slider covers {@code min..sliderMax} for quick adjustment; the field accepts any value up to {@code hardMax}
 * for precision. Used on the client thread only.
 */
final class ValueRow {

    static final int HEIGHT = 20;

    private static final int FIELD_WIDTH = 46;
    private static final int FIELD_GAP = 2;
    private static final int MAX_FIELD_LENGTH = 12;
    private static final double SLIDER_STEP = 100.0;
    private static final Pattern NUMBER_TEXT = Pattern.compile("-?[0-9]*[.,]?[0-9]*");

    private final Text label;
    private final double min;
    private final double hardMax;
    private final DoubleConsumer onChange;
    private final RowSlider slider;
    private final TextFieldWidget field;

    private double sliderMax;
    private double current;
    private boolean syncing;

    ValueRow(TextRenderer textRenderer, int x, int y, int width, Text label,
             double min, double sliderMax, double hardMax, double initial, DoubleConsumer onChange) {
        this.label = label;
        this.min = min;
        this.sliderMax = sliderMax;
        this.hardMax = hardMax;
        this.onChange = onChange;
        this.current = Numbers.clamp(initial, min, hardMax);

        int sliderWidth = width - FIELD_WIDTH - FIELD_GAP;
        this.slider = new RowSlider(x, y, sliderWidth, normalized(current));
        this.field = new TextFieldWidget(textRenderer, x + sliderWidth + FIELD_GAP, y, FIELD_WIDTH, HEIGHT, label);
        field.setMaxLength(MAX_FIELD_LENGTH);
        field.setTextPredicate(text -> NUMBER_TEXT.matcher(text).matches());
        field.setText(Numbers.format(current));
        field.setChangedListener(this::onFieldChanged);
    }

    /** Hands both widgets to the adder (usually {@code Screen#addDrawableChild}). */
    void addTo(Consumer<ClickableWidget> adder) {
        adder.accept(slider);
        adder.accept(field);
    }

    /** Sets the value without notifying the change listener. */
    void setValue(double value) {
        current = Numbers.clamp(value, min, hardMax);
        showCurrent();
    }

    /** Changes the range of the slider, e.g. once the length of the sound is known. */
    void setSliderMax(double newMax) {
        sliderMax = Math.max(newMax, min + 1.0 / SLIDER_STEP);
        slider.show(normalized(current));
    }

    void setActive(boolean active) {
        slider.active = active;
        field.setEditable(active);
        field.active = active;
    }

    private void onSlider(double normalizedValue) {
        double raw = min + normalizedValue * (sliderMax - min);
        current = Numbers.clamp(Math.round(raw * SLIDER_STEP) / SLIDER_STEP, min, hardMax);
        syncing = true;
        field.setText(Numbers.format(current));
        syncing = false;
        onChange.accept(current);
    }

    private void onFieldChanged(String text) {
        if (syncing) {
            return;
        }
        double parsed = Numbers.parse(text);
        if (Double.isNaN(parsed)) {
            return;
        }
        current = Numbers.clamp(parsed, min, hardMax);
        slider.show(normalized(current));
        onChange.accept(current);
    }

    private void showCurrent() {
        syncing = true;
        field.setText(Numbers.format(current));
        syncing = false;
        slider.show(normalized(current));
    }

    private double normalized(double value) {
        if (sliderMax <= min) {
            return 0.0;
        }
        return Numbers.clamp((value - min) / (sliderMax - min), 0.0, 1.0);
    }

    private final class RowSlider extends SliderWidget {

        RowSlider(int x, int y, int width, double normalizedValue) {
            super(x, y, width, HEIGHT, Text.empty(), normalizedValue);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.empty().append(label).append(Text.literal(": " + Numbers.format(current))));
        }

        @Override
        protected void applyValue() {
            onSlider(this.value);
            updateMessage();
        }

        void show(double normalizedValue) {
            this.value = normalizedValue;
            updateMessage();
        }
    }
}
