package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.MBDPartMachine;

import java.util.List;

/**
 * Graph node that exposes controller links for a multiblock part machine.
 *
 * <p>The node is read-only with respect to the part. It reports whether the part is formed and lists controllers that
 * are known MBD multiblock machines. Non-part inputs leave existing output values unchanged.</p>
 */
@LDLRegister(name = "part info", group = "graph_processor.node.mbd2.machine")
public class PartInfoNode extends BaseNode {
    /**
     * Machine to inspect; only part machines produce output.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Whether the part currently belongs to a formed multiblock.
     */
    @OutputPort(name = "is formed")
    public boolean isFormed;
    /**
     * Controllers currently linked to the part.
     */
    @OutputPort
    public List<MBDMultiblockMachine> controllers;

    /**
     * Samples part formation state and controller list from the input machine.
     */
    @Override
    protected void process() {
        if (machine instanceof MBDPartMachine part) {
            isFormed = part.isFormed();
            controllers = part.getControllers().stream().filter(MBDMultiblockMachine.class::isInstance).map(MBDMultiblockMachine.class::cast).toList();
        }
    }
}
