package com.non_coffee.mbd2thread.network;

import com.non_coffee.mbd2thread.Mbd2Thread;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Mbd2ThreadNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Mbd2Thread.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private Mbd2ThreadNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.messageBuilder(C2SSetGadgetModePacket.class, id++)
                .encoder(C2SSetGadgetModePacket::encode)
                .decoder(C2SSetGadgetModePacket::decode)
                .consumerMainThread(C2SSetGadgetModePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SSetBuilderBuildModePacket.class, id++)
                .encoder(C2SSetBuilderBuildModePacket::encode)
                .decoder(C2SSetBuilderBuildModePacket::decode)
                .consumerMainThread(C2SSetBuilderBuildModePacket::handle)
                .add();
    }
}
