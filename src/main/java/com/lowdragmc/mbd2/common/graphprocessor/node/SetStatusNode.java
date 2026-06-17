package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;

/**
 * Trigger node that sets a machine state by name.
 *
 * <p>The node is intentionally string-based so event graphs can route status values from text parameters or recipe
 * status nodes. Validation and fallback behavior are delegated to {@link MBDMachine#setMachineState(String)}.</p>
 */
@LDLRegister(name = "set machine status", group = "graph_processor.node.mbd2.machine")
public class SetStatusNode extends LinearTriggerNode {
    /**
     * Machine to mutate.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Machine-state name to apply.
     */
    @InputPort
    public String status;

    /**
     * Applies the state name when both inputs are present.
     */
    @Override
    protected void process() {
        if (machine != null && status != null) {
            machine.setMachineState(status);
        }
    }
}
