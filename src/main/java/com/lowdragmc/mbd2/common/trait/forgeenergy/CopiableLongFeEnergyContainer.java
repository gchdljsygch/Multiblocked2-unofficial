package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

public class CopiableLongFeEnergyContainer implements ILongFeEnergyContainer, ITagSerializable<Tag>, IContentChangeAware {
    private Runnable onContentsChanged = () -> {};

    private final long energyCapacity;
    private final long maxReceivePerTick;
    private final long maxExtractPerTick;

    private long energyStored;

    public CopiableLongFeEnergyContainer(long energyCapacity, long maxReceivePerTick, long maxExtractPerTick) {
        this.energyCapacity = Math.max(0L, energyCapacity);
        this.maxReceivePerTick = Math.max(0L, maxReceivePerTick);
        this.maxExtractPerTick = Math.max(0L, maxExtractPerTick);
    }

    public CopiableLongFeEnergyContainer copy() {
        var copied = new CopiableLongFeEnergyContainer(energyCapacity, maxReceivePerTick, maxExtractPerTick);
        copied.energyStored = energyStored;
        return copied;
    }

    @Override
    public synchronized long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getEnergyCapacity() {
        return energyCapacity;
    }

    @Override
    public synchronized void setEnergyStored(long energyStored) {
        long clamped = Math.max(0L, Math.min(energyCapacity, energyStored));
        if (this.energyStored == clamped) return;
        this.energyStored = clamped;
        onContentsChanged.run();
    }

    @Override
    public synchronized long changeEnergy(long energyToAdd) {
        long old = energyStored;
        long next;
        if (energyToAdd >= 0) {
            long space = energyCapacity - old;
            long add = Math.min(space, energyToAdd);
            next = old + add;
        } else {
            long remove = Math.min(old, -energyToAdd);
            next = old - remove;
        }
        setEnergyStored(next);
        return next - old;
    }

    @Override
    public long getMaxReceivePerTick() {
        return maxReceivePerTick;
    }

    @Override
    public long getMaxExtractPerTick() {
        return maxExtractPerTick;
    }

    @Override
    public boolean canReceive(Direction side) {
        return true;
    }

    @Override
    public boolean canExtract(Direction side) {
        return true;
    }

    @Override
    public synchronized long receiveEnergy(Direction side, long amount, boolean simulate) {
        if (amount <= 0L) return 0L;
        if (side != null && !canReceive(side)) return 0L;
        long limit = Math.min(amount, maxReceivePerTick);
        long space = energyCapacity - energyStored;
        long accepted = Math.min(space, limit);
        if (accepted <= 0L) return 0L;
        if (!simulate) setEnergyStored(energyStored + accepted);
        return accepted;
    }

    @Override
    public synchronized long extractEnergy(Direction side, long amount, boolean simulate) {
        if (amount <= 0L) return 0L;
        if (side != null && !canExtract(side)) return 0L;
        long limit = Math.min(amount, maxExtractPerTick);
        long extracted = Math.min(energyStored, limit);
        if (extracted <= 0L) return 0L;
        if (!simulate) setEnergyStored(energyStored - extracted);
        return extracted;
    }

    @Override
    public Tag serializeNBT() {
        return LongTag.valueOf(getEnergyStored());
    }

    @Override
    public void deserializeNBT(Tag nbt) {
        if (nbt instanceof LongTag tag) {
            setEnergyStored(tag.getAsLong());
        }
    }

    @Override
    public void setOnContentsChanged(Runnable onContentChanged) {
        this.onContentsChanged = onContentChanged == null ? () -> {} : onContentChanged;
    }

    @Override
    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }
}
