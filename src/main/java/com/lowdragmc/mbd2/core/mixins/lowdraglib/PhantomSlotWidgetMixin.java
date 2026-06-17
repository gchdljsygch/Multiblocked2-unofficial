package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes native item-stack caps from LDLib phantom item slots.
 *
 * <p>Phantom slots are configuration values rather than real inventories, so MBD lets them hold
 * large recipe counts up to {@link Integer#MAX_VALUE}. The mixin is pseudo-targeted because LDLib
 * can move or omit this widget in some dependency sets.</p>
 */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget")
public abstract class PhantomSlotWidgetMixin {
    @Shadow
    private int maxStackSize;

    /**
     * Sets the default phantom count range to the largest count an {@link ItemStack} can carry.
     *
     * @param ci mixin callback info
     */
    @Inject(method = {"<init>()V", "<init>(Lcom/lowdragmc/lowdraglib/side/item/IItemTransfer;III)V"}, at = @At("TAIL"), remap = false)
    private void mbd2$setDefaultStackLimitToIntMax(CallbackInfo ci) {
        this.maxStackSize = Integer.MAX_VALUE;
    }

    /**
     * Adjusts an existing phantom stack without clamping to the item's native max stack size.
     *
     * @param slot        phantom slot being adjusted
     * @param mouseButton mouse button id
     * @param clickTypeIn click type; shift-click halves or doubles the amount
     * @param ci          callback cancelled because this method replaces LDLib's adjustment logic
     */
    @Inject(method = "adjustPhantomSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$adjustPhantomSlotWithoutVanillaCap(Slot slot, int mouseButton, ClickType clickTypeIn, CallbackInfo ci) {
        ItemStack stackSlot = slot.getItem();
        long currentCount = stackSlot.getCount();
        long stackSize;

        if (clickTypeIn == ClickType.QUICK_MOVE) {
            stackSize = mouseButton == 0 ? (currentCount + 1L) / 2L : currentCount * 2L;
        } else {
            stackSize = mouseButton == 0 ? currentCount - 1L : currentCount + 1L;
        }

        stackSlot.setCount(mbd2$clampPhantomCount(stackSize));
        slot.set(stackSlot);
        ci.cancel();
    }

    /**
     * Fills a phantom slot from the held stack without native stack-size clamping.
     *
     * @param slot        phantom slot being filled
     * @param stackHeld   cursor stack
     * @param mouseButton left click copies the held count, right click sets one item
     * @param ci          callback cancelled because this method replaces LDLib's fill logic
     */
    @Inject(method = "fillPhantomSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$fillPhantomSlotWithoutVanillaCap(Slot slot, ItemStack stackHeld, int mouseButton, CallbackInfo ci) {
        if (stackHeld.isEmpty()) {
            slot.set(ItemStack.EMPTY);
            ci.cancel();
            return;
        }

        long requestedCount = mouseButton == 0 ? stackHeld.getCount() : 1L;
        ItemStack phantomStack = stackHeld.copy();
        phantomStack.setCount(mbd2$clampPhantomCount(requestedCount));
        slot.set(phantomStack);
        ci.cancel();
    }

    /**
     * Clamps a requested phantom count to the configured phantom range.
     *
     * @param count requested count
     * @return count in the range {@code [0, maxStackSize]}
     */
    @Unique
    private int mbd2$clampPhantomCount(long count) {
        if (count <= 0L) {
            return 0;
        }
        long maxAllowedCount = Math.max(this.maxStackSize, 0);
        return (int) Math.min(count, maxAllowedCount);
    }
}
