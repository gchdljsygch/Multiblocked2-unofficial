package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeSerializer;
import net.minecraft.nbt.CompoundTag;

/**
 * Graph node that serializes a runtime {@link MBDRecipe} back into graph-friendly values.
 *
 * <p>The node exposes the recipe ID as text and the serialized recipe body as an NBT compound. It resets both outputs
 * to {@code null} when no input recipe is supplied, making downstream graph branches see an explicit absence.</p>
 */
@LDLRegister(name = "recipe deserialize", group = "graph_processor.node.mbd2.machine.recipe")
public class RecipeDeserializeNode extends BaseNode {
    /**
     * Recipe to serialize.
     */
    @InputPort
    public MBDRecipe in;
    /**
     * String form of {@link MBDRecipe#getId()}.
     */
    @OutputPort
    public String id;
    /**
     * NBT payload produced by {@link MBDRecipeSerializer}.
     */
    @OutputPort
    public CompoundTag recipe;

    /**
     * Serializes the input recipe or clears outputs when no recipe is present.
     */
    @Override
    protected void process() {
        id = null;
        recipe = null;
        if (in != null) {
            id = in.getId().toString();
            recipe = MBDRecipeSerializer.SERIALIZER.toNBT(in);
        }
    }
}
