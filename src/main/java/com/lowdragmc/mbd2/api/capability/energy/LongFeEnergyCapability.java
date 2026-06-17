package com.lowdragmc.mbd2.api.capability.energy;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Holder for MBD's long-range Forge Energy capability token.
 *
 * <p>Use {@link #CAPABILITY} when exposing or querying {@link ILongFeEnergyContainer} from block entities, parts, or
 * machine traits. Registration is performed through {@code MBDCapabilities}; this class only provides the stable
 * Forge capability handle.</p>
 */
public final class LongFeEnergyCapability {
    private LongFeEnergyCapability() {
    }

    /**
     * Forge capability for {@link ILongFeEnergyContainer}.
     */
    public static final Capability<ILongFeEnergyContainer> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });
}

