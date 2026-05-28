package com.non_coffee.mbd2thread.energy.fe.trait;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.AutoIO;
import com.lowdragmc.mbd2.common.trait.IAutoIOTrait;
import com.lowdragmc.mbd2.common.trait.ICapabilityProviderTrait;
import com.lowdragmc.mbd2.common.trait.RecipeHandlerTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.non_coffee.mbd2thread.energy.fe.api.ILongFeEnergyContainer;
import com.non_coffee.mbd2thread.energy.fe.api.LongFeEnergyCapability;
import com.non_coffee.mbd2thread.energy.fe.impl.ForgeEnergyAdapters;
import com.non_coffee.mbd2thread.energy.fe.impl.CopiableLongFeEnergyContainer;
import com.non_coffee.mbd2thread.integration.energy.FluxNetworksLongEnergyCompat;
import com.non_coffee.mbd2thread.integration.energy.MekanismStrictEnergyCompat;
import com.non_coffee.mbd2thread.energy.fe.impl.LongFeContainerAdapters;
import com.non_coffee.mbd2thread.energy.fe.recipe.LongFeRecipeCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public class LongFeEnergyCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(LongFeEnergyCapabilityTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Persisted
    @DescSynced
    public final CopiableLongFeEnergyContainer container;

    private final LongFeRecipeHandler recipeHandler = new LongFeRecipeHandler();
    private final ForgeEnergyRecipeHandler forgeEnergyRecipeHandler = new ForgeEnergyRecipeHandler();
    private final LongFeContainerCap longContainerCap = new LongFeContainerCap();
    private final ForgeEnergyCap forgeEnergyCap = new ForgeEnergyCap();
    private final FluxFnEnergyCap fluxFnEnergyCap = new FluxFnEnergyCap();
    private final MekStrictEnergyCap mekStrictEnergyCap = new MekStrictEnergyCap();

    public LongFeEnergyCapabilityTrait(MBDMachine machine, LongFeEnergyCapabilityTraitDefinition definition) {
        super(machine, definition);
        container = new CopiableLongFeEnergyContainer(
                definition.getCapacity(),
                definition.getMaxReceivePerTick(),
                definition.getMaxExtractPerTick()
        );
        container.setOnContentsChanged(this::notifyListeners);
    }

    @Override
    public LongFeEnergyCapabilityTraitDefinition getDefinition() {
        return (LongFeEnergyCapabilityTraitDefinition) super.getDefinition();
    }

    @Override
    public void onLoadingTraitInPreview() {
        container.setEnergyStored(getDefinition().getCapacity() / 2);
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler, forgeEnergyRecipeHandler);
    }

    @Override
    public List<ICapabilityProviderTrait<?>> getCapabilityProviderTraits() {
        var caps = new ArrayList<ICapabilityProviderTrait<?>>(4);
        caps.add(longContainerCap);
        caps.add(forgeEnergyCap);
        if (FluxNetworksLongEnergyCompat.getCapability().isPresent()) {
            caps.add(fluxFnEnergyCap);
        }
        if (MekanismStrictEnergyCompat.getCapability().isPresent()) {
            caps.add(mekStrictEnergyCap);
        }
        return List.copyOf(caps);
    }

    @Override
    public @Nullable AutoIO getAutoIO() {
        return getDefinition().getAutoIO().isEnable() ? getDefinition().getAutoIO() : null;
    }

    @Override
    public IO getCapabilityIO(@Nullable Direction side) {
        var autoIO = getAutoIO();
        if (autoIO != null) {
            var front = getMachine().getFrontFacing().orElse(Direction.NORTH);
            IO io = autoIO.getIO(front, side);
            if (io == IO.IN || io == IO.OUT) {
                return io;
            }
        }
        return super.getCapabilityIO(side);
    }

    @Override
    public void handleAutoIO(BlockPos port, Direction side, IO io) {
        var neighborPos = port.relative(side);
        var neighborSide = side.getOpposite();
        Optional.ofNullable(getMachine().getLevel().getBlockEntity(neighborPos)).ifPresent(be -> {
            if (io == IO.IN) {
                var longSource = be.getCapability(LongFeEnergyCapability.CAPABILITY, neighborSide).resolve();
                if (longSource.isPresent()) {
                    long extracted = longSource.get().extractEnergy(neighborSide, getDefinition().getMaxReceivePerTick(), true);
                    extracted = container.receiveEnergy(null, extracted, false);
                    longSource.get().extractEnergy(neighborSide, extracted, false);
                } else {
                    var fluxSource = FluxNetworksLongEnergyCompat.getStorage(be, neighborSide);
                    if (fluxSource.isPresent()) {
                        long extractedSim = FluxNetworksLongEnergyCompat.extract(fluxSource.get(), getDefinition().getMaxReceivePerTick(), true);
                        long canAccept = container.receiveEnergy(null, extractedSim, true);
                        if (canAccept > 0) {
                            long extracted = FluxNetworksLongEnergyCompat.extract(fluxSource.get(), canAccept, false);
                            container.receiveEnergy(null, extracted, false);
                        }
                    } else {
                        var mekSource = MekanismStrictEnergyCompat.getHandler(be, neighborSide);
                        if (mekSource.isPresent()) {
                            Object reqJ = MekanismStrictEnergyCompat.joulesFromFe(getDefinition().getMaxReceivePerTick());
                            Object extractedSimJ = MekanismStrictEnergyCompat.extractAmount(mekSource.get(), reqJ, true);
                            long available = MekanismStrictEnergyCompat.feFloorFromJoules(extractedSimJ);
                            long canAccept = container.receiveEnergy(null, available, true);
                            if (canAccept > 0) {
                                Object execJ = MekanismStrictEnergyCompat.joulesFromFe(canAccept);
                                Object extractedExecJ = MekanismStrictEnergyCompat.extractAmount(mekSource.get(), execJ, false);
                                long got = MekanismStrictEnergyCompat.feFloorFromJoules(extractedExecJ);
                                if (got > 0) {
                                    container.receiveEnergy(null, got, false);
                                }
                            }
                        } else {
                            be.getCapability(ForgeCapabilities.ENERGY, neighborSide).resolve().ifPresent(source -> {
                                int extracted = source.extractEnergy((int) Math.min(Integer.MAX_VALUE, getDefinition().getMaxReceivePerTick()), true);
                                long received = container.receiveEnergy(null, extracted, false);
                                source.extractEnergy((int) Math.min(Integer.MAX_VALUE, received), false);
                            });
                        }
                    }
                }
            }

            if (io == IO.OUT || io == IO.BOTH) {
                var longTarget = be.getCapability(LongFeEnergyCapability.CAPABILITY, neighborSide).resolve();
                if (longTarget.isPresent()) {
                    long accepted = longTarget.get().receiveEnergy(neighborSide, getDefinition().getMaxExtractPerTick(), true);
                    accepted = container.extractEnergy(null, accepted, false);
                    longTarget.get().receiveEnergy(neighborSide, accepted, false);
                } else {
                    var fluxTarget = FluxNetworksLongEnergyCompat.getStorage(be, neighborSide);
                    if (fluxTarget.isPresent()) {
                        long canExtract = container.extractEnergy(null, getDefinition().getMaxExtractPerTick(), true);
                        if (canExtract > 0) {
                            long acceptedSim = FluxNetworksLongEnergyCompat.receive(fluxTarget.get(), canExtract, true);
                            if (acceptedSim > 0) {
                                long toSend = container.extractEnergy(null, acceptedSim, true);
                                if (toSend > 0) {
                                    long inserted = FluxNetworksLongEnergyCompat.receive(fluxTarget.get(), toSend, false);
                                    if (inserted > 0) {
                                        container.extractEnergy(null, inserted, false);
                                    }
                                }
                            } else {
                                be.getCapability(ForgeCapabilities.ENERGY, neighborSide).resolve().ifPresent(target -> {
                                    int accepted = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, canExtract), true);
                                    long extracted = container.extractEnergy(null, accepted, false);
                                    target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, extracted), false);
                                });
                            }
                        }
                    } else {
                        var mekTarget = MekanismStrictEnergyCompat.getHandler(be, neighborSide);
                        if (mekTarget.isPresent()) {
                            long canExtract = container.extractEnergy(null, getDefinition().getMaxExtractPerTick(), true);
                            if (canExtract > 0) {
                                Object reqJ = MekanismStrictEnergyCompat.joulesFromFe(canExtract);
                                Object remainderSim = MekanismStrictEnergyCompat.insertRemainder(mekTarget.get(), reqJ, true);
                                Object insertedSimJ = MekanismStrictEnergyCompat.subtract(reqJ, remainderSim);
                                long canInsert = MekanismStrictEnergyCompat.feFloorFromJoules(insertedSimJ);
                                if (canInsert > 0) {
                                    Object execJ = MekanismStrictEnergyCompat.joulesFromFe(canInsert);
                                    Object remainderExec = MekanismStrictEnergyCompat.insertRemainder(mekTarget.get(), execJ, false);
                                    Object insertedExecJ = MekanismStrictEnergyCompat.subtract(execJ, remainderExec);
                                    long inserted = MekanismStrictEnergyCompat.feFloorFromJoules(insertedExecJ);
                                    if (inserted > 0) {
                                        container.extractEnergy(null, inserted, false);
                                    }
                                }
                            }
                        } else {
                            be.getCapability(ForgeCapabilities.ENERGY, neighborSide).resolve().ifPresent(target -> {
                                int accepted = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, getDefinition().getMaxExtractPerTick()), true);
                                long extracted = container.extractEnergy(null, accepted, false);
                                target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, extracted), false);
                            });
                        }
                    }
                }
            }
        });
    }

    public class LongFeRecipeHandler extends RecipeHandlerTrait<Long> {
        protected LongFeRecipeHandler() {
            super(LongFeEnergyCapabilityTrait.this, LongFeRecipeCapability.CAP);
        }

        @Override
        public List<Long> handleRecipeInner(IO io, MBDRecipe recipe, List<Long> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            long required = left.stream().reduce(0L, Long::sum);
            if (required <= 0) return null;

            var energy = simulate ? container.copy() : container;
            if (io == IO.IN) {
                long taken = Math.min(energy.getEnergyStored(), required);
                if (!simulate) {
                    energy.changeEnergy(-taken);
                }
                required -= taken;
            } else if (io == IO.OUT) {
                long space = energy.getEnergyCapacity() - energy.getEnergyStored();
                long given = Math.min(space, required);
                if (!simulate) {
                    energy.changeEnergy(given);
                }
                required -= given;
            }

            return required > 0 ? List.of(required) : null;
        }
    }

    public class ForgeEnergyRecipeHandler extends RecipeHandlerTrait<Integer> {
        protected ForgeEnergyRecipeHandler() {
            super(LongFeEnergyCapabilityTrait.this, ForgeEnergyRecipeCapability.CAP);
        }

        @Override
        public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            int required = left.stream().reduce(0, Integer::sum);
            if (required <= 0) return null;

            var energy = simulate ? container.copy() : container;
            if (io == IO.IN) {
                long taken = Math.min(energy.getEnergyStored(), (long) required);
                if (!simulate) {
                    energy.changeEnergy(-taken);
                }
                required -= (int) taken;
            } else if (io == IO.OUT) {
                long space = energy.getEnergyCapacity() - energy.getEnergyStored();
                long given = Math.min(space, (long) required);
                if (!simulate) {
                    energy.changeEnergy(given);
                }
                required -= (int) given;
            }

            return required > 0 ? List.of(required) : null;
        }
    }

    public class LongFeContainerCap implements ICapabilityProviderTrait<ILongFeEnergyContainer> {
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        @Override
        public Capability<ILongFeEnergyContainer> getCapability() {
            return LongFeEnergyCapability.CAPABILITY;
        }

        @Override
        public ILongFeEnergyContainer getCapContent(IO capbilityIO) {
            return new LongFeContainerAdapters.Wrapper(container, capbilityIO);
        }

        @Override
        public ILongFeEnergyContainer mergeContents(List<ILongFeEnergyContainer> contents) {
            return new LongFeContainerAdapters.ListView(contents);
        }
    }

    public class ForgeEnergyCap implements ICapabilityProviderTrait<IEnergyStorage> {
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        @Override
        public Capability<IEnergyStorage> getCapability() {
            return ForgeCapabilities.ENERGY;
        }

        @Override
        public IEnergyStorage getCapContent(IO capbilityIO) {
            return new ForgeEnergyAdapters.Wrapper(container, capbilityIO);
        }

        @Override
        public IEnergyStorage mergeContents(List<IEnergyStorage> contents) {
            return new ForgeEnergyAdapters.Combined(contents);
        }
    }

    public class FluxFnEnergyCap implements ICapabilityProviderTrait<Object> {
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        @Override
        public Capability<Object> getCapability() {
            return (Capability<Object>) FluxNetworksLongEnergyCompat.getCapability().orElse(null);
        }

        @Override
        public Object getCapContent(IO capbilityIO) {
            ILongFeEnergyContainer wrapped = new LongFeContainerAdapters.Wrapper(container, capbilityIO);
            return FluxNetworksLongEnergyCompat.createStorageProxy(wrapped, new FluxNetworksLongEnergyCompat.FluxLongAccess() {
                @Override
                public long getEnergyStored(Object container) {
                    return ((ILongFeEnergyContainer) container).getEnergyStored();
                }

                @Override
                public long getEnergyCapacity(Object container) {
                    return ((ILongFeEnergyContainer) container).getEnergyCapacity();
                }

                @Override
                public boolean canReceive(Object container) {
                    return ((ILongFeEnergyContainer) container).canReceive(null);
                }

                @Override
                public boolean canExtract(Object container) {
                    return ((ILongFeEnergyContainer) container).canExtract(null);
                }

                @Override
                public long receive(Object container, long amount, boolean simulate) {
                    return ((ILongFeEnergyContainer) container).receiveEnergy(null, amount, simulate);
                }

                @Override
                public long extract(Object container, long amount, boolean simulate) {
                    return ((ILongFeEnergyContainer) container).extractEnergy(null, amount, simulate);
                }
            });
        }
    }

    public class MekStrictEnergyCap implements ICapabilityProviderTrait<Object> {
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            if (side == null) return IO.NONE;
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        @Override
        public Capability<Object> getCapability() {
            return (Capability<Object>) MekanismStrictEnergyCompat.getCapability().orElse(null);
        }

        @Override
        public Object getCapContent(IO capbilityIO) {
            ILongFeEnergyContainer wrapped = new LongFeContainerAdapters.Wrapper(container, capbilityIO);
            return MekanismStrictEnergyCompat.createStrictHandlerProxy(wrapped, new MekanismStrictEnergyCompat.StrictLongAccess() {
                @Override
                public long getEnergyStored(Object container) {
                    return ((ILongFeEnergyContainer) container).getEnergyStored();
                }

                @Override
                public long getEnergyCapacity(Object container) {
                    return ((ILongFeEnergyContainer) container).getEnergyCapacity();
                }

                @Override
                public void setEnergyStored(Object container, long energy) {
                    ((ILongFeEnergyContainer) container).setEnergyStored(energy);
                }

                @Override
                public boolean canReceive(Object container) {
                    return ((ILongFeEnergyContainer) container).canReceive(null);
                }

                @Override
                public boolean canExtract(Object container) {
                    return ((ILongFeEnergyContainer) container).canExtract(null);
                }

                @Override
                public long receive(Object container, long amount, boolean simulate) {
                    return ((ILongFeEnergyContainer) container).receiveEnergy(null, amount, simulate);
                }

                @Override
                public long extract(Object container, long amount, boolean simulate) {
                    return ((ILongFeEnergyContainer) container).extractEnergy(null, amount, simulate);
                }
            });
        }
    }
}
