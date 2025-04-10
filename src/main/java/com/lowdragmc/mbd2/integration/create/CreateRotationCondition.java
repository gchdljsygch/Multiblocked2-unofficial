package com.lowdragmc.mbd2.integration.create;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.integration.create.machine.CreateRotationTrait;
import com.simibubi.create.AllBlocks;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;

@Getter
@NoArgsConstructor
public class CreateRotationCondition extends RecipeCondition {

    public final static CreateRotationCondition INSTANCE = new CreateRotationCondition();
    @Configurable(name = "config.recipe.condition.rpm.min")
    @NumberRange(range = {0f, Float.MAX_VALUE})
    private float minRPM;
    @Configurable(name = "config.recipe.condition.rpm.max")
    @NumberRange(range = {0f, Float.MAX_VALUE})
    private float maxRPM;
    @Configurable(name = "config.recipe.condition.stress.min")
    @NumberRange(range = {0f, Float.MAX_VALUE})
    private float minStress;
    @Configurable(name = "config.recipe.condition.stress.min")
    @NumberRange(range = {0f, Float.MAX_VALUE})
    private float maxStress;

    public CreateRotationCondition(float minRPM, float maxRPM, float minStress, float maxStress) {
        this.minRPM = minRPM;
        this.maxRPM = maxRPM;
        this.minStress = minStress;
        this.maxStress = maxStress;
    }

    @Override
    public String getType() {
        return "create_rotation";
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.create_rpm.tooltip", minRPM, maxRPM, minStress, maxStress);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(AllBlocks.SHAFT.asStack());
    }

    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var proxy = recipeLogic.machine.getRecipeCapabilitiesProxy();
        var inputs = proxy.get(IO.IN, CreateStressRecipeCapability.CAP);
        if (inputs != null) {
            for (var input : inputs) {
                CreateRotationTrait trait = null;
                if (input instanceof CreateRotationTrait.RPMRecipeHandler handler) {
                    trait = handler.getTrait();
                } else if (input instanceof CreateRotationTrait.StressRecipeHandler handler) {
                    trait = handler.getTrait();
                }
                if (trait != null) {
                    var rpm = Math.abs(trait.getLastSpeed());
                    var stress = rpm * trait.getTorque();
                    if (rpm >= minRPM && rpm <= maxRPM && stress >= minStress && stress <= maxStress) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("minRPM", minRPM);
        config.addProperty("maxRPM", maxRPM);
        config.addProperty("minStress", minStress);
        config.addProperty("maxStress", maxStress);
        return config;
    }

    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        minRPM = GsonHelper.getAsFloat(config, "minRPM", 0);
        maxRPM = GsonHelper.getAsFloat(config, "maxRPM", 1);
        minStress = GsonHelper.getAsFloat(config, "minStress", 0);
        maxStress = GsonHelper.getAsFloat(config, "maxStress", 1);
        return this;
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        minRPM = buf.readFloat();
        maxRPM = buf.readFloat();
        minStress = buf.readFloat();
        maxStress = buf.readFloat();
        return this;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeFloat(minRPM);
        buf.writeFloat(maxRPM);
        buf.writeFloat(minStress);
        buf.writeFloat(maxStress);
    }

    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putFloat("minRPM", minRPM);
        tag.putFloat("maxRPM", maxRPM);
        tag.putFloat("minStress", minStress);
        tag.putFloat("maxStress", maxStress);
        return tag;
    }

    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        minRPM = tag.getFloat("minRPM");
        maxRPM = tag.getFloat("maxRPM");
        minStress = tag.getFloat("minStress");
        maxStress = tag.getFloat("maxStress");
        return this;
    }

}
