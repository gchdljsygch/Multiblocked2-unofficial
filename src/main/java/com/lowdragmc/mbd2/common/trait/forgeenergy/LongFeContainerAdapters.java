package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Adapters for composing and direction-gating MBD long-FE energy containers.
 */
public final class LongFeContainerAdapters {
    private LongFeContainerAdapters() {
    }

    /**
     * IO-gated wrapper around a long-FE container.
     *
     * <p>Read methods are always forwarded. Mutating and transfer methods are allowed only when the wrapper
     * {@link IO} supports the corresponding recipe direction.</p>
     */
    public static final class Wrapper implements ILongFeEnergyContainer {
        private final ILongFeEnergyContainer delegate;
        private final IO io;

        /**
         * Creates a direction-gated view of one container.
         *
         * @param delegate backing container
         * @param io       IO direction exposed by this wrapper
         */
        public Wrapper(ILongFeEnergyContainer delegate, IO io) {
            this.delegate = delegate;
            this.io = io;
        }

        /**
         * Returns the delegate's stored energy.
         *
         * @return stored FE as a long
         */
        @Override
        public long getEnergyStored() {
            return delegate.getEnergyStored();
        }

        /**
         * Returns the delegate's capacity.
         *
         * @return maximum FE as a long
         */
        @Override
        public long getEnergyCapacity() {
            return delegate.getEnergyCapacity();
        }

        /**
         * Sets stored energy when output mutation is allowed.
         *
         * @param energyStored requested stored FE
         */
        @Override
        public void setEnergyStored(long energyStored) {
            if (!io.support(IO.OUT)) return;
            delegate.setEnergyStored(energyStored);
        }

        /**
         * Changes stored energy when the sign is compatible with this wrapper's IO.
         *
         * @param energyToAdd positive to add, negative to remove
         * @return actual signed change, or {@code 0} when blocked
         */
        @Override
        public long changeEnergy(long energyToAdd) {
            if (energyToAdd > 0 && !io.support(IO.OUT)) return 0;
            if (energyToAdd < 0 && !io.support(IO.IN)) return 0;
            return delegate.changeEnergy(energyToAdd);
        }

        /**
         * Returns the delegate's receive limit.
         *
         * @return maximum FE accepted per tick
         */
        @Override
        public long getMaxReceivePerTick() {
            return delegate.getMaxReceivePerTick();
        }

        /**
         * Returns the delegate's extract limit.
         *
         * @return maximum FE extracted per tick
         */
        @Override
        public long getMaxExtractPerTick() {
            return delegate.getMaxExtractPerTick();
        }

        /**
         * Reports whether this wrapper and delegate can receive from a side.
         *
         * @param side side being queried, or {@code null} for side-independent access
         * @return {@code true} when input is exposed and delegate accepts the side
         */
        @Override
        public boolean canReceive(Direction side) {
            return io.support(IO.IN) && delegate.canReceive(side);
        }

        /**
         * Reports whether this wrapper and delegate can extract from a side.
         *
         * @param side side being queried, or {@code null} for side-independent access
         * @return {@code true} when output is exposed and delegate extracts from the side
         */
        @Override
        public boolean canExtract(Direction side) {
            return io.support(IO.OUT) && delegate.canExtract(side);
        }

        /**
         * Receives energy when input is allowed.
         *
         * @param side     transfer side, or {@code null}
         * @param amount   requested FE amount
         * @param simulate {@code true} to avoid mutating the delegate
         * @return accepted FE amount
         */
        @Override
        public long receiveEnergy(Direction side, long amount, boolean simulate) {
            if (!io.support(IO.IN)) return 0L;
            return delegate.receiveEnergy(side, amount, simulate);
        }

        /**
         * Extracts energy when output is allowed.
         *
         * @param side     transfer side, or {@code null}
         * @param amount   requested FE amount
         * @param simulate {@code true} to avoid mutating the delegate
         * @return extracted FE amount
         */
        @Override
        public long extractEnergy(Direction side, long amount, boolean simulate) {
            if (!io.support(IO.OUT)) return 0L;
            return delegate.extractEnergy(side, amount, simulate);
        }
    }

    /**
     * Combined long-FE view over multiple containers.
     *
     * <p>Storage and limits are summed. Set/change/transfer operations are distributed in list order and preserve
     * each backing container's own clamping and side rules.</p>
     */
    public static final class ListView implements ILongFeEnergyContainer {
        private final List<ILongFeEnergyContainer> containers;

        /**
         * Creates an immutable combined view of long-FE containers.
         *
         * @param containers ordered containers to expose
         */
        public ListView(List<ILongFeEnergyContainer> containers) {
            this.containers = java.util.List.copyOf(containers);
        }

        /**
         * Returns total stored energy.
         *
         * @return sum of all backing containers
         */
        @Override
        public long getEnergyStored() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getEnergyStored();
            }
            return sum;
        }

        /**
         * Returns total capacity.
         *
         * @return sum of all backing capacities
         */
        @Override
        public long getEnergyCapacity() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getEnergyCapacity();
            }
            return sum;
        }

        /**
         * Distributes an absolute stored-energy value across containers in list order.
         *
         * <p>Energy beyond total capacity is clamped by filling every backing container.</p>
         *
         * @param energyStored requested total stored FE
         */
        @Override
        public void setEnergyStored(long energyStored) {
            long remaining = Math.max(0L, energyStored);
            for (var c : containers) {
                long fill = Math.min(c.getEnergyCapacity(), remaining);
                c.setEnergyStored(fill);
                remaining -= fill;
            }
            if (remaining > 0) {
                for (int i = containers.size() - 1; i >= 0; i--) {
                    var c = containers.get(i);
                    c.setEnergyStored(c.getEnergyCapacity());
                    remaining -= c.getEnergyCapacity();
                    if (remaining <= 0) break;
                }
            }
        }

        /**
         * Adds or removes energy across containers in list order.
         *
         * @param energyToAdd positive to add, negative to remove
         * @return actual signed change
         */
        @Override
        public long changeEnergy(long energyToAdd) {
            long remaining = energyToAdd;
            if (remaining > 0) {
                for (var c : containers) {
                    if (remaining <= 0) break;
                    long changed = c.changeEnergy(remaining);
                    remaining -= changed;
                }
            } else if (remaining < 0) {
                for (var c : containers) {
                    if (remaining >= 0) break;
                    long changed = c.changeEnergy(remaining);
                    remaining -= changed;
                }
            }
            return energyToAdd - remaining;
        }

        /**
         * Returns total receive limit.
         *
         * @return sum of backing receive limits
         */
        @Override
        public long getMaxReceivePerTick() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getMaxReceivePerTick();
            }
            return sum;
        }

        /**
         * Returns total extract limit.
         *
         * @return sum of backing extract limits
         */
        @Override
        public long getMaxExtractPerTick() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getMaxExtractPerTick();
            }
            return sum;
        }

        /**
         * Reports whether any backing container can receive from a side.
         *
         * @param side side being queried, or {@code null}
         * @return {@code true} when at least one backing container can receive
         */
        @Override
        public boolean canReceive(Direction side) {
            for (var c : containers) {
                if (c.canReceive(side)) return true;
            }
            return false;
        }

        /**
         * Reports whether any backing container can extract from a side.
         *
         * @param side side being queried, or {@code null}
         * @return {@code true} when at least one backing container can extract
         */
        @Override
        public boolean canExtract(Direction side) {
            for (var c : containers) {
                if (c.canExtract(side)) return true;
            }
            return false;
        }

        /**
         * Receives energy into backing containers in list order.
         *
         * @param side     transfer side, or {@code null}
         * @param amount   requested FE amount
         * @param simulate {@code true} to avoid mutating backing containers
         * @return total accepted FE
         */
        @Override
        public long receiveEnergy(Direction side, long amount, boolean simulate) {
            long remaining = amount;
            long acceptedTotal = 0;
            for (var c : containers) {
                if (remaining <= 0) break;
                long accepted = c.receiveEnergy(side, remaining, simulate);
                acceptedTotal += accepted;
                remaining -= accepted;
            }
            return acceptedTotal;
        }

        /**
         * Extracts energy from backing containers in list order.
         *
         * @param side     transfer side, or {@code null}
         * @param amount   requested FE amount
         * @param simulate {@code true} to avoid mutating backing containers
         * @return total extracted FE
         */
        @Override
        public long extractEnergy(Direction side, long amount, boolean simulate) {
            long remaining = amount;
            long extractedTotal = 0;
            for (var c : containers) {
                if (remaining <= 0) break;
                long extracted = c.extractEnergy(side, remaining, simulate);
                extractedTotal += extracted;
                remaining -= extracted;
            }
            return extractedTotal;
        }
    }
}

