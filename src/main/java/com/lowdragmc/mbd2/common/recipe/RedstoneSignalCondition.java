package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
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
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;

/**
 * Recipe condition that requires a redstone neighbor signal range at the machine position.
 *
 * <p>The business goal is to let recipes depend on vanilla redstone control. Signal bounds use vanilla's inclusive
 * {@code 0..15} range. Matching reads {@link net.minecraft.world.level.Level#getBestNeighborSignal} at the machine
 * position on the recipe logic thread and has no side effects.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RedstoneSignalCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static RedstoneSignalCondition INSTANCE = new RedstoneSignalCondition();
    @Configurable(name = "config.recipe.condition.redstone_signal.signal.min")
    @NumberRange(range = {0f, 15f})
    private int minSignal;
    @Configurable(name = "config.recipe.condition.redstone_signal.signal.max")
    @NumberRange(range = {0f, 15f})
    private int maxSignal;

    /**
     * Creates a redstone-signal condition.
     *
     * @param minSignal minimum accepted signal, inclusive; expected range is {@code 0..15}
     * @param maxSignal maximum accepted signal, inclusive; expected range is {@code 0..15}
     */
    public RedstoneSignalCondition(int minSignal, int maxSignal) {
        this.minSignal = minSignal;
        this.maxSignal = maxSignal;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code redstone_signal}
     */
    @Override
    public String getType() {
        return "redstone_signal";
    }

    /**
     * Builds the tooltip describing the accepted signal range.
     *
     * @return localized tooltip component
     */
    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.redstone_signal.tooltip", minSignal, maxSignal);
    }

    /**
     * Returns the editor icon for this condition.
     *
     * @return redstone torch item texture
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.REDSTONE_TORCH);
    }

    /**
     * Tests the best neighboring redstone signal at the machine position.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine level and position
     * @return {@code true} when the signal is inside the inclusive configured range
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var pos = recipeLogic.getMachine().getPos();
        var signal = recipeLogic.getMachine().getLevel().getBestNeighborSignal(pos);
        return signal >= minSignal && signal <= maxSignal;
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with {@code minSignal} and {@code maxSignal}
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("minSignal", minSignal);
        config.addProperty("maxSignal", maxSignal);
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
        minSignal = GsonHelper.getAsInt(config, "minSignal", 0);
        maxSignal = GsonHelper.getAsInt(config, "maxSignal", 1);
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
        minSignal = buf.readVarInt();
        maxSignal = buf.readVarInt();
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
        buf.writeVarInt(minSignal);
        buf.writeVarInt(maxSignal);
    }

    /**
     * Serializes the condition to NBT.
     *
     * @return NBT tag with signal bounds
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putInt("minSignal", minSignal);
        tag.putInt("maxSignal", maxSignal);
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
        minSignal = tag.getInt("minSignal");
        maxSignal = tag.getInt("maxSignal");
        return this;
    }

}
