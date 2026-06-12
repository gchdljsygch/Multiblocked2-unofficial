package com.lowdragmc.mbd2.core.mixins.ae2;

import appeng.helpers.MultiCraftingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiCraftingTracker.class)
public interface MultiCraftingTrackerAccessor {
    @Invoker(value = "isBusy", remap = false)
    boolean mbd2$isBusy(int slot);
}
