package com.lowdragmc.mbd2.core.mixins.ae2;

import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accesses AE2 interface internals needed by MBD's serializable interface traits.
 *
 * <p>These accessors deliberately bypass {@link InterfaceLogic}'s private field and method
 * visibility. Callers must keep config and storage inventories consistent with AE2's own
 * callbacks, because the setters replace normally final references.</p>
 */
@Mixin(InterfaceLogic.class)
public interface InterfaceLogicAccessor {
    /**
     * Replaces the configuration inventory used by the interface logic.
     *
     * @param config replacement config inventory owned by an MBD trait
     */
    @Mutable
    @Accessor(value = "config", remap = false)
    void mbd2$setConfig(ConfigInventory config);

    /**
     * Replaces the storage inventory used by the interface logic.
     *
     * @param storage replacement storage inventory owned by an MBD trait
     */
    @Mutable
    @Accessor(value = "storage", remap = false)
    void mbd2$setStorage(ConfigInventory storage);

    /**
     * Invokes AE2's config-row change callback after external config mutation.
     */
    @Invoker(value = "onConfigRowChanged", remap = false)
    void mbd2$onConfigRowChanged();

    /**
     * Invokes AE2's storage change callback after external storage mutation.
     */
    @Invoker(value = "onStorageChanged", remap = false)
    void mbd2$onStorageChanged();

    /**
     * Recomputes AE2's planned work for a single interface slot.
     *
     * @param slot interface slot index to refresh
     */
    @Invoker(value = "updatePlan", remap = false)
    void mbd2$updatePlan(int slot);
}
