package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.DefaultValue;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Toggle wrapper for an axis-aligned bounding box.
 * <p>
 * Machine states use this for optional render bounding boxes. When disabled,
 * callers normally inherit a parent state's box or use the default shape-based
 * renderer bounds. Coordinates are in block-local units.
 */
public class ToggleAABB extends ToggleObject<AABB> {

    @Getter
    @Setter
    @Configurable
    @DefaultValue(numberValue = {0, 0, 0, 1, 1, 1})
    private AABB value;

    /**
     * Creates a bounding-box toggle.
     *
     * @param value  block-local box to store
     * @param enable whether the box should override parent/default behavior
     */
    public ToggleAABB(AABB value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled bounding-box toggle.
     *
     * @param value block-local box to store
     */
    public ToggleAABB(AABB value) {
        this(value, true);
    }

    /**
     * Creates a toggle with a default zero-size box at the origin.
     *
     * @param enable initial enabled state
     */
    public ToggleAABB(boolean enable) {
        this(new AABB(BlockPos.ZERO), enable);
    }

    /**
     * Creates a disabled bounding-box toggle.
     */
    public ToggleAABB() {
        this(false);
    }
}
