package com.non_coffee.mbd2thread.network;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.non_coffee.mbd2thread.util.BuilderMaterialBindings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SSetBuilderBuildModePacket(int handOrdinal, boolean slowBuild) {
    public static void encode(C2SSetBuilderBuildModePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.handOrdinal);
        buf.writeBoolean(msg.slowBuild);
    }

    public static C2SSetBuilderBuildModePacket decode(FriendlyByteBuf buf) {
        return new C2SSetBuilderBuildModePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(C2SSetBuilderBuildModePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        ctx.enqueueWork(() -> {
            InteractionHand hand = msg.handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof MBDGadgetsItem)) return;
            if (!BuilderMaterialBindings.isBuilder(stack)) return;

            BuilderMaterialBindings.setSlowBuild(stack, msg.slowBuild);
            player.getInventory().setChanged();
        });
        ctx.setPacketHandled(true);
    }
}
