package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Trait that performs configured automatic IO on neighboring blocks.
 *
 * <p>The business goal is to periodically call
 * {@link #handleAutoIO(BlockPos, Direction, IO)} for each configured side so
 * concrete traits can auto-insert or auto-extract items, fluids, energy, or
 * other resources. The default implementation runs on the server tick path and
 * assumes the trait is owned by the machine's logical server thread.</p>
 */
public interface IAutoIOTrait extends IProxyAutoIOTrait {
    /**
     * Returns this trait's automatic IO configuration.
     *
     * @return configuration used by the default server tick, or {@code null} when
     * this trait currently does not support automatic IO
     */
    @Nullable
    AutoIO getAutoIO();

    /**
     * Runs automatic IO on the configured interval.
     *
     * <p>Side effects: on matching ticks, calls
     * {@link #handleAutoIO(BlockPos, Direction, IO)} once for each world side
     * whose resolved IO is not {@link IO#NONE}. The port position is the owning
     * machine position.</p>
     */
    @Override
    default void serverTick() {
        var autoIO = getAutoIO();
        if (autoIO != null && getMachine().getOffsetTimer() % autoIO.getInterval() == 0) {
            var pos = getMachine().getPos();
            var front = getMachine().getFrontFacing().orElse(Direction.NORTH);
            for (var side : Direction.values()) {
                var io = autoIO.getIO(front, side);
                if (io != IO.NONE) {
                    handleAutoIO(pos, side, io);
                }
            }
        }
    }
}
