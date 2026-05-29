package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import lombok.NoArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@NoArgsConstructor
public class C2SSetBuilderPatternPacket implements IPacket {
    private int handOrdinal;
    private int patternIndex;

    public C2SSetBuilderPatternPacket(int handOrdinal, int patternIndex) {
        this.handOrdinal = handOrdinal;
        this.patternIndex = patternIndex;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeVarInt(patternIndex);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        patternIndex = buf.readVarInt();
    }

    @Override
    public void execute(IHandlerContext handler) {
        ServerPlayer player = handler.getPlayer();
        if (player == null) return;

        InteractionHand hand = handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MBDGadgetsItem)) return;
        if (!BuilderMaterialBindings.isBuilder(stack)) return;

        BuilderMaterialBindings.setPatternIndex(stack, patternIndex);
        player.getInventory().setChanged();
    }
}
