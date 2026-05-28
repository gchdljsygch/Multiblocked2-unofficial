package com.non_coffee.mbd2thread.energy.fe.compat;

import com.google.common.collect.Table;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeHandlerSlotsProxy;
import com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability;
import com.non_coffee.mbd2thread.energy.fe.recipe.LongFeRecipeCapability;
import com.non_coffee.mbd2thread.energy.fe.trait.LongFeEnergyCapabilityTrait;

import java.util.ArrayList;
import java.util.List;

public class RecipeCapabilitiesProxyCompat {
    private RecipeCapabilitiesProxyCompat() {
    }

    public static void apply(Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy) {
        prioritizeForgeEnergy(proxy);
        addLongFeFallback(proxy);
    }

    private static void prioritizeForgeEnergy(Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy) {
        for (IO handlerIO : IO.values()) {
            if (!proxy.contains(handlerIO, ForgeEnergyRecipeCapability.CAP)) continue;
            List<IRecipeHandler<?>> handlers = proxy.get(handlerIO, ForgeEnergyRecipeCapability.CAP);
            if (handlers == null || handlers.size() <= 1) continue;
            List<IRecipeHandler<?>> preferred = new ArrayList<>();
            List<IRecipeHandler<?>> rest = new ArrayList<>();
            for (var h : handlers) {
                if (isLongFeForgeEnergyHandler(h)) {
                    preferred.add(h);
                } else {
                    rest.add(h);
                }
            }
            if (!preferred.isEmpty() && preferred.size() != handlers.size()) {
                handlers.clear();
                handlers.addAll(preferred);
                handlers.addAll(rest);
            }
        }
    }

    private static boolean isLongFeForgeEnergyHandler(IRecipeHandler<?> handler) {
        if (handler == null) return false;
        IRecipeHandler<?> raw = unwrap(handler);
        return raw.getClass().getName().equals(LongFeEnergyCapabilityTrait.ForgeEnergyRecipeHandler.class.getName());
    }

    private static void addLongFeFallback(Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy) {
        if (hasAny(proxy, LongFeRecipeCapability.CAP)) return;
        for (IO handlerIO : IO.values()) {
            if (!proxy.contains(handlerIO, ForgeEnergyRecipeCapability.CAP)) continue;
            List<IRecipeHandler<?>> handlers = proxy.get(handlerIO, ForgeEnergyRecipeCapability.CAP);
            if (handlers == null || handlers.isEmpty()) continue;
            List<IRecipeHandler<?>> wrapped = new ArrayList<>(handlers.size());
            for (var h : handlers) {
                IRecipeHandler<?> raw = unwrap(h);
                if (raw.getRecipeCapability() == ForgeEnergyRecipeCapability.CAP) {
                    @SuppressWarnings("unchecked")
                    IRecipeHandler<Integer> delegate = (IRecipeHandler<Integer>) raw;
                    wrapped.add(new LongFeRecipeCapabilityFallbackHandler(delegate));
                }
            }
            if (wrapped.isEmpty()) continue;
            proxy.put(handlerIO, LongFeRecipeCapability.CAP, wrapped);
        }
    }

    private static boolean hasAny(Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy, RecipeCapability<?> cap) {
        for (IO handlerIO : IO.values()) {
            if (proxy.contains(handlerIO, cap)) {
                List<IRecipeHandler<?>> handlers = proxy.get(handlerIO, cap);
                if (handlers != null && !handlers.isEmpty()) return true;
            }
        }
        return false;
    }

    private static IRecipeHandler<?> unwrap(IRecipeHandler<?> handler) {
        if (handler instanceof RecipeHandlerSlotsProxy<?> slotsProxy) {
            return slotsProxy.proxy();
        }
        return handler;
    }
}

