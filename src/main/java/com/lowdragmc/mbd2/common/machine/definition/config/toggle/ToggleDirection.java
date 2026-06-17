package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;

/**
 * Toggle wrapper for a direction value.
 * <p>
 * Disabled direction toggles represent "no override"; enabled toggles store an
 * explicit Minecraft direction. The default stored value is
 * {@link Direction#NORTH}.
 */
public class ToggleDirection extends ToggleObject<Direction> {

    @Getter
    @Setter
    @Configurable
    private Direction value = Direction.NORTH;

    /**
     * Creates a direction toggle.
     *
     * @param value  direction to store
     * @param enable whether the stored direction is active
     */
    public ToggleDirection(Direction value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled direction toggle.
     *
     * @param value direction to store
     */
    public ToggleDirection(Direction value) {
        this(value, true);
    }

    /**
     * Creates a toggle using north as the stored direction.
     *
     * @param enable initial enabled state
     */
    public ToggleDirection(boolean enable) {
        this(Direction.NORTH, enable);
    }

    /**
     * Creates a disabled direction toggle.
     */
    public ToggleDirection() {
        this(false);
    }

}
