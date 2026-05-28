package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import lombok.NoArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@NoArgsConstructor
public class C2SSetGadgetModePacket implements IPacket {
    private int handOrdinal;
    private int modeDamageValue;

    public C2SSetGadgetModePacket(int handOrdinal, int modeDamageValue) {
        this.handOrdinal = handOrdinal;
        this.modeDamageValue = modeDamageValue;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeVarInt(modeDamageValue);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        modeDamageValue = buf.readVarInt();
    }

    @Override
    public void execute(IHandlerContext handler) {
        ServerPlayer player = handler.getPlayer();
        if (player == null) return;

        InteractionHand hand = handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MBDGadgetsItem)) return;

        if (modeDamageValue < 0 || modeDamageValue > 2) return;
        stack.setDamageValue(modeDamageValue);
        player.getInventory().setChanged();
    }
}
