package com.lowdragmc.mbd2.common.network;

import com.lowdragmc.lowdraglib.networking.INetworking;
import com.lowdragmc.lowdraglib.networking.forge.LDLNetworkingImpl;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderBuildModePacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderPatternPacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetGadgetModePacket;
import com.lowdragmc.mbd2.common.network.packets.SPatternErrorPosPacket;

/**
 * Network channel registration for MBD2 gameplay packets.
 *
 * <p>The business goal is to centralize client/server packet registration for gadget configuration and multiblock
 * debug visualization. The channel version is fixed at {@code 0.0.1}; both sides must register the same packet set
 * during mod initialization before any packet is sent.</p>
 */
public class MBD2Network {
    /**
     * LowDragLib networking channel used by all MBD2 packets.
     */
    public static final INetworking NETWORK = LDLNetworkingImpl.createNetworking(MBD2.id("network"), "0.0.1");

    /**
     * Registers all packets on the shared channel.
     *
     * <p>Side effects: mutates the LowDragLib networking registry. Call exactly once during common setup on the mod
     * initialization thread.</p>
     */
    public static void init() {
        NETWORK.registerS2C(SPatternErrorPosPacket.class);
        NETWORK.registerC2S(C2SSetGadgetModePacket.class);
        NETWORK.registerC2S(C2SSetBuilderBuildModePacket.class);
        NETWORK.registerC2S(C2SSetBuilderPatternPacket.class);
    }
}
