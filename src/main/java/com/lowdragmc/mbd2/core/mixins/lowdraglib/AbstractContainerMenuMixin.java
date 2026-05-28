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

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    public abstract Slot getSlot(int index);

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract ItemStack quickMoveStack(Player player, int index);

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

    private boolean mbd2$isWidgetTransferSlot(Slot slot) {
        return slot.getClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.SlotWidget$WidgetSlotItemTransfer");
    }
}
