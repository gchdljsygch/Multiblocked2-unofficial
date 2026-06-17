package com.lowdragmc.mbd2.core.mixins.ae2;

import appeng.helpers.MultiCraftingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes AE2's per-slot crafting-busy check to the long-count interface update path.
 *
 * <p>The check remains owned by {@link MultiCraftingTracker}; MBD only needs to avoid issuing
 * duplicate extraction or crafting requests while AE2 is already tracking work for a slot.</p>
 */
@Mixin(MultiCraftingTracker.class)
public interface MultiCraftingTrackerAccessor {
    /**
     * Returns whether AE2 has an active crafting request for an interface slot.
     *
     * @param slot interface slot index
     * @return {@code true} when the slot is already waiting on crafting work
     */
    @Invoker(value = "isBusy", remap = false)
    boolean mbd2$isBusy(int slot);
}
