package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Per-side automatic transfer configuration for a trait.
 *
 * <p>The business goal is to let a machine periodically push or pull resources
 * on sides relative to its front facing. Instances are mutable definition/editor
 * state; runtime readers should treat them as owned by the machine thread.
 * {@link #getIO(Direction, Direction)} is side-effect free.</p>
 */
@Getter
@Setter
public class AutoIO {
    @Configurable(name = "config.definition.trait.capability_io.front")
    private IO frontIO = IO.NONE;

    @Configurable(name = "config.definition.trait.capability_io.back")
    private IO backIO = IO.NONE;

    @Configurable(name = "config.definition.trait.capability_io.left")
    private IO leftIO = IO.NONE;

    @Configurable(name = "config.definition.trait.capability_io.right")
    private IO rightIO = IO.NONE;

    @Configurable(name = "config.definition.trait.capability_io.top")
    private IO topIO = IO.NONE;

    @Configurable(name = "config.definition.trait.capability_io.bottom")
    private IO bottomIO = IO.NONE;

    @Configurable(name = "config.definition.trait.auto_io.interval", tips = "config.definition.trait.auto_io.interval.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int interval = 20;

    /**
     * Resolves configured IO for a world side.
     *
     * <p>Preconditions: {@code front} must be a valid machine front. For vertical
     * front facings, horizontal sides collapse to {@code topIO}; for horizontal
     * fronts, {@code UP} and {@code DOWN} use top/bottom directly and horizontal
     * sides are interpreted relative to front. A {@code null} side is treated as
     * no side and returns {@link IO#NONE}.</p>
     *
     * @param front machine front direction
     * @param side  world side being queried, or {@code null} for no side
     * @return configured IO for the side
     */
    public IO getIO(Direction front, @Nullable Direction side) {
        if (front.getAxis() == Direction.Axis.Y) {
            if (side == front) {
                return frontIO;
            } else if (side == front.getOpposite()) {
                return backIO;
            } else {
                return topIO;
            }
        }
        if (side == Direction.UP) {
            return topIO;
        } else if (side == Direction.DOWN) {
            return bottomIO;
        } else if (side == front) {
            return frontIO;
        } else if (side == front.getOpposite()) {
            return backIO;
        } else if (side == front.getClockWise()) {
            return rightIO;
        } else if (side == front.getCounterClockWise()) {
            return leftIO;
        }
        return IO.NONE;
    }
}
