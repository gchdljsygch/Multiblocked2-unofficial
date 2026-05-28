package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraft.core.Direction;

import java.util.List;

public final class LongFeContainerAdapters {
    private LongFeContainerAdapters() {
    }

    public static final class Wrapper implements ILongFeEnergyContainer {
        private final ILongFeEnergyContainer delegate;
        private final IO io;

        public Wrapper(ILongFeEnergyContainer delegate, IO io) {
            this.delegate = delegate;
            this.io = io;
        }

        @Override
        public long getEnergyStored() {
            return delegate.getEnergyStored();
        }

        @Override
        public long getEnergyCapacity() {
            return delegate.getEnergyCapacity();
        }

        @Override
        public void setEnergyStored(long energyStored) {
            if (!io.support(IO.OUT)) return;
            delegate.setEnergyStored(energyStored);
        }

        @Override
        public long changeEnergy(long energyToAdd) {
            if (energyToAdd > 0 && !io.support(IO.OUT)) return 0;
            if (energyToAdd < 0 && !io.support(IO.IN)) return 0;
            return delegate.changeEnergy(energyToAdd);
        }

        @Override
        public long getMaxReceivePerTick() {
            return delegate.getMaxReceivePerTick();
        }

        @Override
        public long getMaxExtractPerTick() {
            return delegate.getMaxExtractPerTick();
        }

        @Override
        public boolean canReceive(Direction side) {
            return io.support(IO.IN) && delegate.canReceive(side);
        }

        @Override
        public boolean canExtract(Direction side) {
            return io.support(IO.OUT) && delegate.canExtract(side);
        }

        @Override
        public long receiveEnergy(Direction side, long amount, boolean simulate) {
            if (!io.support(IO.IN)) return 0L;
            return delegate.receiveEnergy(side, amount, simulate);
        }

        @Override
        public long extractEnergy(Direction side, long amount, boolean simulate) {
            if (!io.support(IO.OUT)) return 0L;
            return delegate.extractEnergy(side, amount, simulate);
        }
    }

    public static final class ListView implements ILongFeEnergyContainer {
        private final List<ILongFeEnergyContainer> containers;

        public ListView(List<ILongFeEnergyContainer> containers) {
            this.containers = java.util.List.copyOf(containers);
        }

        @Override
        public long getEnergyStored() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getEnergyStored();
            }
            return sum;
        }

        @Override
        public long getEnergyCapacity() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getEnergyCapacity();
            }
            return sum;
        }

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

        @Override
        public long getMaxReceivePerTick() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getMaxReceivePerTick();
            }
            return sum;
        }

        @Override
        public long getMaxExtractPerTick() {
            long sum = 0;
            for (var c : containers) {
                sum += c.getMaxExtractPerTick();
            }
            return sum;
        }

        @Override
        public boolean canReceive(Direction side) {
            for (var c : containers) {
                if (c.canReceive(side)) return true;
            }
            return false;
        }

        @Override
        public boolean canExtract(Direction side) {
            for (var c : containers) {
                if (c.canExtract(side)) return true;
            }
            return false;
        }

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

