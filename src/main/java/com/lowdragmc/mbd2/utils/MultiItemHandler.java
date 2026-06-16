package com.lowdragmc.mbd2.utils;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presents several item handlers as one contiguous virtual inventory.
 *
 * <p>The business goal is to let auto-build consume blocks from every sided and
 * unsided item capability on a bound block entity through one
 * {@link IItemHandler}. The wrapper is immutable after construction, but each
 * method delegates to the underlying handlers, so thread safety and side
 * effects are exactly those of the wrapped capabilities.</p>
 */
public final class MultiItemHandler implements IItemHandler {
    private final List<IItemHandler> handlers;
    private final int[] offsets;
    private final int slots;

    /**
     * Builds a virtual handler from the non-null handlers that currently expose
     * at least one slot.
     *
     * <p>Preconditions: the list itself must be non-null. Null handlers and
     * zero-slot handlers are ignored. Side effects: reads slot counts from each
     * handler, but does not insert, extract, or retain the mutable input list.</p>
     *
     * @param handlers candidate handlers in the order they should appear in the
     *                 virtual slot range
     */
    public MultiItemHandler(List<IItemHandler> handlers) {
        Objects.requireNonNull(handlers);
        List<IItemHandler> filtered = new ArrayList<>();
        for (IItemHandler h : handlers) {
            if (h != null && h.getSlots() > 0) filtered.add(h);
        }
        this.handlers = List.copyOf(filtered);
        this.offsets = new int[this.handlers.size() + 1];
        int total = 0;
        for (int i = 0; i < this.handlers.size(); i++) {
            offsets[i] = total;
            total += this.handlers.get(i).getSlots();
        }
        offsets[this.handlers.size()] = total;
        this.slots = total;
    }

    /**
     * Indicates whether the wrapper has any virtual slots.
     *
     * @return {@code true} when construction found no usable handler slots
     */
    public boolean isEmpty() {
        return slots <= 0;
    }

    /**
     * Returns the sum of all wrapped handler slots.
     *
     * @return virtual slot count in the range {@code [0, Integer.MAX_VALUE]}
     */
    @Override
    public int getSlots() {
        return slots;
    }

    /**
     * Reads a stack from a virtual slot.
     *
     * <p>Preconditions: callers should pass a slot in {@code [0, getSlots())}.
     * Invalid indices are tolerated and return empty. Side effects are limited
     * to the wrapped handler's read behavior.</p>
     *
     * @param slot virtual slot index
     * @return stack reported by the owning handler, or {@link ItemStack#EMPTY}
     * for an out-of-range index
     */
    @Override
    public ItemStack getStackInSlot(int slot) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return ItemStack.EMPTY;
        return ref.handler.getStackInSlot(ref.localSlot);
    }

    /**
     * Inserts into the wrapped handler that owns a virtual slot.
     *
     * <p>Preconditions: {@code slot} should be in {@code [0, getSlots())};
     * {@code stack} follows Forge item handler rules. Invalid slots are not
     * consumed. Side effects are delegated to the selected handler when
     * {@code simulate} is {@code false}.</p>
     *
     * @param slot     virtual slot index
     * @param stack    stack to offer to the owning handler
     * @param simulate {@code true} to query the result without changing storage
     * @return remainder that could not be inserted, or the original stack for an
     * invalid slot
     */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return stack;
        return ref.handler.insertItem(ref.localSlot, stack, simulate);
    }

    /**
     * Extracts from the wrapped handler that owns a virtual slot.
     *
     * <p>Preconditions: {@code slot} should be in {@code [0, getSlots())};
     * {@code amount} is expected to be non-negative by Forge callers. Invalid
     * slots return empty. Side effects occur only in the selected handler and
     * only when {@code simulate} is {@code false}.</p>
     *
     * @param slot     virtual slot index
     * @param amount   maximum item count requested
     * @param simulate {@code true} to query without removing items
     * @return extracted stack, or {@link ItemStack#EMPTY} when nothing can be
     * extracted
     */
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return ItemStack.EMPTY;
        return ref.handler.extractItem(ref.localSlot, amount, simulate);
    }

    /**
     * Returns the item limit for a virtual slot.
     *
     * @param slot virtual slot index
     * @return wrapped slot limit, or {@code 0} for an invalid index
     */
    @Override
    public int getSlotLimit(int slot) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return 0;
        return ref.handler.getSlotLimit(ref.localSlot);
    }

    /**
     * Checks whether a stack may be inserted into a virtual slot.
     *
     * <p>Preconditions: {@code slot} should be in {@code [0, getSlots())}.
     * Side effects are delegated to the wrapped handler's validation behavior.</p>
     *
     * @param slot  virtual slot index
     * @param stack stack being tested
     * @return {@code true} only when the owning handler accepts the stack
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return false;
        return ref.handler.isItemValid(ref.localSlot, stack);
    }

    /**
     * Returns the immutable list of handlers that contributed slots.
     *
     * @return wrapped handlers in virtual-slot order
     */
    public List<IItemHandler> getHandlers() {
        return handlers;
    }

    private SlotRef resolveSlot(int slot) {
        if (slot < 0 || slot >= slots) return null;
        for (int i = 0; i < handlers.size(); i++) {
            int start = offsets[i];
            int end = offsets[i + 1];
            if (slot >= start && slot < end) {
                return new SlotRef(handlers.get(i), slot - start);
            }
        }
        return null;
    }

    private static final class SlotRef {
        private final IItemHandler handler;
        private final int localSlot;

        private SlotRef(IItemHandler handler, int localSlot) {
            this.handler = handler;
            this.localSlot = localSlot;
        }
    }
}

