package com.lowdragmc.mbd2.common.capability.recipe;

import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.capability.recipe.LongFeRecipeCapability;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adapts an integer Forge Energy recipe handler to the long-FE recipe capability.
 *
 * <p>The business goal is backward compatibility: machines that expose only the regular FE capability can still
 * satisfy long-FE recipes whose requested amounts fit in {@link Integer#MAX_VALUE}. Requests above that limit are
 * returned unhandled instead of truncating. Simulation and commit semantics are delegated to the wrapped handler.</p>
 */
public class LongFeRecipeCapabilityFallbackHandler implements IRecipeHandler<Long> {
    private final IRecipeHandler<Integer> delegate;

    /**
     * Creates a long-FE fallback around a Forge Energy handler.
     *
     * @param delegate integer FE handler to invoke for compatible amounts
     */
    public LongFeRecipeCapabilityFallbackHandler(IRecipeHandler<Integer> delegate) {
        this.delegate = delegate;
    }

    /**
     * Handles long-FE content without a recipe-group override.
     *
     * @param io       requested recipe IO
     * @param recipe   recipe being matched or executed
     * @param left     remaining long-FE amounts
     * @param slotName optional slot name
     * @param simulate {@code true} to avoid committing mutations
     * @return remaining long-FE amounts, or {@code null} on success
     */
    @Override
    public List<Long> handleRecipeInner(IO io, MBDRecipe recipe, List<Long> left, @Nullable String slotName, boolean simulate) {
        return handleRecipeInner(io, recipe, left, slotName, simulate, null);
    }

    /**
     * Copies generic content to long values before invoking the fallback.
     *
     * @param io          requested recipe IO
     * @param recipe      recipe being matched or executed
     * @param left        generic remaining content
     * @param slotName    optional slot name
     * @param simulate    {@code true} to avoid committing mutations
     * @param recipeGroup optional recipe group forwarded to the delegate
     * @return remaining long-FE amounts, or {@code null} on success
     */
    @Override
    public List<Long> handleRecipe(IO io, MBDRecipe recipe, List<?> left, @Nullable String slotName, boolean simulate, @Nullable String recipeGroup) {
        var copied = new ArrayList<Long>(left.size());
        for (var content : left) {
            copied.add(copyContent(content));
        }
        return handleRecipeInner(io, recipe, copied, slotName, simulate, recipeGroup);
    }

    private List<Long> handleRecipeInner(IO io, MBDRecipe recipe, List<Long> left, @Nullable String slotName, boolean simulate, @Nullable String recipeGroup) {
        if (left == null || left.isEmpty()) return null;
        for (Long v : left) {
            if (v != null && v > Integer.MAX_VALUE) {
                return left;
            }
        }
        List<Integer> ints = left.stream().map(v -> v == null ? 0 : v.intValue()).toList();
        List<Integer> intLeft = delegate.handleRecipe(io, recipe, ints, slotName, simulate, recipeGroup);
        if (intLeft == null) return null;
        return intLeft.stream().map(v -> v == null ? 0L : v.longValue()).toList();
    }

    /**
     * Returns the wrapped handler's slot names.
     *
     * @return slot names accepted by the delegate
     */
    @Override
    public Set<String> getSlotNames() {
        return delegate.getSlotNames();
    }

    /**
     * Returns the wrapped handler's distinct matching flag.
     *
     * @return delegate distinct flag
     */
    @Override
    public boolean isDistinct() {
        return delegate.isDistinct();
    }

    /**
     * Returns the wrapped handler's primary recipe group.
     *
     * @return recipe group id
     */
    @Override
    public String getRecipeGroup() {
        return delegate.getRecipeGroup();
    }

    /**
     * Returns all recipe groups accepted by the wrapped handler.
     *
     * @return accepted group ids
     */
    @Override
    public Set<String> getRecipeGroups() {
        return delegate.getRecipeGroups();
    }

    /**
     * Exposes this adapter as a long-FE handler.
     *
     * @return long-FE recipe capability
     */
    @Override
    public RecipeCapability<Long> getRecipeCapability() {
        return LongFeRecipeCapability.CAP;
    }

    /**
     * Delegates recipe-start hook to the wrapped handler.
     *
     * @param holder capability holder whose recipe started
     * @param io     handler side used by the recipe
     * @param recipe active recipe
     */
    @Override
    public void preWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        delegate.preWorking(holder, io, recipe);
    }

    /**
     * Delegates recipe-stop hook to the wrapped handler.
     *
     * @param holder capability holder whose recipe stopped
     * @param io     handler side used by the recipe
     * @param recipe active recipe
     */
    @Override
    public void postWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        delegate.postWorking(holder, io, recipe);
    }
}

