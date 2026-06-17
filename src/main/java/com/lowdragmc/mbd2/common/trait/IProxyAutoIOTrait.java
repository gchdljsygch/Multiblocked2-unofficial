package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigPartSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Trait that can execute automatic IO through a target port.
 *
 * <p>The business goal is to let both local auto IO traits and multiblock proxy
 * parts reuse one transfer hook. Proxy calls can originate from
 * {@link ConfigPartSettings#proxyControllerCapabilities()}, where {@code port}
 * may be a controller or part position instead of this trait's own block
 * position. Calls are expected on the logical server thread.</p>
 */
public interface IProxyAutoIOTrait extends ITrait {
    /**
     * Performs one automatic IO pass for a port side.
     *
     * <p>Side effects are implementation-specific and usually include moving
     * resources between this trait's storage and a neighboring capability.</p>
     *
     * @param port world position whose neighbor should be accessed
     * @param side side of {@code port} to transfer through
     * @param io   transfer direction to perform; never {@link IO#NONE} in normal
     *             default auto-IO calls
     */
    void handleAutoIO(BlockPos port, Direction side, IO io);

}
