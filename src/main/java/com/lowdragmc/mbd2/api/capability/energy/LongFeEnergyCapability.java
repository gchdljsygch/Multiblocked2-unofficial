package com.lowdragmc.mbd2.api.capability.energy;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class LongFeEnergyCapability {
    private LongFeEnergyCapability() {
    }

    public static final Capability<ILongFeEnergyContainer> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });
}

