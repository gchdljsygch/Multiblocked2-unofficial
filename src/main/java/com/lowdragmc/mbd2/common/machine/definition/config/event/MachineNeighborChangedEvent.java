package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;


/**
 * Fired when Minecraft reports a neighbor change to a machine block.
 */
@Getter
@LDLRegister(name = "MachineNeighborChangedEvent", group = "MachineEvent")
public class MachineNeighborChangedEvent extends MachineEvent {
    /**
     * Block type that caused the neighbor update.
     */
    @GraphParameterGet
    public final Block block;
    /**
     * Position of the neighbor that changed.
     */
    @GraphParameterGet(displayName = "pos")
    public final BlockPos fromPos;

    /**
     * Creates an event for a vanilla neighbor update.
     * <p>
     * The event is notification-only; handlers can react to redstone, block, or capability changes near the machine but
     * should perform world mutation on the logical server thread.
     *
     * @param machine machine receiving the neighbor update
     * @param block   block type that caused the update
     * @param fromPos position of the changed neighbor
     */
    public MachineNeighborChangedEvent(MBDMachine machine, Block block, BlockPos fromPos) {
        super(machine);
        this.block = block;
        this.fromPos = fromPos;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("block")).ifPresent(p -> p.setValue(block));
        Optional.ofNullable(exposedParameters.get("fromPos")).ifPresent(p -> p.setValue(fromPos));
    }
}
