package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import lombok.NoArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Client-to-server request to switch an {@link MBDGadgetsItem} mode.
 *
 * <p>The packet is sent from the gadget mode wheel. The server rechecks that the selected hand still contains the
 * gadget and clamps accepted modes to the known damage-value range {@code 0..2}; invalid requests are ignored without
 * disconnecting the client.</p>
 */
@NoArgsConstructor
public class C2SSetGadgetModePacket implements IPacket {
    private int handOrdinal;
    private int modeDamageValue;

    /**
     * Creates a mode-change request.
     *
     * @param handOrdinal     {@code 1} for off hand, any other value for main hand
     * @param modeDamageValue gadget damage value; valid values are {@code 0} builder, {@code 1} recipe debugger, and
     *                        {@code 2} multiblock debugger
     */
    public C2SSetGadgetModePacket(int handOrdinal, int modeDamageValue) {
        this.handOrdinal = handOrdinal;
        this.modeDamageValue = modeDamageValue;
    }

    /**
     * Writes the requested hand and mode to the network buffer.
     *
     * @param buf destination packet buffer
     */
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handOrdinal);
        buf.writeVarInt(modeDamageValue);
    }

    /**
     * Reads the requested hand and mode from the network buffer.
     *
     * @param buf source packet buffer
     */
    @Override
    public void decode(FriendlyByteBuf buf) {
        handOrdinal = buf.readVarInt();
        modeDamageValue = buf.readVarInt();
    }

    /**
     * Applies the mode change to the sender's currently held gadget.
     *
     * <p>Side effects on the logical server: mutates the held stack damage value and marks the inventory changed. The
     * method performs all trust checks against live server state and ignores missing players, non-gadget stacks, and
     * out-of-range modes.</p>
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

        if (modeDamageValue < 0 || modeDamageValue > 2) return;
        stack.setDamageValue(modeDamageValue);
        player.getInventory().setChanged();
    }
}
