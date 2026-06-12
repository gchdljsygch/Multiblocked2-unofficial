package com.lowdragmc.mbd2.integration.ae2.trait;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.core.mixins.ae2.InterfaceLogicAccessor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

@Getter
@Setter
public class SerializableInterfaceLogic extends InterfaceLogic implements ITagSerializable<CompoundTag>, IContentChangeAware {
    private static final long MAX_INTERFACE_AMOUNT = Integer.MAX_VALUE;
    private static final long MAX_FLUID_AMOUNT = Long.MAX_VALUE;

    private Runnable onContentsChanged = () -> {};

    public SerializableInterfaceLogic(IManagedGridNode gridNode, InterfaceLogicHost host, Item is, int slots) {
        super(gridNode, host, is, slots);
        replaceInterfaceInventories(slots);
    }

    private void replaceInterfaceInventories(int slots) {
        var accessor = (InterfaceLogicAccessor) this;
        var config = ConfigInventory.configStacks(null, slots, accessor::mbd2$onConfigRowChanged, true);
        useExtendedCapacities(config);
        accessor.mbd2$setConfig(config);

        var storage = ConfigInventory.configStacks(null, slots, accessor::mbd2$onStorageChanged, true);
        useExtendedCapacities(storage);
        accessor.mbd2$setStorage(storage);
    }

    private static void useExtendedCapacities(ConfigInventory inventory) {
        inventory.useRegisteredCapacities();
        AEKeyTypes.getAll().forEach(keyType -> inventory.setCapacity(keyType, MAX_INTERFACE_AMOUNT));
        inventory.setCapacity(AEKeyType.fluids(), MAX_FLUID_AMOUNT);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        writeToNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        readFromNBT(nbt);
    }
}
