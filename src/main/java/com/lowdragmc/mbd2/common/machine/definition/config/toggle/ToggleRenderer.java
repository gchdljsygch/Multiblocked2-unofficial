package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import lombok.Getter;
import lombok.Setter;

/**
 * Toggle wrapper for an {@link IRenderer}.
 * <p>
 * Disabled renderer toggles represent "inherit or use default renderer" in
 * machine states and item/entity model settings. Enabled toggles store the
 * renderer that should be used directly.
 */
public class ToggleRenderer extends ToggleObject<IRenderer> {
    @Getter
    @Setter
    @Configurable
    private IRenderer value;

    /**
     * Creates a renderer toggle.
     *
     * @param value  renderer to store; callers normally use
     *               {@link IRenderer#EMPTY} instead of {@code null}
     * @param enable whether the renderer should override inherited/default
     *               behavior
     */
    public ToggleRenderer(IRenderer value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled renderer toggle.
     *
     * @param value renderer to store
     */
    public ToggleRenderer(IRenderer value) {
        this(value, true);
    }

    /**
     * Creates a toggle storing {@link IRenderer#EMPTY}.
     *
     * @param enable initial enabled state
     */
    public ToggleRenderer(boolean enable) {
        this(IRenderer.EMPTY, enable);
    }

    /**
     * Creates a disabled renderer toggle.
     */
    public ToggleRenderer() {
        this(false);
    }

}
