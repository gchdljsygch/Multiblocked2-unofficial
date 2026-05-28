package com.non_coffee.mbd2thread.energy.fe.api;

import net.minecraft.core.Direction;

public interface ILongFeEnergyContainer {
    long getEnergyStored();

    long getEnergyCapacity();

    void setEnergyStored(long energyStored);

    long changeEnergy(long energyToAdd);

    long getMaxReceivePerTick();

    long getMaxExtractPerTick();

    boolean canReceive(Direction side);

    boolean canExtract(Direction side);

    long receiveEnergy(Direction side, long amount, boolean simulate);

    long extractEnergy(Direction side, long amount, boolean simulate);
}

