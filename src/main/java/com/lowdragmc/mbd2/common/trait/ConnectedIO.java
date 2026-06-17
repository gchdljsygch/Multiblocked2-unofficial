package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Per-side connection mask for visual or logical connectivity.
 *
 * <p>The business goal is to decide whether a side should be considered
 * connected relative to the machine's front facing. This is a lightweight
 * mutable configuration object; {@link #getConnection(Direction, Direction)} is
 * side-effect free and safe to call from render or recipe checks when the
 * configuration is not being edited concurrently.</p>
 */
@Getter
@Setter
public class ConnectedIO {
    @Configurable(name = "config.definition.trait.capability_io.front")
    private boolean frontIO = true;

    @Configurable(name = "config.definition.trait.capability_io.back")
    private boolean backIO = true;

    @Configurable(name = "config.definition.trait.capability_io.left")
    private boolean leftIO = true;

    @Configurable(name = "config.definition.trait.capability_io.right")
    private boolean rightIO = true;

    @Configurable(name = "config.definition.trait.capability_io.top")
    private boolean topIO = true;

    @Configurable(name = "config.definition.trait.capability_io.bottom")
    private boolean bottomIO = true;

    /**
     * Resolves whether a queried world side is connected.
     *
     * <p>For vertical fronts, front/back use their dedicated flags and all other
     * sides use {@code topIO}. For horizontal fronts, horizontal sides are
     * interpreted relative to front. A {@code null} side is treated as
     * disconnected.</p>
     *
     * @param front machine front direction
     * @param side  world side being queried, or {@code null}
     * @return {@code true} when the side is enabled for connection
     */
    public boolean getConnection(Direction front, @Nullable Direction side) {
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
        return false;
    }
}
