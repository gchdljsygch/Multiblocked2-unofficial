package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import net.minecraft.nbt.CompoundTag;

/**
 * Graph node that exposes scalar metadata from an {@link MBDRecipe}.
 *
 * <p>The node is read-only with respect to the recipe and exports values commonly used by recipe modification graphs:
 * ID, duration, priority, fuel flag, and custom data. A {@code null} input leaves existing field values untouched.</p>
 */
@LDLRegister(name = "recipe info", group = "graph_processor.node.mbd2.machine.recipe")
public class RecipeInfoNode extends BaseNode {
    /**
     * Recipe to inspect.
     */
    @InputPort
    public MBDRecipe recipe;
    /**
     * String form of the recipe ID.
     */
    @OutputPort(name = "recipe id")
    public String recipeID;
    /**
     * Recipe duration in ticks.
     */
    @OutputPort
    public int duration;
    /**
     * Recipe priority used by recipe selection.
     */
    @OutputPort
    public int priority;
    /**
     * Whether this recipe is registered as fuel.
     */
    @OutputPort(name = "is fuel recipe")
    public boolean isFuel;
    /**
     * Recipe custom data tag.
     */
    @OutputPort(name = "data")
    public CompoundTag data;

    /**
     * Copies recipe metadata into output fields.
     */
    @Override
    protected void process() {
        if (recipe != null) {
            recipeID = recipe.getId().toString();
            duration = recipe.duration;
            priority = recipe.priority;
            isFuel = recipe.isFuel;
            data = recipe.data;
        }
    }
}
