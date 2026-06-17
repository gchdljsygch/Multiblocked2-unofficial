package com.lowdragmc.mbd2.common.trait.forgeenergy;

import net.minecraftforge.energy.IEnergyStorage;

import java.util.Arrays;

/**
 * Combined view over multiple Forge energy storages.
 *
 * <p>The business goal is to expose several trait energy buffers as one logical
 * Forge capability. Receive/extract operations walk storages in order until the
 * requested amount is satisfied. The record keeps the supplied storage array by
 * reference; callers must not mutate it concurrently with capability access.</p>
 *
 * @param storages storages to expose in order
 */
public record EnergyStorageList(IEnergyStorage[] storages) implements IEnergyStorage {
    /**
     * Receives energy into child storages in order.
     *
     * @param maxReceive requested FE amount
     * @param simulate   {@code true} to calculate without mutation
     * @return total FE accepted
     */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = 0;
        for (var storage : storages) {
            received += storage.receiveEnergy(maxReceive - received, simulate);
            if (received >= maxReceive) break;
        }
        return received;
    }

    /**
     * Extracts energy from child storages in order.
     *
     * @param maxExtract requested FE amount
     * @param simulate   {@code true} to calculate without mutation
     * @return total FE extracted
     */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = 0;
        for (var storage : storages) {
            extracted += storage.extractEnergy(maxExtract - extracted, simulate);
            if (extracted >= maxExtract) break;
        }
        return extracted;
    }

    /**
     * Returns total stored FE.
     *
     * @return sum of child stored amounts
     */
    @Override
    public int getEnergyStored() {
        return Arrays.stream(storages).reduce(0, (acc, storage) -> acc + storage.getEnergyStored(), Integer::sum);
    }

    /**
     * Returns total FE capacity.
     *
     * @return sum of child capacities
     */
    @Override
    public int getMaxEnergyStored() {
        return Arrays.stream(storages).reduce(0, (acc, storage) -> acc + storage.getMaxEnergyStored(), Integer::sum);
    }

    /**
     * Returns whether any child storage can extract energy.
     *
     * @return {@code true} when at least one child allows extraction
     */
    @Override
    public boolean canExtract() {
        return Arrays.stream(storages).anyMatch(IEnergyStorage::canExtract);
    }

    /**
     * Returns whether any child storage can receive energy.
     *
     * @return {@code true} when at least one child allows receiving
     */
    @Override
    public boolean canReceive() {
        return Arrays.stream(storages).anyMatch(IEnergyStorage::canReceive);
    }
}
