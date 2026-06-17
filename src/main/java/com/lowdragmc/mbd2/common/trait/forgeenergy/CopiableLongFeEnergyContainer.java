package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

/**
 * Long-range FE container with NBT serialization, callbacks, and copy support.
 *
 * <p>Stored energy is synchronized for direct reads and mutations. Capacity and per-tick transfer limits are clamped
 * to non-negative values at construction. The container is side-agnostic by default; side-specific gating is handled
 * by adapter wrappers.</p>
 */
public class CopiableLongFeEnergyContainer implements ILongFeEnergyContainer, ITagSerializable<Tag>, IContentChangeAware {
    private Runnable onContentsChanged = () -> {
    };

    private final long energyCapacity;
    private final long maxReceivePerTick;
    private final long maxExtractPerTick;

    private long energyStored;

    /**
     * Creates an empty long-FE container.
     *
     * @param energyCapacity    maximum stored FE, clamped to at least {@code 0}
     * @param maxReceivePerTick maximum accepted FE per receive call/tick, clamped to at least {@code 0}
     * @param maxExtractPerTick maximum extracted FE per extract call/tick, clamped to at least {@code 0}
     */
    public CopiableLongFeEnergyContainer(long energyCapacity, long maxReceivePerTick, long maxExtractPerTick) {
        this.energyCapacity = Math.max(0L, energyCapacity);
        this.maxReceivePerTick = Math.max(0L, maxReceivePerTick);
        this.maxExtractPerTick = Math.max(0L, maxExtractPerTick);
    }

    /**
     * Creates an independent container with the same limits and stored energy.
     *
     * @return copy without listener state from this instance
     */
    public CopiableLongFeEnergyContainer copy() {
        var copied = new CopiableLongFeEnergyContainer(energyCapacity, maxReceivePerTick, maxExtractPerTick);
        copied.energyStored = energyStored;
        return copied;
    }

    /**
     * Returns the current stored energy.
     *
     * @return stored FE in {@code 0..getEnergyCapacity()}
     */
    @Override
    public synchronized long getEnergyStored() {
        return energyStored;
    }

    /**
     * Returns maximum stored energy.
     *
     * @return non-negative capacity
     */
    @Override
    public long getEnergyCapacity() {
        return energyCapacity;
    }

    /**
     * Sets stored energy after clamping and notifies listeners on change.
     *
     * @param energyStored requested stored FE
     */
    @Override
    public synchronized void setEnergyStored(long energyStored) {
        long clamped = Math.max(0L, Math.min(energyCapacity, energyStored));
        if (this.energyStored == clamped) return;
        this.energyStored = clamped;
        onContentsChanged.run();
    }

    /**
     * Adds or removes energy within capacity bounds.
     *
     * @param energyToAdd positive to add, negative to remove
     * @return actual signed change applied
     */
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

    /**
     * Returns the receive limit used by {@link #receiveEnergy(Direction, long, boolean)}.
     *
     * @return non-negative FE per receive call/tick
     */
    @Override
    public long getMaxReceivePerTick() {
        return maxReceivePerTick;
    }

    /**
     * Returns the extract limit used by {@link #extractEnergy(Direction, long, boolean)}.
     *
     * @return non-negative FE per extract call/tick
     */
    @Override
    public long getMaxExtractPerTick() {
        return maxExtractPerTick;
    }

    /**
     * Reports whether this container accepts energy from a side.
     *
     * @param side side being queried, or {@code null}
     * @return always {@code true}; adapters may add side/IO restrictions
     */
    @Override
    public boolean canReceive(Direction side) {
        return true;
    }

    /**
     * Reports whether this container extracts energy to a side.
     *
     * @param side side being queried, or {@code null}
     * @return always {@code true}; adapters may add side/IO restrictions
     */
    @Override
    public boolean canExtract(Direction side) {
        return true;
    }

    /**
     * Receives energy within capacity and per-tick limits.
     *
     * @param side     transfer side, or {@code null}
     * @param amount   requested FE amount
     * @param simulate {@code true} to calculate acceptance without mutation
     * @return accepted FE amount
     */
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

    /**
     * Extracts energy within stored amount and per-tick limits.
     *
     * @param side     transfer side, or {@code null}
     * @param amount   requested FE amount
     * @param simulate {@code true} to calculate extraction without mutation
     * @return extracted FE amount
     */
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

    /**
     * Serializes stored energy as a long tag.
     *
     * @return long tag containing current stored FE
     */
    @Override
    public Tag serializeNBT() {
        return LongTag.valueOf(getEnergyStored());
    }

    /**
     * Restores stored energy from a long tag.
     *
     * @param nbt tag to read; non-long tags are ignored
     */
    @Override
    public void deserializeNBT(Tag nbt) {
        if (nbt instanceof LongTag tag) {
            setEnergyStored(tag.getAsLong());
        }
    }

    /**
     * Installs the callback fired after committed storage changes.
     *
     * @param onContentChanged callback, or {@code null} to reset to a no-op
     */
    @Override
    public void setOnContentsChanged(Runnable onContentChanged) {
        this.onContentsChanged = onContentChanged == null ? () -> {
        } : onContentChanged;
    }

    /**
     * Returns the currently installed content-change callback.
     *
     * @return non-null callback
     */
    @Override
    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }
}
