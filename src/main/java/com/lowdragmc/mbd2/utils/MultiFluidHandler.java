package com.lowdragmc.mbd2.utils;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presents several fluid handlers as one contiguous virtual tank set.
 *
 * <p>The business goal is to let auto-build draw bucket-sized fluid amounts
 * from every sided and unsided fluid capability on a bound block entity through
 * one {@link IFluidHandler}. The wrapper's handler list is immutable after
 * construction, but fluid reads and mutations are delegated to the wrapped
 * handlers, so thread safety and side effects follow those capabilities.</p>
 */
public final class MultiFluidHandler implements IFluidHandler {
    private final List<IFluidHandler> handlers;
    private final int[] offsets;
    private final int tanks;

    /**
     * Builds a virtual fluid handler from handlers that currently expose tanks.
     *
     * <p>Preconditions: the list itself must be non-null. Null handlers and
     * zero-tank handlers are ignored. Side effects: reads tank counts only and
     * does not retain the mutable input list.</p>
     *
     * @param handlers candidate handlers in the order they should appear in the
     *                 virtual tank range
     */
    public MultiFluidHandler(List<IFluidHandler> handlers) {
        Objects.requireNonNull(handlers);
        List<IFluidHandler> filtered = new ArrayList<>();
        for (IFluidHandler h : handlers) {
            if (h != null && h.getTanks() > 0) filtered.add(h);
        }
        this.handlers = List.copyOf(filtered);
        this.offsets = new int[this.handlers.size() + 1];
        int total = 0;
        for (int i = 0; i < this.handlers.size(); i++) {
            offsets[i] = total;
            total += this.handlers.get(i).getTanks();
        }
        offsets[this.handlers.size()] = total;
        this.tanks = total;
    }

    /**
     * Indicates whether the wrapper has any virtual tanks.
     *
     * @return {@code true} when construction found no usable fluid tanks
     */
    public boolean isEmpty() {
        return tanks <= 0;
    }

    /**
     * Returns the sum of all wrapped handler tanks.
     *
     * @return virtual tank count in the range {@code [0, Integer.MAX_VALUE]}
     */
    @Override
    public int getTanks() {
        return tanks;
    }

    /**
     * Reads the contents of a virtual tank.
     *
     * <p>Preconditions: callers should pass a tank in {@code [0, getTanks())}.
     * Invalid indices are tolerated and return empty. Side effects are limited
     * to the wrapped handler's read behavior.</p>
     *
     * @param tank virtual tank index
     * @return fluid stack reported by the owning handler, or
     * {@link FluidStack#EMPTY} for an out-of-range index
     */
    @Override
    public FluidStack getFluidInTank(int tank) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return FluidStack.EMPTY;
        return ref.handler.getFluidInTank(ref.localTank);
    }

    /**
     * Returns the capacity of a virtual tank.
     *
     * @param tank virtual tank index
     * @return wrapped tank capacity in millibuckets, or {@code 0} for an invalid
     * index
     */
    @Override
    public int getTankCapacity(int tank) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return 0;
        return ref.handler.getTankCapacity(ref.localTank);
    }

    /**
     * Checks whether a fluid may be inserted into a virtual tank.
     *
     * <p>Preconditions: {@code tank} should be in {@code [0, getTanks())}.
     * Side effects are delegated to the wrapped handler's validation behavior.</p>
     *
     * @param tank  virtual tank index
     * @param stack fluid stack being tested
     * @return {@code true} only when the owning handler accepts the fluid
     */
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return false;
        return ref.handler.isFluidValid(ref.localTank, stack);
    }

    /**
     * Offers a fluid stack to each wrapped handler until the request is fully
     * accepted or no handler can accept more.
     *
     * <p>Preconditions: {@code resource} must be non-null and normally have a
     * positive amount. Side effects are delegated to wrapped handlers when
     * {@code action} executes; simulation should not mutate them according to
     * Forge rules.</p>
     *
     * @param resource fluid and amount requested for insertion
     * @param action   Forge execution mode controlling whether storage changes
     * @return total amount accepted in millibuckets, in the range
     * {@code [0, resource.getAmount()]}
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        int filled = 0;
        FluidStack remaining = resource.copy();
        for (IFluidHandler handler : handlers) {
            int part = handler.fill(remaining, action);
            if (part > 0) {
                filled += part;
                remaining.shrink(part);
                if (remaining.isEmpty()) break;
            }
        }
        return filled;
    }

    /**
     * Drains a matching fluid request across wrapped handlers.
     *
     * <p>Business goal: let a bound multi-sided tank satisfy one bucket request
     * even when the fluid is distributed across compatible internal handlers.
     * Preconditions: {@code resource} must be non-null. The returned stack only
     * grows with parts that report the same fluid; incompatible parts are not
     * merged into the result. Side effects are delegated to wrapped handlers
     * when {@code action} executes.</p>
     *
     * @param resource requested fluid type and maximum amount
     * @param action   Forge execution mode controlling whether storage changes
     * @return drained fluid stack, or {@link FluidStack#EMPTY} when no matching
     * fluid is available
     */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        int toDrain = resource.getAmount();
        FluidStack drainedTotal = FluidStack.EMPTY;
        FluidStack remainingRequest = resource.copy();
        for (IFluidHandler handler : handlers) {
            FluidStack part = handler.drain(remainingRequest, action);
            if (!part.isEmpty()) {
                if (drainedTotal.isEmpty()) {
                    drainedTotal = part.copy();
                } else if (drainedTotal.isFluidEqual(part)) {
                    drainedTotal.grow(part.getAmount());
                }
                remainingRequest.shrink(part.getAmount());
                toDrain -= part.getAmount();
                if (toDrain <= 0) break;
            }
        }
        return drainedTotal;
    }

    /**
     * Drains up to a requested amount from wrapped handlers in order.
     *
     * <p>Preconditions: {@code maxDrain} is measured in millibuckets; values
     * less than or equal to zero return empty. The result merges only compatible
     * fluid parts. Side effects are delegated to wrapped handlers when
     * {@code action} executes.</p>
     *
     * @param maxDrain maximum amount to drain in millibuckets
     * @param action   Forge execution mode controlling whether storage changes
     * @return drained fluid stack, or {@link FluidStack#EMPTY} when nothing can
     * be drained
     */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        int toDrain = maxDrain;
        FluidStack drainedTotal = FluidStack.EMPTY;
        for (IFluidHandler handler : handlers) {
            FluidStack part = handler.drain(toDrain, action);
            if (!part.isEmpty()) {
                if (drainedTotal.isEmpty()) {
                    drainedTotal = part.copy();
                } else if (drainedTotal.isFluidEqual(part)) {
                    drainedTotal.grow(part.getAmount());
                }
                toDrain -= part.getAmount();
                if (toDrain <= 0) break;
            }
        }
        return drainedTotal;
    }

    /**
     * Returns the immutable list of handlers that contributed tanks.
     *
     * @return wrapped handlers in virtual-tank order
     */
    public List<IFluidHandler> getHandlers() {
        return handlers;
    }

    private TankRef resolveTank(int tank) {
        if (tank < 0 || tank >= tanks) return null;
        for (int i = 0; i < handlers.size(); i++) {
            int start = offsets[i];
            int end = offsets[i + 1];
            if (tank >= start && tank < end) {
                return new TankRef(handlers.get(i), tank - start);
            }
        }
        return null;
    }

    private static final class TankRef {
        private final IFluidHandler handler;
        private final int localTank;

        private TankRef(IFluidHandler handler, int localTank) {
            this.handler = handler;
            this.localTank = localTank;
        }
    }
}
