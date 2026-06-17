package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards vanilla container clicks against LDLib widget transfer slots that contain oversized
 * stacks.
 *
 * <p>Widget transfer slots can represent machine storage with counts above an item's normal
 * max stack size. Vanilla click handling assumes native stack limits and can corrupt cursor
 * swaps, so this mixin intercepts only {@link ModularUIContainer} interactions and lets normal
 * containers fall through untouched.</p>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    public abstract Slot getSlot(int index);

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract ItemStack quickMoveStack(Player player, int index);

    /**
     * Prevents unsafe pickup clicks while preserving LDLib shift-click transfer behavior.
     *
     * @param slotId      clicked slot index
     * @param dragType    vanilla drag/click subtype
     * @param clickTypeIn vanilla click type
     * @param player      player interacting with the menu
     * @param ci          callback cancelled when MBD handles or blocks the interaction
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mbd2$guardWidgetTransferInteractions(int slotId, int dragType, ClickType clickTypeIn, Player player, CallbackInfo ci) {
        if (!(((Object) this) instanceof ModularUIContainer)) {
            return;
        }
        if (slotId < 0) {
            return;
        }

        Slot slot = getSlot(slotId);
        if (!mbd2$isWidgetTransferSlot(slot)) {
            return;
        }

        if (clickTypeIn == ClickType.QUICK_MOVE) {
            quickMoveStack(player, slotId);
            ci.cancel();
            return;
        }

        if (clickTypeIn != ClickType.PICKUP) {
            return;
        }

        ItemStack carried = getCarried();
        ItemStack slotStack = slot.getItem();
        if (carried.isEmpty() || slotStack.isEmpty()) {
            return;
        }

        if (slotStack.getCount() > slotStack.getMaxStackSize()) {
            ci.cancel();
        }
    }

    /**
     * Identifies LDLib's private widget transfer slot class without linking against the nested
     * implementation type from this base-menu mixin.
     *
     * @param slot slot instance to test
     * @return {@code true} for LDLib widget item-transfer slots
     */
    private boolean mbd2$isWidgetTransferSlot(Slot slot) {
        return slot.getClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.SlotWidget$WidgetSlotItemTransfer");
    }
}
