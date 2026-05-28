package com.non_coffee.mbd2thread.util;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MultiFluidHandler implements IFluidHandler {
    private final List<IFluidHandler> handlers;
    private final int[] offsets;
    private final int tanks;

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

    public boolean isEmpty() {
        return tanks <= 0;
    }

    @Override
    public int getTanks() {
        return tanks;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return FluidStack.EMPTY;
        return ref.handler.getFluidInTank(ref.localTank);
    }

    @Override
    public int getTankCapacity(int tank) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return 0;
        return ref.handler.getTankCapacity(ref.localTank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        TankRef ref = resolveTank(tank);
        if (ref == null) return false;
        return ref.handler.isFluidValid(ref.localTank, stack);
    }

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
