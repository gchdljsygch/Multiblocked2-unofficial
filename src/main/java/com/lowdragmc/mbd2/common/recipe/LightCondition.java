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
import net.minecraft.world.level.LightLayer;

import javax.annotation.Nonnull;

/**
 * Recipe condition that requires sky light, block light, and optional sky visibility at the machine position.
 *
 * <p>The business goal is to model environmental recipe gates such as sunlight-only or darkness-only operation. Light
 * ranges are intended to use vanilla's {@code 0..15} brightness scale and are inclusive. Matching reads the machine's
 * current level and position on the recipe logic thread and has no side effects.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class LightCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static LightCondition INSTANCE = new LightCondition();
    @Configurable(name = "config.recipe.condition.light.min_sky_light")
    @NumberRange(range = {0, 15})
    private int minSkyLight = 0;
    @Configurable(name = "config.recipe.condition.light.max_sky_light")
    @NumberRange(range = {0, 15})
    private int maxSkyLight = 15;
    @Configurable(name = "config.recipe.condition.light.min_block_light")
    @NumberRange(range = {0, 15})
    private int minBlockLight = 0;
    @Configurable(name = "config.recipe.condition.light.max_block_light")
    @NumberRange(range = {0, 15})
    private int maxBlockLight = 15;
    @Configurable(name = "config.recipe.condition.light.can_see_sky", tips = "config.recipe.condition.light.can_see_sky.tooltip")
    private boolean canSeeSky;

    /**
     * Creates a light range condition.
     *
     * @param minSkyLight   minimum sky light, inclusive; expected range is {@code 0..15}
     * @param maxSkyLight   maximum sky light, inclusive; expected range is {@code 0..15}
     * @param minBlockLight minimum block light, inclusive; expected range is {@code 0..15}
     * @param maxBlockLight maximum block light, inclusive; expected range is {@code 0..15}
     * @param canSeeSky     {@code true} to additionally require direct sky visibility at the machine position
     */
    public LightCondition(int minSkyLight, int maxSkyLight, int minBlockLight, int maxBlockLight, boolean canSeeSky) {
        this.minSkyLight = minSkyLight;
        this.maxSkyLight = maxSkyLight;
        this.minBlockLight = minBlockLight;
        this.maxBlockLight = maxBlockLight;
        this.canSeeSky = canSeeSky;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code light}
     */
    @Override
    public String getType() {
        return "light";
    }

    /**
     * Builds the tooltip describing the configured light ranges.
     *
     * @return localized tooltip component
     */
    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.light.tooltip", minSkyLight, maxSkyLight, minBlockLight, maxBlockLight, canSeeSky);
    }

    /**
     * Returns the editor icon for this condition.
     *
     * @return daylight detector item texture
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.DAYLIGHT_DETECTOR);
    }

    /**
     * Tests the light at the machine position.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine level and position
     * @return {@code true} when sky and block light are inside their inclusive ranges and sky visibility matches the
     * optional requirement
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var level = recipeLogic.machine.getLevel();
        var pos = recipeLogic.getMachine().getPos();
        var blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        var skyLight = level.getBrightness(LightLayer.SKY, pos);
        return blockLight >= minBlockLight && blockLight <= maxBlockLight && skyLight >= minSkyLight && skyLight <= maxSkyLight && (!canSeeSky || level.canSeeSky(pos));
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with light ranges and sky-visibility flag
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("minSkyLight", minSkyLight);
        config.addProperty("maxSkyLight", maxSkyLight);
        config.addProperty("minBlockLight", minBlockLight);
        config.addProperty("maxBlockLight", maxBlockLight);
        config.addProperty("canSeeSky", canSeeSky);
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
        minSkyLight = GsonHelper.getAsInt(config, "minSkyLight", 0);
        maxSkyLight = GsonHelper.getAsInt(config, "maxSkyLight", 15);
        minBlockLight = GsonHelper.getAsInt(config, "minBlockLight", 0);
        maxBlockLight = GsonHelper.getAsInt(config, "maxBlockLight", 15);
        canSeeSky = GsonHelper.getAsBoolean(config, "canSeeSky", false);
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
        minSkyLight = buf.readVarInt();
        maxSkyLight = buf.readVarInt();
        minBlockLight = buf.readVarInt();
        maxBlockLight = buf.readVarInt();
        canSeeSky = buf.readBoolean();
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
        buf.writeVarInt(minSkyLight);
        buf.writeVarInt(maxSkyLight);
        buf.writeVarInt(minBlockLight);
        buf.writeVarInt(maxBlockLight);
        buf.writeBoolean(canSeeSky);
    }

    /**
     * Serializes the condition to NBT.
     *
     * @return NBT tag with light ranges and sky-visibility flag
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putInt("minSkyLight", minSkyLight);
        tag.putInt("maxSkyLight", maxSkyLight);
        tag.putInt("minBlockLight", minBlockLight);
        tag.putInt("maxBlockLight", maxBlockLight);
        tag.putBoolean("canSeeSky", canSeeSky);
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
        minSkyLight = tag.getInt("minSkyLight");
        maxSkyLight = tag.getInt("maxSkyLight");
        minBlockLight = tag.getInt("minBlockLight");
        maxBlockLight = tag.getInt("maxBlockLight");
        canSeeSky = tag.getBoolean("canSeeSky");
        return this;
    }

}
