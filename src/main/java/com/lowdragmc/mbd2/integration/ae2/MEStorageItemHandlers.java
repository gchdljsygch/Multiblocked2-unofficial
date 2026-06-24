package com.lowdragmc.mbd2.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Bridges AE2 ME storage capabilities into the Forge item handler shape used by
 * the multiblock builder.
 */
public final class MEStorageItemHandlers {
    private MEStorageItemHandlers() {
    }

    /**
     * Adds item handler views for every AE2 ME storage capability exposed by a
     * block entity.
     *
     * @param be       block entity to inspect
     * @param handlers mutable output list that receives ME-backed handlers
     */
    public static void collectItemHandlers(BlockEntity be, List<IItemHandler> handlers) {
        if (be == null || handlers == null) return;

        Set<MEStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IActionSource blockSource = sourceFor(be);
        for (Direction dir : Direction.values()) {
            IActionSource source = sourceForPart(be, dir, blockSource);
            collectStorage(be, dir, source, seen, handlers);
        }
        collectStorage(be, null, blockSource, seen, handlers);
    }

    /**
     * Checks whether a block entity exposes AE2 ME storage that can be used as
     * an item source.
     *
     * @param be block entity to inspect
     * @return {@code true} when any ME storage capability is present
     */
    public static boolean hasItemStorage(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : Direction.values()) {
            if (hasStorage(be, dir)) return true;
        }
        return hasStorage(be, null);
    }

    private static void collectStorage(BlockEntity be,
                                       @Nullable Direction side,
                                       IActionSource source,
                                       Set<MEStorage> seen,
                                       List<IItemHandler> handlers) {
        try {
            be.getCapability(Capabilities.STORAGE, side).resolve().ifPresent(storage -> {
                if (storage != null && seen.add(storage)) {
                    handlers.add(new MEStorageItemHandler(storage, source));
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean hasStorage(BlockEntity be, @Nullable Direction side) {
        try {
            return be.getCapability(Capabilities.STORAGE, side).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static IActionSource sourceFor(Object object) {
        if (object instanceof IActionHost host) {
            return IActionSource.ofMachine(host);
        }
        return IActionSource.empty();
    }

    private static IActionSource sourceForPart(BlockEntity be, Direction side, IActionSource fallback) {
        if (be instanceof IPartHost host) {
            IPart part = host.getPart(side);
            if (part instanceof IActionHost) {
                return sourceFor(part);
            }
        }
        return fallback;
    }

    private static final class MEStorageItemHandler implements IItemHandler {
        private final MEStorage storage;
        private final IActionSource source;

        private MEStorageItemHandler(MEStorage storage, IActionSource source) {
            this.storage = storage;
            this.source = source;
        }

        @Override
        public int getSlots() {
            return snapshot().size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemEntry entry = entryAt(slot);
            if (entry == null) return ItemStack.EMPTY;
            return entry.key.toStack(toStackCount(entry.amount));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= getSlots()) return stack;
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) return stack;

            long inserted = storage.insert(key, stack.getCount(), Actionable.ofSimulate(simulate), source);
            if (inserted <= 0) return stack;
            if (inserted >= stack.getCount()) return ItemStack.EMPTY;
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - (int) inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            ItemEntry entry = entryAt(slot);
            if (entry == null) return ItemStack.EMPTY;

            long requested = Math.min(amount, entry.amount);
            long extracted = storage.extract(entry.key, requested, Actionable.ofSimulate(simulate), source);
            if (extracted <= 0) return ItemStack.EMPTY;
            return entry.key.toStack(toStackCount(extracted));
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty() && AEItemKey.of(stack) != null;
        }

        @Nullable
        private ItemEntry entryAt(int slot) {
            if (slot < 0) return null;
            List<ItemEntry> entries = snapshot();
            if (slot >= entries.size()) return null;
            return entries.get(slot);
        }

        private List<ItemEntry> snapshot() {
            List<ItemEntry> entries = new ArrayList<>();
            for (var entry : storage.getAvailableStacks()) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount > 0 && key instanceof AEItemKey itemKey) {
                    entries.add(new ItemEntry(itemKey, amount));
                }
            }
            return entries;
        }

        private static int toStackCount(long amount) {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1, amount));
        }

        private record ItemEntry(AEItemKey key, long amount) {
        }
    }
}
