package com.lowdragmc.mbd2.integration.jei;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.api.registry.MultiblockXEIRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * JEI plugin entry point for MBD recipe categories, recipes, and catalysts.
 */
@JeiPlugin
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MBDJEIPlugin implements IModPlugin {
    private final Runnable xeiChangeListener = this::refreshMultiblockRecipes;
    private IJeiRuntime jeiRuntime;
    private List<MultiblockInfoCategory.MultiblockInfoWrapper> registeredMultiblockRecipes = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return MBD2.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        MBD2.LOGGER.info("JEI register categories");
        var jeiHelpers = registry.getJeiHelpers();
        registry.addRecipeCategories(new MultiblockInfoCategory(jeiHelpers));
        for (var recipeType : MBDRegistries.RECIPE_TYPES) {
            if (recipeType.isXEIVisible()) {
                registry.addRecipeCategories(new MBDRecipeTypeCategory(jeiHelpers, recipeType));
                if (recipeType.isRequireFuelForWorking()) {
                    registry.addRecipeCategories(new MBDRecipeTypeFuelCategory(jeiHelpers, recipeType));
                }
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        MBD2.LOGGER.info("JEI register catalysts");
        MultiblockInfoCategory.registerRecipeCatalysts(registration);
        MBDRecipeTypeCategory.registerRecipeCatalysts(registration);
        MBDRecipeTypeFuelCategory.registerRecipeCatalysts(registration);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        MBD2.LOGGER.info("JEI register recipes");
        registeredMultiblockRecipes = MultiblockInfoCategory.registerRecipes(registration);
        MBDRecipeTypeCategory.registerRecipes(registration);
        MBDRecipeTypeFuelCategory.registerRecipes(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (LDLib.isReiLoaded() || LDLib.isEmiLoaded()) return;
        jeiRuntime = runtime;
        MultiblockXEIRegistry.addChangeListener(xeiChangeListener);
        // A server can finish its login sync before JEI exposes the runtime.
        // Reconcile the initial recipe snapshot with the current registry.
        refreshMultiblockRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        MultiblockXEIRegistry.removeChangeListener(xeiChangeListener);
        jeiRuntime = null;
        registeredMultiblockRecipes = List.of();
    }

    private void refreshMultiblockRecipes() {
        if (jeiRuntime == null || LDLib.isReiLoaded() || LDLib.isEmiLoaded()) {
            return;
        }
        if (!registeredMultiblockRecipes.isEmpty()) {
            jeiRuntime.getRecipeManager().hideRecipes(
                    MultiblockInfoCategory.RECIPE_TYPE, registeredMultiblockRecipes);
        }
        registeredMultiblockRecipes = MultiblockXEIRegistry.entries().stream()
                .map(MultiblockInfoCategory.MultiblockInfoWrapper::new)
                .toList();
        jeiRuntime.getRecipeManager().addRecipes(
                MultiblockInfoCategory.RECIPE_TYPE, registeredMultiblockRecipes);
    }
}
