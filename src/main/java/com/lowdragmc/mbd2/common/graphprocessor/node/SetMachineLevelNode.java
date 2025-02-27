package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;

@LDLRegister(name = "set machine level", group = "graph_processor.node.mbd2.machine")
public class SetMachineLevelNode extends LinearTriggerNode {
    @InputPort
    public MBDMachine machine;
    @InputPort(name = "machine level")
    public int machineLevel;

    @Override
    protected void process() {
        if (machine != null) {
            machine.setMachineLevel(machineLevel);
        }
    }
}
