package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Side-filtered Forge energy storage view.
 *
 * <p>The business goal is to expose one internal energy storage through Forge
 * capabilities while enforcing configured input/output permissions and transfer
 * limits. The wrapper delegates directly to the supplied storage and inherits
 * its thread-safety; mutation should happen on the owning machine's logical
 * thread.</p>
 */
public class EnergyStorageWrapper implements IEnergyStorage {
    private final IEnergyStorage storage;
    private final IO io;
    private final int maxReceive;
    private final int maxExtract;

    /**
     * Creates a side-filtered energy storage wrapper.
     *
     * @param storage    internal storage to expose
     * @param io         capability IO permitted through this wrapper
     * @param maxReceive maximum FE accepted per call, non-negative
     * @param maxExtract maximum FE extracted per call, non-negative
     */
    public EnergyStorageWrapper(IEnergyStorage storage, IO io, int maxReceive, int maxExtract) {
        this.storage = storage;
        this.io = io;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    /**
     * Attempts to receive energy when this wrapper permits input.
     *
     * @param maxReceive requested FE amount
     * @param simulate   {@code true} to calculate without mutating storage
     * @return FE accepted, capped by this wrapper's max receive limit
     */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (io == IO.IN || io == IO.BOTH) {
            return storage.receiveEnergy(Math.min(this.maxReceive, maxReceive), simulate);
        }
        return 0;
    }

    /**
     * Attempts to extract energy when this wrapper permits output.
     *
     * @param maxExtract requested FE amount
     * @param simulate   {@code true} to calculate without mutating storage
     * @return FE extracted, capped by this wrapper's max extract limit
     */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (io == IO.OUT || io == IO.BOTH) {
            return storage.extractEnergy(Math.min(this.maxExtract, maxExtract), simulate);
        }
        return 0;
    }

    /**
     * Returns currently stored FE.
     *
     * @return delegated stored amount
     */
    @Override
    public int getEnergyStored() {
        return storage.getEnergyStored();
    }

    /**
     * Returns maximum FE capacity.
     *
     * @return delegated capacity
     */
    @Override
    public int getMaxEnergyStored() {
        return storage.getMaxEnergyStored();
    }

    /**
     * Returns whether this wrapper allows extraction.
     *
     * @return {@code true} for output or bidirectional IO
     */
    @Override
    public boolean canExtract() {
        return io == IO.OUT || io == IO.BOTH;
    }

    /**
     * Returns whether this wrapper allows receiving energy.
     *
     * @return {@code true} for input or bidirectional IO
     */
    @Override
    public boolean canReceive() {
        return io == IO.IN || io == IO.BOTH;
    }
}
