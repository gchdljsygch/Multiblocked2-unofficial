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

/**
 * Client-to-server request to toggle builder slow-build mode.
 *
 * <p>The server accepts the request only when the chosen hand still contains an {@link MBDGadgetsItem} configured as a
 * multiblock builder. This prevents a stale client UI from changing arbitrary stacks.</p>
 */
@NoArgsConstructor
public class C2SSetBuilderBuildModePacket implements IPacket {
    private int handOrdinal;
    private boolean slowBuild;

    /**
     * Creates a builder build-mode request.
     *
     * @param handOrdinal {@code 1} for off hand, any other value for main hand
     * @param slowBuild   {@code true} to place blocks over scheduled ticks, {@code false} for immediate placement
     */
    public C2SSetBuilderBuildModePacket(int handOrdinal, boolean slowBuild) {
        this.handOrdinal = handOrdinal;
        this.slowBuild = slowBuild;
    }

    /**
     * Writes the hand and slow-build flag to the network buffer.
     *
     * @param buf destination packet buffer
     */
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeBoolean(slowBuild);
    }

    /**
     * Reads the hand and slow-build flag from the network buffer.
     *
     * @param buf source packet buffer
     */
    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        slowBuild = buf.readBoolean();
    }

    /**
     * Stores the slow-build flag on the sender's held builder stack.
     *
     * <p>Side effects on the logical server: mutates builder stack NBT and marks the inventory changed. Missing players,
     * non-gadget stacks, and non-builder gadget modes are ignored.</p>
     *
     * @param handler LowDragLib packet context
     */
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
