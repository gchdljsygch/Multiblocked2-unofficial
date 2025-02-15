package com.lowdragmc.mbd2.integration.pneumaticcraft.trait.heat;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.integration.pneumaticcraft.PNCHeatRecipeCapability;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.desht.pneumaticcraft.common.core.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;

@Getter
@NoArgsConstructor
public class PNCTemperatureCondition extends RecipeCondition {
    public final static PNCTemperatureCondition INSTANCE = new PNCTemperatureCondition();
    @Configurable(name = "config.recipe.condition.temperature.min")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    private float minTemperature;
    @Configurable(name = "config.recipe.condition.temperature.max")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    private float maxTemperature;

    public PNCTemperatureCondition(float minTemperature, float maxTemperature) {
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
    }

    @Override
    public String getType() {
        return "pneumatic_temperature";
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.pneumatic_temperature.tooltip", minTemperature, maxTemperature);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(ModItems.HEAT_FRAME.get());
    }

    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var proxy = recipeLogic.machine.getRecipeCapabilitiesProxy();
        var toCheck = new ArrayList<IRecipeHandler<?>>();
        if (recipe.inputs.containsKey(PNCHeatRecipeCapability.CAP) && proxy.contains(IO.IN, PNCHeatRecipeCapability.CAP)) {
            var inputs = proxy.get(IO.IN, PNCHeatRecipeCapability.CAP);
            toCheck.addAll(inputs);
        }
        if (recipe.outputs.containsKey(PNCHeatRecipeCapability.CAP) && proxy.contains(IO.OUT, PNCHeatRecipeCapability.CAP)) {
            var outputs = proxy.get(IO.OUT, PNCHeatRecipeCapability.CAP);
            toCheck.addAll(outputs);
        }
        if (proxy.contains(IO.BOTH, PNCHeatRecipeCapability.CAP)) {
            toCheck.addAll(proxy.get(IO.BOTH, PNCHeatRecipeCapability.CAP));
        }
        for (IRecipeHandler<?> handler : toCheck) {
            if (handler instanceof PNCHeatExchangerTrait.HeatRecipeHandler heatRecipeHandler) {
                var temp = ((PNCHeatExchangerTrait)heatRecipeHandler.trait).getHandler().getTemperature() - 273;
                if (temp >= minTemperature && temp <= maxTemperature) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("minTemperature", minTemperature);
        config.addProperty("maxTemperature", maxTemperature);
        return config;
    }

    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        minTemperature = GsonHelper.getAsFloat(config, "minTemperature", 0);
        maxTemperature = GsonHelper.getAsFloat(config, "maxTemperature", 1);
        return this;
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        minTemperature = buf.readFloat();
        maxTemperature = buf.readFloat();
        return this;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeFloat(minTemperature);
        buf.writeFloat(maxTemperature);
    }

    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putFloat("minTemperature", minTemperature);
        tag.putFloat("maxTemperature", maxTemperature);
        return tag;
    }

    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        minTemperature = tag.getFloat("minTemperature");
        maxTemperature = tag.getFloat("maxTemperature");
        return this;
    }

}
