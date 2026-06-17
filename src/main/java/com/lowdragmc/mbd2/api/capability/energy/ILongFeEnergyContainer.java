package com.lowdragmc.mbd2.api.capability.energy;

import net.minecraft.core.Direction;

/**
 * Forge Energy style container that uses {@code long} amounts instead of Forge's {@code int} range.
 *
 * <p>The business goal is to let machines store and transfer FE values above {@link Integer#MAX_VALUE} while keeping
 * Forge-compatible receive/extract semantics. Implementations should clamp stored energy to {@code 0..capacity},
 * respect the per-tick transfer limits, and treat {@code simulate=true} as a read-only capacity check. Calls normally
 * happen on the logical server thread; implementations that are shared across threads must provide their own
 * synchronization.</p>
 */
public interface ILongFeEnergyContainer {
    /**
     * Returns the currently stored FE.
     *
     * @return stored energy, expected in {@code 0..getEnergyCapacity()}
     */
    long getEnergyStored();

    /**
     * Returns the maximum stored FE.
     *
     * @return non-negative capacity
     */
    long getEnergyCapacity();

    /**
     * Sets the stored FE amount.
     *
     * <p>Implementations should clamp to capacity and fire content-change notifications only when the committed value
     * changes.</p>
     *
     * @param energyStored requested stored energy
     */
    void setEnergyStored(long energyStored);

    /**
     * Adds or removes FE from the container.
     *
     * @param energyToAdd positive to add energy, negative to remove energy
     * @return actual signed amount applied
     */
    long changeEnergy(long energyToAdd);

    /**
     * Returns the maximum FE accepted by one receive operation/tick.
     *
     * @return non-negative receive limit
     */
    long getMaxReceivePerTick();

    /**
     * Returns the maximum FE extracted by one extract operation/tick.
     *
     * @return non-negative extract limit
     */
    long getMaxExtractPerTick();

    /**
     * Checks whether energy can be received from a side.
     *
     * @param side transfer side, or {@code null} for side-independent access
     * @return {@code true} when receive operations are allowed from that side
     */
    boolean canReceive(Direction side);

    /**
     * Checks whether energy can be extracted from a side.
     *
     * @param side transfer side, or {@code null} for side-independent access
     * @return {@code true} when extract operations are allowed from that side
     */
    boolean canExtract(Direction side);

    /**
     * Receives FE into the container.
     *
     * @param side     transfer side, or {@code null} for side-independent access
     * @param amount   requested FE amount; non-positive values should return {@code 0}
     * @param simulate {@code true} to calculate acceptance without mutating state
     * @return accepted FE amount
     */
    long receiveEnergy(Direction side, long amount, boolean simulate);

    /**
     * Extracts FE from the container.
     *
     * @param side     transfer side, or {@code null} for side-independent access
     * @param amount   requested FE amount; non-positive values should return {@code 0}
     * @param simulate {@code true} to calculate extraction without mutating state
     * @return extracted FE amount
     */
    long extractEnergy(Direction side, long amount, boolean simulate);
}

