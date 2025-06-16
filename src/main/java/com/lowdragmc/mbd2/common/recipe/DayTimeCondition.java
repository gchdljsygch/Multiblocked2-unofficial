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

@Getter
@Setter
@NoArgsConstructor
public class DayTimeCondition extends RecipeCondition {

    public final static DayTimeCondition INSTANCE = new DayTimeCondition();
    @Configurable(name = "config.recipe.condition.day_time.is_day")
    private boolean isDay;

    public DayTimeCondition(boolean isDay) {
        this.isDay = isDay;
    }

    @Override
    public String getType() {
        return "day_time";
    }

    @Override
    public Component getTooltips() {
        return isDay ? Component.translatable("recipe.condition.day_time.tooltip.true") : Component.translatable("recipe.condition.day_time.tooltip.false");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.CLOCK);
    }

    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var level = recipeLogic.machine.getLevel();
        return level != null && level.isDay() == isDay;
    }

    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("isDay", isDay);
        return config;
    }

    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        isDay = GsonHelper.getAsBoolean(config, "isDay", false);
        return this;
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        isDay = buf.readBoolean();
        return this;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeBoolean(isDay);
    }

    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putBoolean("isDay", isDay);
        return tag;
    }

    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        isDay = tag.getBoolean("isDay");
        return this;
    }

}
