package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;

/**
 * Recipe condition that requires a minimum machine level.
 *
 * <p>The business goal is to let recipes scale with machine tier or user-defined level. The configured level is a
 * minimum inclusive threshold and is intended to be non-negative. Matching reads the machine level from the active
 * recipe logic and has no side effects.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class MachineLevelCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static MachineLevelCondition INSTANCE = new MachineLevelCondition();
    @Configurable(name = "config.recipe.condition.machine_level.level", tips = "config.recipe.condition.machine_level.level.tips")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int level;

    /**
     * Creates a minimum-level condition.
     *
     * @param level required machine level, inclusive; expected range is {@code [0, Integer.MAX_VALUE]}
     */
    public MachineLevelCondition(int level) {
        this.level = level;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code machine_level}
     */
    @Override
    public String getType() {
        return "machine_level";
    }

    /**
     * Builds the tooltip describing the required level.
     *
     * @return localized tooltip component
     */
    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.machine_level.tooltip", this.level);
    }

    /**
     * Returns the editor icon for this condition.
     *
     * @return text texture containing {@code LV}
     */
    @Override
    public IGuiTexture getIcon() {
        return new TextTexture("LV");
    }

    /**
     * Tests the active machine level.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine
     * @return {@code true} when {@code machine.getMachineLevel() >= level}
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        return recipeLogic.machine.getMachineLevel() >= this.level;
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with {@code level}
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("level", this.level);
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
        this.level = GsonHelper.getAsInt(config, "level");
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
        level = buf.readVarInt();
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
        buf.writeVarInt(level);
    }

    /**
     * Serializes the condition to NBT.
     *
     * @return NBT tag with {@code level}
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putInt("level", level);
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
        level = tag.getInt("level");
        return this;
    }

}
