package com.lowdragmc.mbd2.api.capability;

import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * Central registration point for MBD Forge capabilities.
 *
 * <p>The machine capability exposes {@link IMachine} on MBD block entities, while the long-FE capability lets traits
 * and integrations transfer energy above Forge's integer FE range. Registration is called from Forge mod setup on
 * the mod event bus.</p>
 */
public class MBDCapabilities {
    /**
     * Forge capability used to resolve an {@link IMachine} from a block entity.
     */
    public static final Capability<IMachine> CAPABILITY_MACHINE = CapabilityManager.get(new CapabilityToken<>() {
    });

    /**
     * Registers MBD capability interfaces with Forge.
     *
     * @param event Forge capability registration event
     */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IMachine.class);
        event.register(ILongFeEnergyContainer.class);
    }
}
