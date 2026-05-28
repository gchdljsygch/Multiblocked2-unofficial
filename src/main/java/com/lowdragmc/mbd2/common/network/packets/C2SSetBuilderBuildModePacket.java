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
public class C2SSetBuilderBuildModePacket implements IPacket {
    private int handOrdinal;
    private boolean slowBuild;

    public C2SSetBuilderBuildModePacket(int handOrdinal, boolean slowBuild) {
        this.handOrdinal = handOrdinal;
        this.slowBuild = slowBuild;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeBoolean(slowBuild);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        slowBuild = buf.readBoolean();
    }

    @Override
    public void execute(IHandlerContext handler) {
        ServerPlayer player = handler.getPlayer();
        if (player == null) return;

        InteractionHand hand = handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MBDGadgetsItem)) return;
        if (!BuilderMaterialBindings.isBuilder(stack)) return;

        BuilderMaterialBindings.setSlowBuild(stack, slowBuild);
        player.getInventory().setChanged();
    }
}
