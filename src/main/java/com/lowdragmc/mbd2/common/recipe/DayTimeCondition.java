package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
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
 * Recipe condition that requires the machine's level to be day or night.
 *
 * <p>The condition reads {@link net.minecraft.world.level.Level#isDay()} from the machine's current level during
 * recipe checks. Detached machines fail the condition because there is no level to query. Matching has no side effects
 * and should run on the recipe logic's normal server tick thread.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DayTimeCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static DayTimeCondition INSTANCE = new DayTimeCondition();
    @Configurable(name = "config.recipe.condition.day_time.is_day")
    private boolean isDay;

    /**
     * Creates a day/night condition.
     *
     * @param isDay {@code true} to require day, {@code false} to require night
     */
    public DayTimeCondition(boolean isDay) {
        this.isDay = isDay;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code day_time}
     */
    @Override
    public String getType() {
        return "day_time";
    }

    /**
     * Returns the localized tooltip for the required time state.
     *
     * @return day or night tooltip
     */
    @Override
    public Component getTooltips() {
        return isDay ? Component.translatable("recipe.condition.day_time.tooltip.true") : Component.translatable("recipe.condition.day_time.tooltip.false");
    }

    /**
     * Returns the editor icon for this condition.
     *
     * @return clock item texture
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.CLOCK);
    }

    /**
     * Tests whether the machine's level currently matches the configured day/night state.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine
     * @return {@code true} when the machine is attached to a level whose day state equals {@code isDay}
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var level = recipeLogic.machine.getLevel();
        return level != null && level.isDay() == isDay;
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with {@code isDay}
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("isDay", isDay);
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
        isDay = GsonHelper.getAsBoolean(config, "isDay", false);
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
        isDay = buf.readBoolean();
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
        buf.writeBoolean(isDay);
    }

    /**
     * Serializes the condition to NBT.
     *
     * @return NBT tag with {@code isDay}
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putBoolean("isDay", isDay);
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
        isDay = tag.getBoolean("isDay");
        return this;
    }

}
