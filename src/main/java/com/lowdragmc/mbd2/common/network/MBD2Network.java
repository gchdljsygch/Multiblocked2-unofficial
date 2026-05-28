package com.lowdragmc.mbd2.common.network;

import com.lowdragmc.lowdraglib.networking.INetworking;
import com.lowdragmc.lowdraglib.networking.forge.LDLNetworkingImpl;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderBuildModePacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetGadgetModePacket;
import com.lowdragmc.mbd2.common.network.packets.SPatternErrorPosPacket;

public class MBD2Network {
    public static final INetworking NETWORK = LDLNetworkingImpl.createNetworking(MBD2.id("network"), "0.0.1");

    public static void init() {
        NETWORK.registerS2C(SPatternErrorPosPacket.class);
        NETWORK.registerC2S(C2SSetGadgetModePacket.class);
        NETWORK.registerC2S(C2SSetBuilderBuildModePacket.class);
    }
}
