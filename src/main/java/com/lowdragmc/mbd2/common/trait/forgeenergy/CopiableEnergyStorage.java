package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.Tag;
import net.minecraftforge.energy.EnergyStorage;

/**
 * Forge {@link EnergyStorage} with NBT serialization, content-change callbacks, and cheap copying.
 *
 * <p>The storage is used by recipe simulation paths that need a copy of current energy state. Mutation callbacks are
 * fired when receive/extract changes the stored amount; callers should install lightweight server-thread callbacks.
 * This class follows Forge's {@code int} FE limits.</p>
 */
public class CopiableEnergyStorage extends EnergyStorage implements ITagSerializable<Tag>, IContentChangeAware {
    @Getter
    @Setter
    public Runnable onContentsChanged = () -> {
    };

    /**
     * Creates an empty storage with equal capacity, receive, and extract limits.
     *
     * @param capacity maximum FE in Forge's {@code int} range
     */
    public CopiableEnergyStorage(int capacity) {
        super(capacity);
    }

    /**
     * Creates a storage initialized with energy and equal transfer limits to capacity.
     *
     * @param capacity maximum FE in Forge's {@code int} range
     * @param energy   initial stored FE, clamped by Forge's base storage
     */
    public CopiableEnergyStorage(int capacity, int energy) {
        super(capacity, capacity, capacity, energy);
    }

    /**
     * Receives energy and notifies listeners when the stored amount changes.
     *
     * @param maxReceive requested FE amount
     * @param simulate   {@code true} to check acceptance without mutation
     * @return accepted FE amount
     */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        var received = super.receiveEnergy(maxReceive, simulate);
        if (received > 0) onContentsChanged.run();
        return received;
    }

    /**
     * Extracts energy and notifies listeners when the stored amount changes.
     *
     * @param maxExtract requested FE amount
     * @param simulate   {@code true} to check extraction without mutation
     * @return extracted FE amount
     */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        var extracted = super.extractEnergy(maxExtract, simulate);
        if (extracted > 0) onContentsChanged.run();
        return extracted;
    }

    /**
     * Creates an independent storage with the same capacity and current energy.
     *
     * @return copy without listener state from this instance
     */
    public CopiableEnergyStorage copy() {
        return new CopiableEnergyStorage(capacity, energy);
    }

    /**
     * Restores stored energy from Forge's serialized storage tag.
     *
     * @param tag serialized energy tag
     */
    @Override
    public void deserializeNBT(Tag tag) {
        super.deserializeNBT(tag);
    }

    /**
     * Serializes stored energy using Forge's storage format.
     *
     * @return serialized energy tag
     */
    @Override
    public Tag serializeNBT() {
        return super.serializeNBT();
    }
}
