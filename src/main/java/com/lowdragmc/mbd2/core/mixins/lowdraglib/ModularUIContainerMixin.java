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
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = false)
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
     * @author
     * @reason Limit shift-click extraction from widget transfer slots to one native stack per action.
     */
    @Overwrite(remap = false)
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

    private boolean mbd2$isWidgetTransferSlot(Slot slot) {
        return slot.getClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.SlotWidget$WidgetSlotItemTransfer");
    }
}
