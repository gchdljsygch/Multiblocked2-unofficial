package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;

/**
 * Toggle wrapper for a machine state's emitted light level.
 * <p>
 * Enabled values override parent/default light behavior. The editor constrains
 * the value to vanilla's {@code 0..15} range.
 */
public class ToggleLightValue extends ToggleObject<Integer> {
    @Configurable
    @NumberRange(range = {0, 15})
    private int value;

    /**
     * Creates a light-level toggle.
     *
     * @param value  light level, expected in {@code 0..15}
     * @param enable whether this value should override inherited light
     */
    public ToggleLightValue(int value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled light-level toggle.
     *
     * @param value light level, expected in {@code 0..15}
     */
    public ToggleLightValue(int value) {
        this(value, true);
    }

    /**
     * Creates a toggle with light level {@code 0}.
     *
     * @param enable initial enabled state
     */
    public ToggleLightValue(boolean enable) {
        this(0, enable);
    }

    /**
     * Creates a disabled light-level toggle.
     */
    public ToggleLightValue() {
        this(false);
    }

    /**
     * Returns the stored light level.
     *
     * @return light level, expected in {@code 0..15}
     */
    @Override
    public Integer getValue() {
        return value;
    }

    /**
     * Stores a light level.
     *
     * @param value light level; callers should provide values in {@code 0..15}
     */
    @Override
    public void setValue(Integer value) {
        this.value = value;
    }
}
