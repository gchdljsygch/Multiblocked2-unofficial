package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.utils.NBTToJsonConverter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.mojang.realmsclient.util.JsonUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.crafting.CraftingHelper;

import javax.annotation.Nonnull;

/**
 * Recipe condition that requires the machine data NBT to contain a configured subset.
 *
 * <p>The business goal is to gate recipes on custom machine state written by scripts, traits, or persisted holder data.
 * When {@code onlyCheckCustomData} is true, matching compares against {@link MBDMachine#getCustomData()}; otherwise it
 * compares against the holder's full saved NBT. The configured data is treated as a required subset by merging it into
 * a copy of the current machine data and checking whether the merge changed anything. Matching has no side effects on
 * the machine because it works on a copy.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class MachineNBTCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static MachineNBTCondition INSTANCE = new MachineNBTCondition();
    @Configurable(name = "config.recipe.condition.machine_custom_data.data", tips = "config.recipe.condition.machine_custom_data.data.tips")
    private CompoundTag data = new CompoundTag();
    @Configurable(name = "config.recipe.condition.machine_custom_data.only_check_custom_data",
            tips = {"config.recipe.condition.machine_custom_data.only_check_custom_data.tips.0",
                    "config.recipe.condition.machine_custom_data.only_check_custom_data.tips.1"})
    private boolean onlyCheckCustomData = true;

    /**
     * Creates a machine-data condition.
     *
     * @param data                required NBT subset; empty data makes the condition pass only when no required data is
     *                            configured
     * @param onlyCheckCustomData {@code true} to compare only custom machine data, {@code false} to compare full holder
     *                            save data
     */
    public MachineNBTCondition(CompoundTag data, boolean onlyCheckCustomData) {
        this.data = data;
        this.onlyCheckCustomData = onlyCheckCustomData;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code machine_custom_data}
     */
    @Override
    public String getType() {
        return "machine_custom_data";
    }

    /**
     * Builds the tooltip showing the required NBT subset.
     *
     * @return localized tooltip component
     */
    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.machine_custom_data.tooltip", this.data);
    }

    /**
     * Returns the editor icon for this condition.
     *
     * @return text texture containing {@code D}
     */
    @Override
    public IGuiTexture getIcon() {
        return new TextTexture("D");
    }

    /**
     * Tests whether the machine currently contains the required data subset.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine
     * @return {@code true} when the required data is empty, or when the active MBD machine contains every configured
     * key/value in the selected data source
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        // check if the machine has the same custom data
        if (!data.isEmpty() && recipeLogic.getMachine() instanceof MBDMachine mbdMachine) {
            var machineData = onlyCheckCustomData ? mbdMachine.getCustomData() : mbdMachine.getHolder().saveWithId();
            var copied = machineData.copy();
            copied.merge(this.data);
            return copied.equals(machineData);
        }
        return data.isEmpty();
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with {@code data} and {@code onlyCheckCustomData}
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.add("data", NBTToJsonConverter.getObject(this.data));
        config.addProperty("onlyCheckCustomData", this.onlyCheckCustomData);
        return config;
    }

    /**
     * Loads the condition from JSON.
     *
     * @param config JSON object produced by {@link #serialize()}
     * @return this condition instance
     */
    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        this.data = CraftingHelper.getNBT(config.getAsJsonObject("data"));
        this.onlyCheckCustomData = JsonUtils.getBooleanOr("onlyCheckCustomData", config, true);
        return this;
    }

    /**
     * Reads the condition from the network buffer.
     *
     * @param buf source buffer
     * @return this condition instance
     */
    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        data = buf.readNbt();
        onlyCheckCustomData = buf.readBoolean();
        return this;
    }

    /**
     * Writes the condition to the network buffer.
     *
     * @param buf destination buffer
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeNbt(data);
        buf.writeBoolean(onlyCheckCustomData);
    }

    /**
     * Serializes the condition to NBT.
     *
     * @return NBT tag with required data and comparison mode
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.put("data", data);
        tag.putBoolean("onlyCheckCustomData", onlyCheckCustomData);
        return tag;
    }

    /**
     * Loads the condition from NBT.
     *
     * @param tag source tag produced by {@link #toNBT()}
     * @return this condition instance
     */
    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        data = tag.getCompound("data");
        onlyCheckCustomData = !tag.contains("onlyCheckCustomData") || tag.getBoolean("onlyCheckCustomData");
        return this;
    }

}
