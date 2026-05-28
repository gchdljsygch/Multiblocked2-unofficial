package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

public final class ForgeEnergyAdapters {
    private ForgeEnergyAdapters() {
    }

    public static final class Wrapper implements IEnergyStorage {
        private final ILongFeEnergyContainer delegate;
        private final IO io;

        public Wrapper(ILongFeEnergyContainer delegate, IO io) {
            this.delegate = delegate;
            this.io = io;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            if (!io.support(IO.IN)) return 0;
            long accepted = delegate.receiveEnergy(null, maxReceive, simulate);
            return (int) Math.min(Integer.MAX_VALUE, accepted);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (!io.support(IO.OUT)) return 0;
            long extracted = delegate.extractEnergy(null, maxExtract, simulate);
            return (int) Math.min(Integer.MAX_VALUE, extracted);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, delegate.getEnergyStored()));
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, delegate.getEnergyCapacity()));
        }

        @Override
        public boolean canExtract() {
            return io.support(IO.OUT);
        }

        @Override
        public boolean canReceive() {
            return io.support(IO.IN);
        }
    }

    public static final class Combined implements IEnergyStorage {
        private final List<IEnergyStorage> storages;

        public Combined(List<IEnergyStorage> storages) {
            this.storages = List.copyOf(storages);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int remaining = maxReceive;
            int acceptedTotal = 0;
            for (var s : storages) {
                if (remaining <= 0) break;
                int accepted = s.receiveEnergy(remaining, simulate);
                acceptedTotal += accepted;
                remaining -= accepted;
            }
            return acceptedTotal;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int remaining = maxExtract;
            int extractedTotal = 0;
            for (var s : storages) {
                if (remaining <= 0) break;
                int extracted = s.extractEnergy(remaining, simulate);
                extractedTotal += extracted;
                remaining -= extracted;
            }
            return extractedTotal;
        }

        @Override
        public int getEnergyStored() {
            long sum = 0;
            for (var s : storages) {
                sum += s.getEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
        }

        @Override
        public int getMaxEnergyStored() {
            long sum = 0;
            for (var s : storages) {
                sum += s.getMaxEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
        }

        @Override
        public boolean canExtract() {
            for (var s : storages) {
                if (s.canExtract()) return true;
            }
            return false;
        }

        @Override
        public boolean canReceive() {
            for (var s : storages) {
                if (s.canReceive()) return true;
            }
            return false;
        }
    }
}

