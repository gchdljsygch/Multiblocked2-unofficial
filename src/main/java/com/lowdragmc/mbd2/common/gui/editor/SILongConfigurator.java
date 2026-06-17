package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ValueConfigurator;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.mbd2.utils.EnergyFormatUtil;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Long-value configurator that accepts compact SI-style energy text.
 *
 * <p>The widget parses user input through {@link EnergyFormatUtil}, clamps values to a configured range, and formats
 * committed values back to readable text. It is an editor UI component and should be accessed from the client UI
 * thread. The configured updater is called through the normal {@link ValueConfigurator} value-update path.</p>
 */
public class SILongConfigurator extends ValueConfigurator<Long> {
    /**
     * Text box used for user input after {@link #init(int)}.
     */
    protected TextFieldWidget textFieldWidget;
    private boolean updatingText;
    private final long min;
    private final long max;
    private final String tooltipKey;

    /**
     * Creates a configurator with the default range {@code 0..Long.MAX_VALUE}.
     */
    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, @Nonnull Long defaultValue, boolean forceUpdate) {
        this(name, supplier, onUpdate, 0L, Long.MAX_VALUE, defaultValue, forceUpdate, null);
    }

    /**
     * Creates a configurator with an explicit inclusive range.
     */
    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, long min, long max, @Nonnull Long defaultValue, boolean forceUpdate) {
        this(name, supplier, onUpdate, min, max, defaultValue, forceUpdate, null);
    }

    /**
     * Creates a configurator with an explicit inclusive range and optional leading tooltip.
     *
     * @param name         editor field name
     * @param supplier     value supplier used by the base configurator
     * @param onUpdate     callback invoked when the value changes
     * @param min          inclusive minimum accepted value
     * @param max          inclusive maximum accepted value
     * @param defaultValue fallback value used when the supplied value is {@code null}
     * @param forceUpdate  whether the base configurator should force update propagation
     * @param tooltipKey   optional translation key displayed before the range tooltip
     */
    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, long min, long max, @Nonnull Long defaultValue, boolean forceUpdate, String tooltipKey) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        this.min = min;
        this.max = max;
        this.tooltipKey = tooltipKey;
    }

    /**
     * Receives external value changes and refreshes the text box unless the user is editing it.
     *
     * @param newValue new value from the configurator source; {@code null} maps to the default value
     */
    @Override
    protected void onValueUpdate(Long newValue) {
        if (newValue == null) newValue = defaultValue;
        if (newValue.equals(value)) return;
        super.onValueUpdate(newValue);
        if (textFieldWidget == null || !textFieldWidget.isFocus()) {
            updateTextFromValue();
        }
    }

    /**
     * Creates the text-field widget and initializes it from the current value.
     *
     * @param width available configurator width in pixels
     */
    @Override
    public void init(int width) {
        super.init(width);
        addWidget(new ImageWidget(leftWidth, 2, width - leftWidth - 3 - rightWidth, 10, ColorPattern.T_GRAY.rectTexture().setRadius(5)));

        textFieldWidget = new TextFieldWidget(leftWidth + 3, 2, width - leftWidth - 6 - rightWidth, 10, null, this::onStringUpdate) {
            @Override
            public void onFocusChanged(com.lowdragmc.lowdraglib.gui.widget.Widget lastFocus, com.lowdragmc.lowdraglib.gui.widget.Widget focus) {
                super.onFocusChanged(lastFocus, focus);
                if (!isFocus()) {
                    commitAndFormat();
                }
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
                if (isFocus() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                    commitAndFormat();
                    return true;
                }
                return handled;
            }
        };
        textFieldWidget.setClientSideWidget();
        textFieldWidget.setBordered(false);
        textFieldWidget.setValidator(s -> isValidEnergyText(s) ? s : textFieldWidget.getCurrentString());
        applyTooltips();
        addWidget(textFieldWidget);

        updateTextFromValue();
    }

    private void onStringUpdate(String s) {
        if (updatingText) return;
        if (s == null || s.isBlank()) return;
        try {
            long parsed = clamp(EnergyFormatUtil.parseEnergy(s));
            super.onValueUpdate(parsed);
            updateValue();
        } catch (NumberFormatException ignored) {
        }
    }

    private void commitAndFormat() {
        if (textFieldWidget == null) return;
        if (updatingText) return;
        String raw = textFieldWidget.getRawCurrentString();
        if (raw == null || raw.isBlank()) {
            updateTextFromValue();
            return;
        }
        try {
            long parsed = clamp(EnergyFormatUtil.parseEnergy(raw));
            super.onValueUpdate(parsed);
            updateValue();
        } catch (NumberFormatException ignored) {
        }
        updateTextFromValue();
    }

    private void updateTextFromValue() {
        if (textFieldWidget == null) return;
        updatingText = true;
        textFieldWidget.setCurrentString(EnergyFormatUtil.formatEnergy(value == null ? defaultValue : value));
        updatingText = false;
    }

    private void applyTooltips() {
        if (textFieldWidget == null) return;
        String minText = Long.toString(min);
        String maxText = Long.toString(max);
        if (tooltipKey == null || tooltipKey.isBlank()) {
            textFieldWidget.setHoverTooltips(
                    Component.translatable("mbd2.energy.si_input.tooltip", minText, maxText)
            );
        } else {
            textFieldWidget.setHoverTooltips(
                    Component.translatable(tooltipKey),
                    Component.translatable("mbd2.energy.si_input.tooltip", minText, maxText)
            );
        }
    }

    private long clamp(long v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static boolean isValidEnergyText(String raw) {
        if (raw == null) return true;
        String s = raw.trim();
        if (s.isEmpty()) return true;
        boolean seenDot = false;
        boolean seenSuffix = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == ',') continue;
            if (c >= '0' && c <= '9') {
                if (seenSuffix) return false;
                continue;
            }
            if (c == '.') {
                if (seenSuffix || seenDot) return false;
                seenDot = true;
                continue;
            }
            if (i != s.length() - 1) return false;
            char u = Character.toUpperCase(c);
            if (u == 'K' || u == 'M' || u == 'G' || u == 'T' || u == 'P' || u == 'E') {
                seenSuffix = true;
                continue;
            }
            return false;
        }
        return true;
    }
}
