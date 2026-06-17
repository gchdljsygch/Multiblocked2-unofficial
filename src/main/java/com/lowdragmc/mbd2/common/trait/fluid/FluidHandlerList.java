package com.lowdragmc.mbd2.common.trait.fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Concatenated view over multiple Forge fluid handlers.
 *
 * <p>The business goal is to expose several trait fluid storages as one logical
 * Forge capability. Tank indexes are flattened in handler order. The record
 * keeps the supplied handler array by reference; callers must not mutate it
 * concurrently with capability access.</p>
 *
 * @param handlers handlers to expose in order
 */
public record FluidHandlerList(IFluidHandler[] handlers) implements IFluidHandler {
    /**
     * Returns the total number of flattened tanks.
     *
     * @return sum of all child handler tank counts
     */
    @Override
    public int getTanks() {
        return Arrays.stream(handlers).mapToInt(IFluidHandler::getTanks).sum();
    }

    /**
     * Returns fluid from a flattened tank index.
     *
     * @param tank zero-based flattened tank index
     * @return child handler fluid, or {@link FluidStack#EMPTY} when out of range
     */
    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        int index = 0;
        for (var handler : handlers) {
            if (tank - index < handler.getTanks()) {
                return handler.getFluidInTank(tank - index);
            }
            index += handler.getTanks();
        }
        return FluidStack.EMPTY;
    }

    /**
     * Returns capacity for a flattened tank index.
     *
     * @param tank zero-based flattened tank index
     * @return child tank capacity, or {@code 0} when out of range
     */
    @Override
    public int getTankCapacity(int tank) {
        int index = 0;
        for (var handler : handlers) {
            if (tank - index < handler.getTanks()) {
                return handler.getTankCapacity(tank - index);
            }
            index += handler.getTanks();
        }
        return 0;
    }

    /**
     * Checks whether a fluid is valid for a flattened tank index.
     *
     * @param tank  zero-based flattened tank index
     * @param stack fluid to test
     * @return child handler validity result, or {@code false} when out of range
     */
    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        int index = 0;
        for (var handler : handlers) {
            if (tank - index < handler.getTanks()) {
                return handler.isFluidValid(tank - index, stack);
            }
            index += handler.getTanks();
        }
        return false;
    }

    /**
     * Attempts to fill child handlers in order.
     *
     * <p>Side effects depend on {@code action}. The input stack is copied and the
     * returned amount is the total accepted by all handlers.</p>
     *
     * @param resource fluid to insert
     * @param action   simulation or execution mode
     * @return total amount accepted
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        var copied = resource.copy();
        for (var handler : handlers) {
            var candidate = copied.copy();
            copied.shrink(handler.fill(candidate, action));
            if (copied.isEmpty()) break;
        }
        return resource.getAmount() - copied.getAmount();
    }

    /**
     * Attempts to drain a matching fluid from child handlers in order.
     *
     * @param resource requested fluid and amount
     * @param action   simulation or execution mode
     * @return total drained stack, or {@link FluidStack#EMPTY} when nothing can be
     * drained
     */
    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        var copied = resource.copy();
        for (var handler : handlers) {
            var candidate = copied.copy();
            copied.shrink(handler.drain(candidate, action).getAmount());
            if (copied.isEmpty()) break;
        }
        copied.setAmount(resource.getAmount() - copied.getAmount());
        return copied;
    }

    /**
     * Drains up to a maximum amount from child handlers in order.
     *
     * <p>After the first child returns a non-empty fluid, later children are
     * drained only for that same fluid type/NBT.</p>
     *
     * @param maxDrain maximum amount to drain; {@code 0} drains nothing
     * @param action   simulation or execution mode
     * @return total drained fluid, or {@link FluidStack#EMPTY} when nothing can be
     * drained
     */
    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain == 0) {
            return FluidStack.EMPTY;
        }
        FluidStack totalDrained = null;
        for (var storage : handlers) {
            if (totalDrained == null || totalDrained.isEmpty()) {
                totalDrained = storage.drain(maxDrain, action);
                if (totalDrained.isEmpty()) {
                    totalDrained = null;
                } else {
                    maxDrain -= totalDrained.getAmount();
                }
            } else {
                FluidStack copy = totalDrained.copy();
                copy.setAmount(maxDrain);
                FluidStack drain = storage.drain(copy, action);
                totalDrained.grow(drain.getAmount());
                maxDrain -= drain.getAmount();
            }
            if (maxDrain <= 0) break;
        }
        return totalDrained == null ? FluidStack.EMPTY : totalDrained;
    }
}
