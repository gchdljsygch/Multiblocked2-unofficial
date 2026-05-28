package com.non_coffee.mbd2thread.gui.editor.configurator;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ValueConfigurator;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.non_coffee.mbd2thread.energy.util.EnergyFormatUtil;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SILongConfigurator extends ValueConfigurator<Long> {
    protected TextFieldWidget textFieldWidget;
    private boolean updatingText;
    private final long min;
    private final long max;
    private final String tooltipKey;

    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, @Nonnull Long defaultValue, boolean forceUpdate) {
        this(name, supplier, onUpdate, 0L, Long.MAX_VALUE, defaultValue, forceUpdate, null);
    }

    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, long min, long max, @Nonnull Long defaultValue, boolean forceUpdate) {
        this(name, supplier, onUpdate, min, max, defaultValue, forceUpdate, null);
    }

    public SILongConfigurator(String name, Supplier<Long> supplier, Consumer<Long> onUpdate, long min, long max, @Nonnull Long defaultValue, boolean forceUpdate, String tooltipKey) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        this.min = min;
        this.max = max;
        this.tooltipKey = tooltipKey;
    }

    @Override
    protected void onValueUpdate(Long newValue) {
        if (newValue == null) newValue = defaultValue;
        if (newValue.equals(value)) return;
        super.onValueUpdate(newValue);
        if (textFieldWidget == null || !textFieldWidget.isFocus()) {
            updateTextFromValue();
        }
    }

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
                    Component.translatable("mbd2thread.energy.si_input.tooltip", minText, maxText)
            );
        } else {
            textFieldWidget.setHoverTooltips(
                    Component.translatable(tooltipKey),
                    Component.translatable("mbd2thread.energy.si_input.tooltip", minText, maxText)
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
