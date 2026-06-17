package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.nbt.CompoundTag;

/**
 * Trigger node that replaces a machine's custom data tag.
 *
 * <p>Execution has a direct side effect on the supplied {@link MBDMachine}. The node performs no defensive copy, so the
 * caller-provided tag object becomes the machine's custom data according to {@link MBDMachine#setCustomData}.</p>
 */
@LDLRegister(name = "set custom data", group = "graph_processor.node.mbd2.machine")
public class SetCustomDataNode extends LinearTriggerNode {
    /**
     * Machine to mutate; {@code null} makes the node a no-op.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Custom data to assign; may be {@code null} if the machine implementation accepts clearing data.
     */
    @InputPort
    public CompoundTag data;

    /**
     * Assigns {@link #data} to the input machine.
     */
    @Override
    protected void process() {
        if (machine != null) {
            machine.setCustomData(data);
        }
    }
}
