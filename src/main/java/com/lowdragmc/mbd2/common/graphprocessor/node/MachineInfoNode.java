package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Graph node that exposes runtime information from an {@link MBDMachine}.
 *
 * <p>The node is read-only with respect to the machine: it samples world, position, facing, state, recipe status,
 * custom data, and machine level into output ports. Missing input leaves the previous port values untouched, matching
 * LowDragLib node field semantics.</p>
 */
@LDLRegister(name = "machine info", group = "graph_processor.node.mbd2.machine")
public class MachineInfoNode extends BaseNode {
    /**
     * Machine to inspect; {@code null} skips processing.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Level containing the machine.
     */
    @OutputPort
    public Level level;
    /**
     * Machine block position encoded as integer coordinates in a float vector.
     */
    @OutputPort
    public Vector3f xyz;
    /**
     * Machine front direction, defaulting to north when the machine has no stored front.
     */
    @OutputPort
    public Direction front;
    /**
     * Current machine-state name.
     */
    @OutputPort
    public String status;
    /**
     * Current recipe-logic status text.
     */
    @OutputPort(name = "recipe status", tips = "graph_processor.node.mbd2.recipe_logic.status.tips")
    public String recipeStatus;
    /**
     * Live custom-data tag returned by the machine.
     */
    @OutputPort(name = "custom data")
    public CompoundTag customData;
    /**
     * Current machine level.
     */
    @OutputPort(name = "machine level")
    public int machineLevel;

    /**
     * Samples machine state into this node's output fields.
     */
    @Override
    protected void process() {
        if (machine != null) {
            var pos = machine.getPos();
            level = machine.getLevel();
            xyz = new Vector3f(pos.getX(), pos.getY(), pos.getZ());
            front = machine.getFrontFacing().orElse(Direction.NORTH);
            status = machine.getMachineState().name();
            recipeStatus = machine.getRecipeLogic().getStatus().toString();
            customData = machine.getCustomData();
            machineLevel = machine.getMachineLevel();
        }
    }
}
