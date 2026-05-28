package com.non_coffee.mbd2thread.network;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SSetGadgetModePacket(int handOrdinal, int modeDamageValue) {
    public static void encode(C2SSetGadgetModePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.handOrdinal);
        buf.writeVarInt(msg.modeDamageValue);
    }

    public static C2SSetGadgetModePacket decode(FriendlyByteBuf buf) {
        return new C2SSetGadgetModePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SSetGadgetModePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) return;

        ctx.enqueueWork(() -> {
            InteractionHand hand = msg.handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof MBDGadgetsItem)) return;

            int mode = msg.modeDamageValue;
            if (mode < 0 || mode > 2) return;
            stack.setDamageValue(mode);
            player.getInventory().setChanged();
        });
        ctx.setPacketHandled(true);
    }
}

