package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor for the mutable recipe map inside {@link RecipeManager}.
 *
 * <p>MBD2 uses the raw map when injecting built-in machine recipes into vanilla recipe
 * manager state after datapack reload. Callers must preserve the nested
 * recipe-type-to-id-to-recipe structure expected by vanilla.</p>
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    /**
     * Returns vanilla's raw recipe table.
     */
    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> getRawRecipes();
}
