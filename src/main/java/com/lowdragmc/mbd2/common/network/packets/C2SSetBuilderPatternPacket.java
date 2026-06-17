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
 * Client-to-server request to change the multiblock builder's selected pattern.
 *
 * <p>The request is produced by the gadget UI. The server validates that the chosen hand still holds a builder gadget;
 * the stored index is normalized by {@link BuilderMaterialBindings#setPatternIndex(ItemStack, int)}.</p>
 */
@NoArgsConstructor
public class C2SSetBuilderPatternPacket implements IPacket {
    private int handOrdinal;
    private int patternIndex;

    /**
     * Creates a selected-pattern request.
     *
     * @param handOrdinal  {@code 1} for off hand, any other value for main hand
     * @param patternIndex zero-based pattern index; negative values are accepted on the wire and clamped server-side
     */
    public C2SSetBuilderPatternPacket(int handOrdinal, int patternIndex) {
        this.handOrdinal = handOrdinal;
        this.patternIndex = patternIndex;
    }

    /**
     * Writes the hand and selected pattern index to the network buffer.
     *
     * @param buf destination packet buffer
     */
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeVarInt(patternIndex);
    }

    /**
     * Reads the hand and selected pattern index from the network buffer.
     *
     * @param buf source packet buffer
     */
    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        patternIndex = buf.readVarInt();
    }

    /**
     * Stores the selected pattern on the sender's held builder stack.
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

        BuilderMaterialBindings.setPatternIndex(stack, patternIndex);
        player.getInventory().setChanged();
    }
}
