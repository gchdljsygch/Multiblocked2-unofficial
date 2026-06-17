package com.lowdragmc.mbd2.common.trait.fluid;

import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Side-filtered Forge fluid handler view over one or more internal
 * {@link FluidStorage} tanks.
 *
 * <p>The business goal is to expose machine fluid tanks through Forge capability
 * queries while enforcing configured input/output permissions and optional
 * same-fluid tank behavior. The wrapper delegates directly to the supplied
 * storages and is not thread-safe beyond the storage implementation; callers
 * should mutate it on the owning machine's logical thread.</p>
 */
public class FluidHandlerWrapper implements IFluidHandler {

    private final FluidStorage[] storages;
    private final IO io;
    private final boolean allowSameFluids;

    /**
     * Creates a side-filtered fluid handler wrapper.
     *
     * @param storages        internal fluid tanks exposed by this wrapper
     * @param io              capability IO allowed through this wrapper
     * @param allowSameFluids {@code true} to allow filling multiple tanks with
     *                        the same fluid during one fill operation
     */
    public FluidHandlerWrapper(FluidStorage[] storages, IO io, boolean allowSameFluids) {
        this.storages = storages;
        this.io = io;
        this.allowSameFluids = allowSameFluids;
    }

    private boolean canCapInput() {
        return io == IO.IN || io == IO.BOTH;
    }

    private boolean canCapOutput() {
        return io == IO.OUT || io == IO.BOTH;
    }

    /**
     * Returns the number of exposed tanks.
     *
     * @return length of the wrapped storage array
     */
    @Override
    public int getTanks() {
        return storages.length;
    }

    /**
     * Returns the fluid in a tank using Forge's fluid stack type.
     *
     * @param tank zero-based tank index
     * @return current tank fluid, or the wrapped storage's empty fluid
     */
    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidHelperImpl.toFluidStack(storages[tank].getFluid());
    }

    /**
     * Replaces the fluid in a tank.
     *
     * <p>Side effects: mutates the wrapped storage. This is an internal
     * modifiable path and does not check this wrapper's side IO.</p>
     *
     * @param tank       zero-based tank index
     * @param fluidStack fluid to store
     */
    public void setFluidInTank(int tank, @NotNull FluidStack fluidStack) {
        storages[tank].setFluid(FluidHelperImpl.toFluidStack(fluidStack));
    }

    /**
     * Returns the capacity of a tank.
     *
     * @param tank zero-based tank index
     * @return capacity truncated to {@code int} for Forge's API
     */
    @Override
    public int getTankCapacity(int tank) {
        return (int) storages[tank].getCapacity();
    }

    /**
     * Checks whether a fluid is valid for a tank.
     *
     * @param tank  zero-based tank index
     * @param stack fluid to test
     * @return wrapped storage validity result
     */
    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return storages[tank].isFluidValid(FluidHelperImpl.toFluidStack(stack));
    }

    /**
     * Attempts to fill the wrapped tanks through Forge's API.
     *
     * @param resource fluid to insert
     * @param action   {@link FluidAction#SIMULATE} to calculate without mutation,
     *                 {@link FluidAction#EXECUTE} to commit
     * @return amount accepted, or {@code 0} when input is not allowed
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (canCapInput()) {
            return (int) fillInternal(FluidHelperImpl.toFluidStack(resource), action.simulate());
        }
        return 0;
    }

    /**
     * Attempts to fill the wrapped tanks using LowDragLib fluid stacks.
     *
     * <p>Side effects: mutates storages only when {@code simulate == false}. When
     * same fluids are disallowed, an existing tank containing the same fluid is
     * preferred; otherwise filling stops after the first tank accepts fluid.</p>
     *
     * @param resource fluid to insert
     * @param simulate {@code true} to calculate without mutation
     * @return amount accepted
     */
    public long fillInternal(com.lowdragmc.lowdraglib.side.fluid.FluidStack resource, boolean simulate) {
        if (resource.isEmpty()) return 0;
        var copied = resource.copy();
        FluidStorage existingStorage = null;
        if (!allowSameFluids) {
            for (var storage : storages) {
                if (!storage.getFluid().isEmpty() && storage.getFluid().isFluidEqual(resource)) {
                    existingStorage = storage;
                    break;
                }
            }
        }
        if (existingStorage == null) {
            for (var storage : storages) {
                var filled = storage.fill(copied.copy(), simulate);
                if (filled > 0) {
                    copied.shrink(filled);
                    if (!allowSameFluids) {
                        break;
                    }
                }
                if (copied.isEmpty()) break;
            }
        } else {
            copied.shrink(existingStorage.fill(copied.copy(), simulate));
        }
        return resource.getAmount() - copied.getAmount();
    }


    /**
     * Drains a matching fluid through Forge's API.
     *
     * @param resource requested fluid and amount
     * @param action   {@link FluidAction#SIMULATE} to calculate without mutation,
     *                 {@link FluidAction#EXECUTE} to commit
     * @return drained fluid, or {@link FluidStack#EMPTY} when output is not
     * allowed
     */

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (canCapOutput()) {
            return FluidHelperImpl.toFluidStack(drainInternal(FluidHelperImpl.toFluidStack(resource), action.simulate()));
        }
        return FluidStack.EMPTY;
    }

    /**
     * Drains a matching LowDragLib fluid stack across wrapped tanks.
     *
     * <p>Side effects: mutates storages only when {@code simulate == false}. The
     * returned stack amount is the amount actually drained, using the requested
     * fluid identity/NBT.</p>
     *
     * @param resource requested fluid and amount
     * @param simulate {@code true} to calculate without mutation
     * @return drained fluid stack, or an empty stack when no matching fluid is
     * available
     */
    public com.lowdragmc.lowdraglib.side.fluid.FluidStack drainInternal(com.lowdragmc.lowdraglib.side.fluid.FluidStack resource, boolean simulate) {
        if (!resource.isEmpty()) {
            var copied = resource.copy();
            for (var transfer : storages) {
                var candidate = copied.copy();
                copied.shrink(transfer.drain(candidate, simulate).getAmount());
                if (copied.isEmpty()) break;
            }
            copied.setAmount(resource.getAmount() - copied.getAmount());
            return copied;
        }
        return com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty();
    }

    /**
     * Drains up to a maximum amount through Forge's API.
     *
     * @param maxDrain maximum amount to drain; {@code 0} drains nothing
     * @param action   {@link FluidAction#SIMULATE} to calculate without mutation,
     *                 {@link FluidAction#EXECUTE} to commit
     * @return drained fluid, or {@link FluidStack#EMPTY} when output is not
     * allowed
     */
    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (canCapOutput()) {
            return FluidHelperImpl.toFluidStack(drainInternal(maxDrain, action.simulate()));
        }
        return FluidStack.EMPTY;
    }

    /**
     * Drains up to a maximum amount from wrapped tanks.
     *
     * <p>After the first non-empty drain, later tanks are drained only for the
     * same fluid as the first drained stack. Side effects depend on
     * {@code simulate}.</p>
     *
     * @param maxDrain maximum amount to drain; {@code 0} drains nothing
     * @param simulate {@code true} to calculate without mutation
     * @return drained fluid stack, or an empty stack when no fluid is available
     */
    public com.lowdragmc.lowdraglib.side.fluid.FluidStack drainInternal(long maxDrain, boolean simulate) {
        if (maxDrain == 0) {
            return com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty();
        }
        com.lowdragmc.lowdraglib.side.fluid.FluidStack totalDrained = null;
        for (var storage : storages) {
            if (totalDrained == null || totalDrained.isEmpty()) {
                totalDrained = storage.drain(maxDrain, simulate);
                if (totalDrained.isEmpty()) {
                    totalDrained = null;
                } else {
                    maxDrain -= totalDrained.getAmount();
                }
            } else {
                com.lowdragmc.lowdraglib.side.fluid.FluidStack copy = totalDrained.copy();
                copy.setAmount(maxDrain);
                com.lowdragmc.lowdraglib.side.fluid.FluidStack drain = storage.drain(copy, simulate);
                totalDrained.grow(drain.getAmount());
                maxDrain -= drain.getAmount();
            }
            if (maxDrain <= 0) break;
        }
        return totalDrained == null ? com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty() : totalDrained;
    }
}
