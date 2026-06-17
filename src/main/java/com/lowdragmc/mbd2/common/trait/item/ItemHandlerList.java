package com.lowdragmc.mbd2.common.trait.item;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Concatenated view over multiple Forge item handlers.
 *
 * <p>The business goal is to expose several trait item storages as one logical
 * capability. Slot indexes are flattened in handler order. The record keeps the
 * supplied handler array by reference; callers must not mutate it concurrently
 * with capability access.</p>
 *
 * @param handlers handlers to expose in order
 */
public record ItemHandlerList(IItemHandler[] handlers) implements IItemHandlerModifiable {

    /**
     * Returns the total number of flattened slots.
     *
     * @return sum of all child handler slot counts
     */
    @Override
    public int getSlots() {
        return Arrays.stream(handlers).mapToInt(IItemHandler::getSlots).sum();
    }

    /**
     * Returns a stack from the flattened slot index.
     *
     * @param slot zero-based flattened slot index
     * @return child handler stack, or {@link ItemStack#EMPTY} when out of range
     */
    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                return handler.getStackInSlot(slot - index);
            }
            index += handler.getSlots();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Replaces a stack in a flattened slot when the child handler is modifiable.
     *
     * <p>Side effects: mutates the selected child handler. Non-modifiable or
     * out-of-range slots are ignored.</p>
     *
     * @param slot  zero-based flattened slot index
     * @param stack stack to store
     */
    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(slot - index, stack);
                }
                return;
            }
            index += handler.getSlots();
        }
    }

    /**
     * Inserts into a flattened slot.
     *
     * @param slot     zero-based flattened slot index
     * @param stack    stack to insert
     * @param simulate {@code true} to calculate without mutating the child
     *                 handler
     * @return uninserted remainder, or the original stack when out of range
     */
    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                return handler.insertItem(slot - index, stack, simulate);
            }
            index += handler.getSlots();
        }
        return stack;
    }

    /**
     * Extracts from a flattened slot.
     *
     * @param slot     zero-based flattened slot index
     * @param amount   maximum item count to extract
     * @param simulate {@code true} to calculate without mutating the child
     *                 handler
     * @return extracted stack, or {@link ItemStack#EMPTY} when out of range
     */
    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                return handler.extractItem(slot - index, amount, simulate);
            }
            index += handler.getSlots();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the limit for a flattened slot.
     *
     * @param slot zero-based flattened slot index
     * @return child slot limit, or {@code 0} when out of range
     */
    @Override
    public int getSlotLimit(int slot) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                return handler.getSlotLimit(slot - index);
            }
            index += handler.getSlots();
        }
        return 0;
    }

    /**
     * Checks whether a stack is valid for a flattened slot.
     *
     * @param slot  zero-based flattened slot index
     * @param stack stack to test
     * @return child handler validity result, or {@code false} when out of range
     */
    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int index = 0;
        for (var handler : handlers) {
            if (slot - index < handler.getSlots()) {
                return handler.isItemValid(slot - index, stack);
            }
            index += handler.getSlots();
        }
        return false;
    }
}
