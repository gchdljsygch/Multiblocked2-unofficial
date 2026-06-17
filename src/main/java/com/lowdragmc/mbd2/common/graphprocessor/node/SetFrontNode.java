package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.core.Direction;

/**
 * Trigger node that changes a machine's front-facing direction.
 *
 * <p>The node mutates the machine only when both machine and direction inputs are present. Direction validity beyond
 * non-null is delegated to {@link MBDMachine#setFrontFacing(Direction)}.</p>
 */
@LDLRegister(name = "set machine front", group = "graph_processor.node.mbd2.machine")
public class SetFrontNode extends LinearTriggerNode {
    /**
     * Machine to rotate.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * New front direction.
     */
    @InputPort
    public Direction front;

    /**
     * Applies the new front direction to the machine.
     */
    @Override
    protected void process() {
        if (machine != null && front != null) {
            machine.setFrontFacing(front);
        }
    }
}
