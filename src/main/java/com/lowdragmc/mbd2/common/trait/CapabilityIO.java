package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Per-side Forge capability access configuration.
 *
 * <p>The business goal is to answer whether a queried side should allow import,
 * export, both, or no access for a simple capability trait. Instances are mutable
 * editor/definition state and should be read from the owning machine thread once
 * runtime traits exist.</p>
 */
@Getter
@Setter
public class CapabilityIO {
    @Configurable(name = "config.definition.trait.capability_io.internal", tips = "config.definition.trait.capability_io.internal.tooltip")
    private IO internal = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.front")
    private IO frontIO = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.back")
    private IO backIO = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.left")
    private IO leftIO = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.right")
    private IO rightIO = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.top")
    private IO topIO = IO.BOTH;
    @Configurable(name = "config.definition.trait.capability_io.bottom")
    private IO bottomIO = IO.BOTH;

    /**
     * Resolves capability IO for a queried world side.
     *
     * <p>{@code side == null} represents an internal/unsided capability query and
     * returns {@code internal} for non-vertical fronts. For vertical fronts, only
     * the front and back directions are directional; all other sides use
     * {@code internal}. Side effects: none.</p>
     *
     * @param front machine front direction
     * @param side  queried world side, or {@code null} for unsided access
     * @return configured capability IO for that query
     */
    public IO getIO(Direction front, @Nullable Direction side) {
        if (front.getAxis() == Direction.Axis.Y) {
            if (side == front) {
                return frontIO;
            } else if (side == front.getOpposite()) {
                return backIO;
            } else {
                return internal;
            }
        }
        if (side == null) {
            return internal;
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
