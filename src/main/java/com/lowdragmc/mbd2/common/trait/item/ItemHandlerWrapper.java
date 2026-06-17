package com.lowdragmc.mbd2.common.trait.item;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Side-filtered item handler view over an {@link ItemStackTransfer}.
 *
 * <p>The business goal is to expose the same internal item storage through Forge
 * capabilities while enforcing the configured side IO. The wrapper does not copy
 * storage; every mutating call delegates to the wrapped transfer when the
 * configured {@link IO} permits it. Thread-safety is the same as the wrapped
 * storage and should be limited to the owning machine's logical thread.</p>
 */
public class ItemHandlerWrapper implements IItemHandlerModifiable {
    private final ItemStackTransfer storage;
    private final IO io;

    /**
     * Creates a side-filtered item handler wrapper.
     *
     * @param storage internal item storage to expose
     * @param io      capability IO permitted through this wrapper
     */
    public ItemHandlerWrapper(ItemStackTransfer storage, IO io) {
        this.storage = storage;
        this.io = io;
    }

    private boolean canCapInput() {
        return io == IO.IN || io == IO.BOTH;
    }

    private boolean canCapOutput() {
        return io == IO.OUT || io == IO.BOTH;
    }

    /**
     * Returns the slot count of the wrapped storage.
     *
     * @return number of addressable slots
     */
    @Override
    public int getSlots() {
        return storage.getSlots();
    }

    /**
     * Returns the stack currently stored in a slot.
     *
     * @param slot zero-based slot index
     * @return stack in that slot, or the wrapped handler's empty-stack result
     */
    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return storage.getStackInSlot(slot);
    }

    /**
     * Replaces the stack in a slot, ignoring side IO restrictions.
     *
     * <p>This is the modifiable-handler administrative path used by internal UI
     * or machine logic. Side effects: mutates the wrapped storage.</p>
     *
     * @param slot  zero-based slot index
     * @param stack stack to store
     */
    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        storage.setStackInSlot(slot, stack);
    }

    /**
     * Attempts to insert a stack when this wrapper permits input.
     *
     * @param slot     zero-based target slot
     * @param stack    stack to insert
     * @param simulate {@code true} to calculate without mutating storage
     * @return uninserted remainder; the original stack when input is not allowed
     */
    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (canCapInput()) {
            return storage.insertItem(slot, stack, simulate);
        }
        return stack;
    }

    /**
     * Attempts to extract items when this wrapper permits output.
     *
     * @param slot     zero-based source slot
     * @param amount   maximum item count to extract
     * @param simulate {@code true} to calculate without mutating storage
     * @return extracted stack, or {@link ItemStack#EMPTY} when output is not
     * allowed
     */
    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (canCapOutput()) {
            return storage.extractItem(slot, amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the maximum stack size accepted by a slot.
     *
     * @param slot zero-based slot index
     * @return wrapped storage slot limit
     */
    @Override
    public int getSlotLimit(int slot) {
        return storage.getSlotLimit(slot);
    }

    /**
     * Checks whether a stack can be inserted into a slot.
     *
     * @param slot  zero-based slot index
     * @param stack stack to test
     * @return wrapped storage validity result; this does not check side IO
     */
    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return storage.isItemValid(slot, stack);
    }

}
