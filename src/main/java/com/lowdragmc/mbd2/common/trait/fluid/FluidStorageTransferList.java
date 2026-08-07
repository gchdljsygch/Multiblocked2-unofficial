package com.lowdragmc.mbd2.common.trait.fluid;

import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

/**
 * Machine fluid transfer list that preserves the tank trait's duplicate-fluid policy.
 */
public class FluidStorageTransferList extends FluidTransferList {

    private final FluidStorage[] storages;
    private final boolean allowSameFluids;

    public FluidStorageTransferList(FluidStorage[] storages, boolean allowSameFluids) {
        super(storages);
        this.storages = storages;
        this.allowSameFluids = allowSameFluids;
    }

    @Override
    public long fill(FluidStack resource, boolean simulate, boolean notifyChanges) {
        if (allowSameFluids) {
            return super.fill(resource, simulate, notifyChanges);
        }
        return FluidHandlerWrapper.fillStorages(storages, false, resource, simulate, notifyChanges);
    }
}
