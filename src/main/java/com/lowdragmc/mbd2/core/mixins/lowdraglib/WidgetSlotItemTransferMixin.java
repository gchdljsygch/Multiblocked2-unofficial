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

/**
 * Makes LDLib widget item-transfer slots expose oversized storage safely to vanilla menus.
 *
 * <p>The backing handler may contain stacks above native limits, but vanilla cursor and drag
 * logic must still see normal item stack sizes. These overwrites prevent direct placement into
 * oversized slots and cap removals at the item's native max stack size.</p>
 */
@Mixin(WidgetSlotItemTransfer.class)
public abstract class WidgetSlotItemTransferMixin {
    @Shadow(remap = false)
    private IItemTransfer itemHandler;

    @Shadow(remap = false)
    private int index;

    @Shadow
    public abstract ItemStack getItem();

    /**
     * Rejects cursor placement while this slot currently contains an oversized stack.
     *
     * @param stack stack being placed by vanilla menu logic
     * @return whether the backing handler accepts the stack and the slot is safe to modify
     * @author pingsu
     * @reason Block vanilla swap-to-cursor behavior when this widget slot currently holds an oversized stack.
     */
    @Overwrite
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
     *
     * @param player interacting player
     * @return whether vanilla may modify this slot through normal slot logic
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
     * Removes at most one native stack from the backing handler.
     *
     * @param amount requested removal count
     * @return extracted stack capped to the current item's native max stack size
     * @author pingsu
     * @reason Limit player pickup to the item's native max stack size while keeping oversized storage intact.
     */
    @Overwrite
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
     * Reports a native stack limit to vanilla container code.
     *
     * @return native max stack size of the current item, or 64 when empty
     * @author pingsu
     * @reason Make vanilla container interaction see the native item stack size instead of oversized slot limits.
     */
    @Overwrite
    public int getMaxStackSize() {
        ItemStack current = getItem();
        return current.isEmpty() ? 64 : current.getMaxStackSize();
    }

    /**
     * Reports the candidate stack's native limit for insertion and drag-splitting.
     *
     * @param stack candidate insertion stack
     * @return native max stack size for the candidate stack
     * @author pingsu
     * @reason Prevent cursor insertion and drag-splitting from exceeding the item's native max stack size.
     */
    @Overwrite
    public int getMaxStackSize(@Nonnull ItemStack stack) {
        return stack.getMaxStackSize();
    }
}
