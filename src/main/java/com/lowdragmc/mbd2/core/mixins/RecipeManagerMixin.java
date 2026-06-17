package com.lowdragmc.mbd2.core.mixins;

import com.google.gson.JsonElement;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Injects MBD2 built-in recipe types after vanilla datapack recipe loading.
 *
 * <p>The mixin copies the recipe map before handing it to each registered
 * {@link MBDRecipeType}, allowing recipe types to append generated recipes without mutating
 * the original immutable map implementation returned by vanilla reload code.</p>
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;

    /**
     * Lets registered MBD recipe types append their built-in recipes after vanilla loading.
     *
     * @param map raw JSON recipe data from the reload
     * @param resourceManager resource manager used by vanilla loading
     * @param profiler reload profiler
     * @param ci mixin callback info
     */
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "TAIL"))
    private void mbd2$cloneVanillaRecipes(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        recipes = new HashMap<>(recipes);
        for (var recipeType : MBDRegistries.RECIPE_TYPES) {
            recipeType.onRecipeManagerLoaded(recipes);
        }
    }
}
