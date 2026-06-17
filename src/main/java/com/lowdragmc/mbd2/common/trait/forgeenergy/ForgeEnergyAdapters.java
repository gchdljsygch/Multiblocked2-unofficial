package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Adapters that expose MBD long-FE containers through Forge's integer {@link IEnergyStorage} API.
 *
 * <p>The adapters clamp visible values to {@link Integer#MAX_VALUE} because Forge Energy uses {@code int} amounts,
 * while the underlying MBD container may store values above that range.</p>
 */
public final class ForgeEnergyAdapters {
    private ForgeEnergyAdapters() {
    }

    /**
     * Side-independent Forge Energy wrapper around one long-FE container.
     *
     * <p>The configured {@link IO} gates mutating directions: Forge receive maps to MBD input and Forge extract maps
     * to MBD output. Simulation is forwarded to the delegate unchanged.</p>
     */
    public static final class Wrapper implements IEnergyStorage {
        private final ILongFeEnergyContainer delegate;
        private final IO io;

        /**
         * Creates an integer Forge Energy view over a long-FE container.
         *
         * @param delegate backing long-FE container
         * @param io       IO direction exposed by this wrapper
         */
        public Wrapper(ILongFeEnergyContainer delegate, IO io) {
            this.delegate = delegate;
            this.io = io;
        }

        /**
         * Receives FE into the delegate when input is allowed.
         *
         * @param maxReceive requested amount in Forge's {@code int} range
         * @param simulate   {@code true} to query capacity without changing stored energy
         * @return accepted amount, clamped to {@code int}
         */
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            if (!io.support(IO.IN)) return 0;
            long accepted = delegate.receiveEnergy(null, maxReceive, simulate);
            return (int) Math.min(Integer.MAX_VALUE, accepted);
        }

        /**
         * Extracts FE from the delegate when output is allowed.
         *
         * @param maxExtract requested amount in Forge's {@code int} range
         * @param simulate   {@code true} to query extraction without changing stored energy
         * @return extracted amount, clamped to {@code int}
         */
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (!io.support(IO.OUT)) return 0;
            long extracted = delegate.extractEnergy(null, maxExtract, simulate);
            return (int) Math.min(Integer.MAX_VALUE, extracted);
        }

        /**
         * Returns the delegate's visible stored energy.
         *
         * @return stored FE clamped to {@code 0..Integer.MAX_VALUE}
         */
        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, delegate.getEnergyStored()));
        }

        /**
         * Returns the delegate's visible capacity.
         *
         * @return capacity clamped to {@code 0..Integer.MAX_VALUE}
         */
        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, delegate.getEnergyCapacity()));
        }

        /**
         * Reports whether Forge extraction is exposed.
         *
         * @return {@code true} when wrapper IO supports output
         */
        @Override
        public boolean canExtract() {
            return io.support(IO.OUT);
        }

        /**
         * Reports whether Forge receive is exposed.
         *
         * @return {@code true} when wrapper IO supports input
         */
        @Override
        public boolean canReceive() {
            return io.support(IO.IN);
        }
    }

    /**
     * Combined Forge Energy view over multiple storages.
     *
     * <p>Receive and extract operations distribute the requested amount in list order. Stored energy and capacity are
     * summed and clamped to Forge's integer range.</p>
     */
    public static final class Combined implements IEnergyStorage {
        private final List<IEnergyStorage> storages;

        /**
         * Creates a combined immutable view of Forge Energy storages.
         *
         * @param storages ordered storages to expose
         */
        public Combined(List<IEnergyStorage> storages) {
            this.storages = List.copyOf(storages);
        }

        /**
         * Receives energy into storages in list order.
         *
         * @param maxReceive requested amount
         * @param simulate   {@code true} to avoid mutating backing storages
         * @return total amount accepted
         */
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

        /**
         * Extracts energy from storages in list order.
         *
         * @param maxExtract requested amount
         * @param simulate   {@code true} to avoid mutating backing storages
         * @return total amount extracted
         */
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

        /**
         * Returns total visible stored energy.
         *
         * @return sum clamped to {@code 0..Integer.MAX_VALUE}
         */
        @Override
        public int getEnergyStored() {
            long sum = 0;
            for (var s : storages) {
                sum += s.getEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
        }

        /**
         * Returns total visible capacity.
         *
         * @return sum clamped to {@code 0..Integer.MAX_VALUE}
         */
        @Override
        public int getMaxEnergyStored() {
            long sum = 0;
            for (var s : storages) {
                sum += s.getMaxEnergyStored();
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
        }

        /**
         * Reports whether any backing storage can extract.
         *
         * @return {@code true} when at least one storage can extract
         */
        @Override
        public boolean canExtract() {
            for (var s : storages) {
                if (s.canExtract()) return true;
            }
            return false;
        }

        /**
         * Reports whether any backing storage can receive.
         *
         * @return {@code true} when at least one storage can receive
         */
        @Override
        public boolean canReceive() {
            for (var s : storages) {
                if (s.canReceive()) return true;
            }
            return false;
        }
    }
}

