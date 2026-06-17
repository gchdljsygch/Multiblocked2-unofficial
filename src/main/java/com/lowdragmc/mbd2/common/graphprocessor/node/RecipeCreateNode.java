package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Graph node that creates an {@link MBDRecipe} from NBT, JSON, or JSON text.
 *
 * <p>The node is used by event graphs that synthesize recipes at runtime. The recipe ID can come from an input port or
 * from the configured fallback ID; invalid wired IDs abort processing. Parsing failures from malformed JSON or invalid
 * fallback IDs propagate to the graph executor, matching the serializer behavior.</p>
 */
@LDLRegister(name = "recipe create", group = "graph_processor.node.mbd2.machine.recipe")
public class RecipeCreateNode extends BaseNode {
    /**
     * Recipe payload; supported types are {@link CompoundTag}, {@link JsonObject}, and JSON {@link CharSequence}.
     */
    @InputPort
    public Object in;
    /**
     * Optional recipe ID string. When connected it must be a valid resource location.
     */
    @InputPort
    public String id;
    /**
     * Deserialized recipe output, or the previous value when input is unsupported.
     */
    @OutputPort
    public MBDRecipe out;
    /**
     * Fallback recipe ID used when the {@link #id} input is not connected.
     */
    @Configurable(name = "id")
    public String internalID = "mbd2:recipe_on_the_fly";

    /**
     * Converts the input payload into an MBD recipe using the selected recipe ID.
     */
    @Override
    protected void process() {
        if (id != null && !ResourceLocation.isValidResourceLocation(id)) return;
        var recipeID = ResourceLocation.parse(id == null ? internalID : id);
        if (in instanceof CompoundTag tag) {
            out = MBDRecipeSerializer.SERIALIZER.fromNBT(recipeID, tag);
        } else if (in instanceof JsonObject json) {
            out = MBDRecipeSerializer.SERIALIZER.fromJson(recipeID, json);
        } else if (in instanceof CharSequence) {
            var json = JsonParser.parseString(in.toString());
            if (json.isJsonObject()) {
                out = MBDRecipeSerializer.SERIALIZER.fromJson(recipeID, json.getAsJsonObject());
            }
        }
    }

    /**
     * Hides the fallback ID configurator when the ID input is wired.
     *
     * @param father configurator group receiving this node's editor controls
     */
    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        for (var port : getInputPorts()) {
            if (port.fieldName.equals("id")) {
                if (!port.getEdges().isEmpty()) return;
            }
        }
        super.buildConfigurator(father);
    }
}
