package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;

/**
 * Trigger node that writes a machine level value.
 *
 * <p>The level is passed through unchanged; any semantic range checks are owned by the machine implementation and
 * machine definitions that consume the level.</p>
 */
@LDLRegister(name = "set machine level", group = "graph_processor.node.mbd2.machine")
public class SetMachineLevelNode extends LinearTriggerNode {
    /**
     * Machine to mutate.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * New machine level value.
     */
    @InputPort(name = "machine level")
    public int machineLevel;

    /**
     * Applies the level to the input machine.
     */
    @Override
    protected void process() {
        if (machine != null) {
            machine.setMachineLevel(machineLevel);
        }
    }
}
