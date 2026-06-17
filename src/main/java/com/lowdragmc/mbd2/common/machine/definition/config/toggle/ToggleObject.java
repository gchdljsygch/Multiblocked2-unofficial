package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;

/**
 * Base wrapper for configuration values that can be enabled or disabled.
 * <p>
 * A disabled wrapper still stores a value, but callers such as machine state
 * inheritance and optional renderer settings usually treat the value as absent
 * and fall back to a parent/default value. The {@link #enable} flag is persisted
 * by LowDragLib; concrete subclasses decide how the value itself is persisted.
 *
 * @param <T> wrapped value type
 */
public abstract class ToggleObject<T> implements IToggleConfigurable {

    @Getter
    @Setter
    @Persisted
    protected boolean enable;

    /**
     * Returns the wrapped value regardless of whether this wrapper is enabled.
     *
     * @return stored value; may be {@code null} for wrapper types that allow an
     * absent value
     */
    public abstract T getValue();

    /**
     * Replaces the wrapped value.
     *
     * @param value value to store; null handling is defined by each subclass
     */
    public abstract void setValue(T value);

    /**
     * Creates a disabled wrapper with a {@code null} value.
     *
     * @param <T> wrapped value type
     * @return disabled wrapper
     */
    public static <T> ToggleObject<T> ofDisabled() {
        return of(false, null);
    }

    /**
     * Creates a disabled wrapper with a prefilled value.
     *
     * @param value value kept for later enabling
     * @param <T>   wrapped value type
     * @return disabled wrapper
     */
    public static <T> ToggleObject<T> ofDisabled(T value) {
        return of(false, value);
    }

    /**
     * Creates an anonymous toggle wrapper.
     *
     * @param enabled initial enabled state
     * @param value   initial wrapped value
     * @param <T>     wrapped value type
     * @return toggle wrapper with simple getter/setter storage
     */
    public static <T> ToggleObject<T> of(boolean enabled, T value) {
        var wrapper = new ToggleObject<T>() {
            @Getter
            @Setter
            private T value;
        };
        wrapper.setValue(value);
        wrapper.setEnable(enabled);
        return wrapper;
    }

}
