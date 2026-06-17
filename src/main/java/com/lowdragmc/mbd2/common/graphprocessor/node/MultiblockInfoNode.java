package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;

import java.util.List;

/**
 * Graph node that exposes controller-specific information for multiblock machines.
 *
 * <p>When the input machine is an {@link MBDMultiblockMachine}, the node reports whether the structure is formed and
 * gathers machine parts as {@link MBDMachine} instances. Non-multiblock inputs leave existing field values untouched.</p>
 */
@LDLRegister(name = "multiblock info", group = "graph_processor.node.mbd2.machine")
public class MultiblockInfoNode extends BaseNode {
    /**
     * Machine to inspect; only multiblock controllers produce output.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Whether the multiblock controller currently has a valid formed structure.
     */
    @OutputPort(name = "is formed")
    public boolean isFormed;
    /**
     * Parts belonging to the formed controller, filtered to MBD machine parts.
     */
    @OutputPort
    public List<MBDMachine> parts;

    /**
     * Samples multiblock formation state and part list from the input controller.
     */
    @Override
    protected void process() {
        if (machine instanceof MBDMultiblockMachine multiblock) {
            isFormed = multiblock.isFormed();
            parts = multiblock.getParts().stream().filter(MBDMachine.class::isInstance).map(MBDMachine.class::cast).toList();
        }
    }
}
