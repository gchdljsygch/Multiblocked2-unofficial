package com.lowdragmc.mbd2.common.blockentity;

import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared dispatch bridge from vanilla block-entity ticking to MBD machines.
 *
 * <p>The bridge deliberately has no {@code Block} superclass so holder
 * lifecycle and tick dispatch can be exercised in JVM tests without loading
 * the vanilla block registry. Registered machine blocks call this method from
 * their vanilla ticker.</p>
 */
public final class MachineBlockEntityTicker {

    private MachineBlockEntityTicker() {
    }

    /**
     * Dispatches one logical tick to the machine attached to a block entity.
     *
     * <p>Known MBD holders are read directly, avoiding an unnecessary
     * capability lookup on every tick. The capability path remains available
     * for compatible third-party holders.</p>
     *
     * @param clientSide whether the owning level is client-side
     * @param blockEntity candidate block entity supplied by vanilla
     */
    public static void tick(boolean clientSide, BlockEntity blockEntity) {
        if (blockEntity instanceof IMachineBlockEntity holder) {
            tickMachine(clientSide, holder.getMetaMachine());
            return;
        }
        IMachine.ofMachine(blockEntity).ifPresent(machine -> tickMachine(clientSide, machine));
    }

    private static void tickMachine(boolean clientSide, IMachine machine) {
        if (machine == null) return;
        if (clientSide) {
            machine.clientTick();
        } else {
            machine.serverTick();
        }
    }
}
