package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.util.PerTickIntCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts LDLib modular-container item movement for oversized widget stacks.
 *
 * <p>MBD storage widgets may expose stacks larger than a vanilla item can hold. This mixin blocks
 * unsafe cursor swaps and limits shift-click extraction to one native stack per action, preserving
 * the backing machine storage while still letting players move usable stack-sized chunks.</p>
 */
@Mixin(ModularUIContainer.class)
public abstract class ModularUIContainerMixin extends AbstractContainerMenu {
    @Shadow(remap = false)
    @Final
    private ModularUI modularUI;

    @Shadow(remap = false)
    @Final
    private PerTickIntCounter transferredPerTick;

    @Shadow(remap = false)
    public abstract boolean attemptMergeStack(ItemStack itemStack, boolean fromContainer, boolean simulate);

    protected ModularUIContainerMixin() {
        super(null, -1);
    }

    /**
     * Prevent swapping an oversized widget slot stack directly onto the carried cursor stack.
     *
     * @param slotId      clicked slot index
     * @param dragType    vanilla drag/click subtype
     * @param clickTypeIn vanilla click type
     * @param player      player interacting with the container
     * @param ci          callback cancelled when the swap would be unsafe
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mbd2$blockOversizedCursorSwap(int slotId, int dragType, ClickType clickTypeIn, Player player, CallbackInfo ci) {
        if (slotId < 0) {
            return;
        }

        Slot slot = getSlot(slotId);
        if (mbd2$shouldBlockOversizedSwap(slot, clickTypeIn)) {
            ci.cancel();
        }
    }

    /**
     * Transfers at most one native item stack from a widget transfer slot per shift-click.
     *
     * @param player player performing the quick move
     * @param index  clicked slot index
     * @return stack moved by this action, or {@link ItemStack#EMPTY} when nothing moved
     * @author pingsu
     * @reason Limit shift-click extraction from widget transfer slots to one native stack per action.
     */
    @Overwrite
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.mayPickup(player) || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack stackToMerge = stackInSlot.copy();
        boolean fromContainer = !modularUI.getSlotMap().get(slot).isPlayerContainer;
        if (!attemptMergeStack(stackToMerge, fromContainer, true)) {
            return ItemStack.EMPTY;
        }

        int itemsMerged;
        if (stackToMerge.isEmpty() || modularUI.getSlotMap().get(slot).canMergeSlot(stackToMerge)) {
            itemsMerged = stackInSlot.getCount() - stackToMerge.getCount();
        } else {
            itemsMerged = stackInSlot.getCount();
        }

        int nativeLimit = Math.max(1, stackInSlot.getMaxStackSize());
        int itemsToExtract = Math.min(itemsMerged, nativeLimit);
        if (itemsToExtract <= 0) {
            return ItemStack.EMPTY;
        }

        transferredPerTick.increment(player.level(), itemsToExtract);
        ItemStack extractedStack = slot.safeTake(itemsToExtract, Integer.MAX_VALUE, player);
        if (extractedStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = extractedStack.copy();
        if (!attemptMergeStack(extractedStack, fromContainer, false)) {
            resultStack = ItemStack.EMPTY;
        }
        if (!extractedStack.isEmpty()) {
            player.drop(extractedStack, false, false);
            resultStack = ItemStack.EMPTY;
        }
        return resultStack;
    }

    /**
     * Checks whether a pickup click would swap an oversized slot stack onto an incompatible cursor
     * stack.
     *
     * @param slot        clicked slot
     * @param clickTypeIn vanilla click type
     * @return {@code true} when the click should be blocked
     */
    private boolean mbd2$shouldBlockOversizedSwap(Slot slot, ClickType clickTypeIn) {
        if (clickTypeIn != ClickType.PICKUP) {
            return false;
        }
        if (!mbd2$isWidgetTransferSlot(slot)) {
            return false;
        }

        ItemStack carried = getCarried();
        if (carried.isEmpty() || !slot.hasItem()) {
            return false;
        }

        ItemStack slotStack = slot.getItem();
        return slotStack.getCount() > slotStack.getMaxStackSize() && !ItemStack.isSameItemSameTags(slotStack, carried);
    }

    /**
     * Identifies LDLib widget transfer slots by implementation class name.
     *
     * @param slot slot instance to test
     * @return {@code true} for LDLib widget item-transfer slots
     */
    private boolean mbd2$isWidgetTransferSlot(Slot slot) {
        return slot.getClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.SlotWidget$WidgetSlotItemTransfer");
    }
}
