package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget.WidgetSlotItemTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nonnull;

@Mixin(WidgetSlotItemTransfer.class)
public abstract class WidgetSlotItemTransferMixin {
    @Shadow(remap = false)
    private IItemTransfer itemHandler;

    @Shadow(remap = false)
    private int index;

    @Shadow(remap = false)
    public abstract ItemStack getItem();

    /**
     * @author
     * @reason Block vanilla swap-to-cursor behavior when this widget slot currently holds an oversized stack.
     */
    @Overwrite(remap = false)
    public boolean mayPlace(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemStack current = getItem();
        if (!current.isEmpty() && current.getCount() > current.getMaxStackSize()) {
            return false;
        }

        return this.itemHandler.isItemValid(this.index, stack);
    }

    /**
     * Disallow vanilla swap-style modification when the slot currently contains an oversized stack.
     */
    public boolean allowModification(Player player) {
        ItemStack current = getItem();
        if (!current.isEmpty() && current.getCount() > current.getMaxStackSize()) {
            return false;
        }
        Slot self = (Slot) (Object) this;
        return self.mayPickup(player) && self.mayPlace(current);
    }

    /**
     * @author
     * @reason Limit player pickup to the item's native max stack size while keeping oversized storage intact.
     */
    @Overwrite(remap = false)
    @Nonnull
    public ItemStack remove(int amount) {
        ItemStack current = getItem();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int cappedAmount = Math.min(amount, current.getMaxStackSize());
        if (cappedAmount <= 0) {
            return ItemStack.EMPTY;
        }

        return this.itemHandler.extractItem(index, cappedAmount, false);
    }

    /**
     * @author
     * @reason Make vanilla container interaction see the native item stack size instead of oversized slot limits.
     */
    @Overwrite(remap = false)
    public int getMaxStackSize() {
        ItemStack current = getItem();
        return current.isEmpty() ? 64 : current.getMaxStackSize();
    }

    /**
     * @author
     * @reason Prevent cursor insertion and drag-splitting from exceeding the item's native max stack size.
     */
    @Overwrite(remap = false)
    public int getMaxStackSize(@Nonnull ItemStack stack) {
        return stack.getMaxStackSize();
    }
}
