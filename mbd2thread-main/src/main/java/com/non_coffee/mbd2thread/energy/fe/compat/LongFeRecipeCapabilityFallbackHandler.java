package com.non_coffee.mbd2thread.energy.fe.compat;

import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.non_coffee.mbd2thread.energy.fe.recipe.LongFeRecipeCapability;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class LongFeRecipeCapabilityFallbackHandler implements IRecipeHandler<Long> {
    private final IRecipeHandler<Integer> delegate;

    public LongFeRecipeCapabilityFallbackHandler(IRecipeHandler<Integer> delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Long> handleRecipeInner(IO io, MBDRecipe recipe, List<Long> left, @Nullable String slotName, boolean simulate) {
        if (left == null || left.isEmpty()) return null;
        for (Long v : left) {
            if (v != null && v > Integer.MAX_VALUE) {
                return left;
            }
        }
        List<Integer> ints = left.stream().map(v -> v == null ? 0 : v.intValue()).toList();
        List<Integer> intLeft = delegate.handleRecipeInner(io, recipe, ints, slotName, simulate);
        if (intLeft == null) return null;
        return intLeft.stream().map(v -> v == null ? 0L : v.longValue()).toList();
    }

    @Override
    public Set<String> getSlotNames() {
        return delegate.getSlotNames();
    }

    @Override
    public boolean isDistinct() {
        return delegate.isDistinct();
    }

    @Override
    public RecipeCapability<Long> getRecipeCapability() {
        return LongFeRecipeCapability.CAP;
    }

    @Override
    public void preWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        delegate.preWorking(holder, io, recipe);
    }

    @Override
    public void postWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
        delegate.postWorking(holder, io, recipe);
    }
}

