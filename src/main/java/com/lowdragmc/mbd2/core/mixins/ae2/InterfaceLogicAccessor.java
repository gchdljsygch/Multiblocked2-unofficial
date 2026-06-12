package com.lowdragmc.mbd2.core.mixins.ae2;

import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InterfaceLogic.class)
public interface InterfaceLogicAccessor {
    @Mutable
    @Accessor(value = "config", remap = false)
    void mbd2$setConfig(ConfigInventory config);

    @Mutable
    @Accessor(value = "storage", remap = false)
    void mbd2$setStorage(ConfigInventory storage);

    @Invoker(value = "onConfigRowChanged", remap = false)
    void mbd2$onConfigRowChanged();

    @Invoker(value = "onStorageChanged", remap = false)
    void mbd2$onStorageChanged();

    @Invoker(value = "updatePlan", remap = false)
    void mbd2$updatePlan(int slot);
}
