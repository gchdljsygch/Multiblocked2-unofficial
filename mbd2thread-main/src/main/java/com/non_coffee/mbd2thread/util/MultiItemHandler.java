package com.non_coffee.mbd2thread.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MultiItemHandler implements IItemHandler {
    private final List<IItemHandler> handlers;
    private final int[] offsets;
    private final int slots;

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

    public boolean isEmpty() {
        return slots <= 0;
    }

    @Override
    public int getSlots() {
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return ItemStack.EMPTY;
        return ref.handler.getStackInSlot(ref.localSlot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return stack;
        return ref.handler.insertItem(ref.localSlot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return ItemStack.EMPTY;
        return ref.handler.extractItem(ref.localSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return 0;
        return ref.handler.getSlotLimit(ref.localSlot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        SlotRef ref = resolveSlot(slot);
        if (ref == null) return false;
        return ref.handler.isItemValid(ref.localSlot, stack);
    }

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

