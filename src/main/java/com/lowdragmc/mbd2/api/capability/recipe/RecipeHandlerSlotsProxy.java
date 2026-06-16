package com.lowdragmc.mbd2.api.capability.recipe;

import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Decorates a recipe handler with an explicit set of accepted slot names.
 *
 * <p>The business goal is to reuse an existing handler while constraining where
 * named recipe content can be routed. All recipe handling, copying, grouping,
 * and working hooks delegate to {@link #proxy}; only
 * {@link #getSlotNames()} is replaced. This wrapper is immutable and thread-safe
 * when the wrapped handler and slot-name set are thread-safe.</p>
 *
 * @param proxy     wrapped handler
 * @param slotNames accepted slot names; empty means no named slots are exposed
 */
public record RecipeHandlerSlotsProxy<T>(IRecipeHandler<T> proxy, Set<String> slotNames) implements IRecipeHandler<T> {

    /**
     * Delegates recipe handling to the wrapped handler.
     *
     * @param io       recipe IO direction
     * @param recipe   recipe being handled
     * @param left     remaining content of this capability
     * @param slotName requested slot name
     * @param simulate {@code true} for no-commit matching
     * @return remaining unsatisfied content, or {@code null} on success
     */
    @Override
    public List<T> handleRecipeInner(IO io, MBDRecipe recipe, List<T> left, @Nullable String slotName, boolean simulate) {
        return proxy.handleRecipeInner(io, recipe, left, slotName, simulate);
    }

    /**
     * Returns the slot names supplied to this wrapper.
     *
     * @return accepted slot names
     */
    @Override
    public Set<String> getSlotNames() {
        return slotNames;
    }

    /**
     * Returns whether the wrapped handler requires distinct matching.
     *
     * @return wrapped handler's distinct flag
     */
    @Override
    public boolean isDistinct() {
        return proxy.isDistinct();
    }

    /**
     * Returns the wrapped handler's primary recipe group.
     *
     * @return recipe group id
     */
    @Override
    public String getRecipeGroup() {
        return proxy.getRecipeGroup();
    }

    /**
     * Returns all recipe groups accepted by the wrapped handler.
     *
     * @return accepted recipe group ids
     */
    @Override
    public Set<String> getRecipeGroups() {
        return proxy.getRecipeGroups();
    }

    /**
     * Returns the wrapped handler's capability.
     *
     * @return handled capability
     */
    @Override
    public RecipeCapability<T> getRecipeCapability() {
        return proxy.getRecipeCapability();
    }

    /**
     * Delegates content copying to the wrapped handler.
     *
     * @param content source content
     * @return copied content
     */
    @Override
    public T copyContent(Object content) {
        return proxy.copyContent(content);
    }

    /**
     * Delegates generic recipe handling to the wrapped handler.
     *
     * @param io       recipe IO direction
     * @param recipe   recipe being handled
     * @param left     generic remaining content
     * @param slotName requested slot name
     * @param simulate {@code true} for no-commit matching
     * @return remaining unsatisfied content, or {@code null} on success
     */
    @Override
    public List<T> handleRecipe(IO io, MBDRecipe recipe, List<?> left, @Nullable String slotName, boolean simulate) {
        return proxy.handleRecipe(io, recipe, left, slotName, simulate);
    }

    /**
     * Delegates group-aware recipe handling to the wrapped handler.
     *
     * @param io          recipe IO direction
     * @param recipe      recipe being handled
     * @param left        generic remaining content
     * @param slotName    requested slot name
     * @param simulate    {@code true} for no-commit matching
     * @param recipeGroup requested recipe group
     * @return remaining unsatisfied content, or {@code null} on success
     */
    @Override
    public List<T> handleRecipe(IO io, MBDRecipe recipe, List<?> left, @Nullable String slotName, boolean simulate, @Nullable String recipeGroup) {
        return proxy.handleRecipe(io, recipe, left, slotName, simulate, recipeGroup);
    }

    /**
     * Delegates working-state entry hook to the wrapped handler.
     *
     * @param holder capability holder whose recipe started working
     * @param io     handler side used by the recipe
     * @param recipe active recipe
     */
    @Override
    public void preWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        proxy.preWorking(holder, io, recipe);
    }

    /**
     * Delegates working-state exit hook to the wrapped handler.
     *
     * @param holder capability holder whose recipe stopped working
     * @param io     handler side used by the recipe
     * @param recipe recipe that was active
     */
    @Override
    public void postWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        proxy.postWorking(holder, io, recipe);
    }
}
