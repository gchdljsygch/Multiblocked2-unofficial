package com.lowdragmc.mbd2.common.trait.forgeenergy;

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
import com.lowdragmc.mbd2.api.capability.energy.ILongFeEnergyContainer;
import com.lowdragmc.mbd2.api.capability.energy.LongFeEnergyCapability;
import com.lowdragmc.mbd2.common.trait.forgeenergy.ForgeEnergyAdapters;
import com.lowdragmc.mbd2.common.trait.forgeenergy.CopiableLongFeEnergyContainer;
import com.lowdragmc.mbd2.integration.fluxnetworks.FluxNetworksLongEnergyCompat;
import com.lowdragmc.mbd2.integration.mekanism.MekanismStrictEnergyCompat;
import com.lowdragmc.mbd2.common.trait.forgeenergy.LongFeContainerAdapters;
import com.lowdragmc.mbd2.common.capability.recipe.LongFeRecipeCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Runtime trait that stores Forge Energy amounts with {@code long} precision and
 * exposes that storage through several energy capability APIs.
 *
 * <p>The business goal is to support machines whose energy capacity or transfer
 * rates exceed Forge's {@code int}-based {@link IEnergyStorage} limits while
 * still remaining compatible with Forge Energy, Flux Networks, Mekanism strict
 * energy handlers, and MBD's own {@link ILongFeEnergyContainer}. The container is
 * mutable and not thread-safe; all mutation should run on the owning machine's
 * logical server thread, while preview initialization runs on the editor/client
 * thread.</p>
 */
public class LongFeEnergyCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(LongFeEnergyCapabilityTrait.class);

    /**
     * Returns the sync-data field holder for long FE traits.
     *
     * @return static managed field holder used by LowDragLib sync/persistence
     */
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

    /**
     * Creates a long FE container and binds it to a machine.
     *
     * <p>Side effects: allocates the configured container and registers a
     * content-change callback that notifies recipe listeners.</p>
     *
     * @param machine    owning machine
     * @param definition long FE definition controlling capacity, transfer limits,
     *                   UI, and auto IO
     */
    public LongFeEnergyCapabilityTrait(MBDMachine machine, LongFeEnergyCapabilityTraitDefinition definition) {
        super(machine, definition);
        container = new CopiableLongFeEnergyContainer(
                definition.getCapacity(),
                definition.getMaxReceivePerTick(),
                definition.getMaxExtractPerTick()
        );
        container.setOnContentsChanged(this::notifyListeners);
    }

    /**
     * Returns this trait's concrete definition type.
     *
     * @return long FE capability definition
     */
    @Override
    public LongFeEnergyCapabilityTraitDefinition getDefinition() {
        return (LongFeEnergyCapabilityTraitDefinition) super.getDefinition();
    }

    /**
     * Seeds preview storage with representative energy.
     *
     * <p>Editor-only side effect: fills the container to half capacity so the UI
     * and renderer preview show visible progress.</p>
     */
    @Override
    public void onLoadingTraitInPreview() {
        container.setEnergyStored(getDefinition().getCapacity() / 2);
    }

    /**
     * Returns recipe handlers contributed by this energy container.
     *
     * <p>Both the native long-FE capability and compatibility Forge Energy
     * integer capability are exposed so recipes can choose either content type.</p>
     *
     * @return long FE and Forge Energy recipe handlers
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler, forgeEnergyRecipeHandler);
    }

    /**
     * Returns capability providers contributed by this energy container.
     *
     * <p>Flux Networks and Mekanism providers are included only when their
     * optional capabilities are present in the current mod environment.</p>
     *
     * @return immutable list of compatible capability providers
     */
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

    /**
     * Returns enabled side-based automatic energy IO configuration.
     *
     * @return auto IO configuration, or {@code null} when side auto IO is
     * disabled
     */
    @Override
    public @Nullable AutoIO getAutoIO() {
        return getDefinition().getAutoIO().isEnable() ? getDefinition().getAutoIO() : null;
    }

    /**
     * Resolves capability IO for a queried side.
     *
     * <p>When auto IO is enabled, explicit input or output side rules override
     * the normal capability IO definition. {@link IO#BOTH} and {@link IO#NONE}
     * from auto IO fall back to the normal side mapping so auto transfer settings
     * do not accidentally hide internal/bidirectional capability access.</p>
     *
     * @param side queried world side, or {@code null} for internal/unsided access
     * @return effective IO for the query
     */
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

    /**
     * Performs one neighboring block energy transfer pass.
     *
     * <p>Side effects: reads the neighbor block entity and transfers energy using
     * the best available API in this order: MBD long FE, Flux Networks long
     * storage, Mekanism strict energy, then Forge Energy. Long APIs preserve
     * {@code long} precision; Forge fallback is capped to {@link Integer#MAX_VALUE}
     * per call. Calls are expected on the logical server thread.</p>
     *
     * @param port position whose neighbor is accessed
     * @param side side of {@code port} used for transfer
     * @param io   transfer direction to perform
     */
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

    /**
     * Recipe handler for native long FE recipe content.
     *
     * <p>Business goal: match large energy amounts without integer truncation.
     * Simulation uses a copied container; commit mode mutates the real container.</p>
     */
    public class LongFeRecipeHandler extends RecipeHandlerTrait<Long> {
        /**
         * Creates the long FE recipe handler bound to the outer trait.
         */
        protected LongFeRecipeHandler() {
            super(LongFeEnergyCapabilityTrait.this, LongFeRecipeCapability.CAP);
        }

        /**
         * Matches or commits long FE recipe content.
         *
         * <p>All long entries in {@code left} are summed into one required amount.
         * For {@link IO#IN}, matching removes energy from the container. For
         * {@link IO#OUT}, matching inserts energy into available capacity.
         * Returning {@code null} marks all content satisfied.</p>
         *
         * @param io       recipe IO direction
         * @param recipe   recipe being matched or committed
         * @param left     long FE amounts still unsatisfied
         * @param slotName optional recipe slot name; currently unused by this
         *                 handler
         * @param simulate {@code true} to check using copied storage;
         *                 {@code false} to mutate real storage
         * @return one-entry list containing the remaining FE amount, or
         * {@code null} when complete
         */
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

    /**
     * Compatibility recipe handler for int-based Forge Energy recipe content.
     *
     * <p>Business goal: let existing FE recipes use this long-capacity storage
     * without requiring recipe definitions to switch to the long FE capability.</p>
     */
    public class ForgeEnergyRecipeHandler extends RecipeHandlerTrait<Integer> {
        /**
         * Creates the Forge Energy compatibility recipe handler.
         */
        protected ForgeEnergyRecipeHandler() {
            super(LongFeEnergyCapabilityTrait.this, ForgeEnergyRecipeCapability.CAP);
        }

        /**
         * Matches or commits int FE recipe content against the long container.
         *
         * @param io       recipe IO direction
         * @param recipe   recipe being matched or committed
         * @param left     integer FE amounts still unsatisfied
         * @param slotName optional recipe slot name; currently unused by this
         *                 handler
         * @param simulate {@code true} to check using copied storage;
         *                 {@code false} to mutate real storage
         * @return one-entry list containing the remaining FE amount, or
         * {@code null} when complete
         */
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

    /**
     * Capability provider for MBD's native long FE capability.
     */
    public class LongFeContainerCap implements ICapabilityProviderTrait<ILongFeEnergyContainer> {
        /**
         * Resolves long FE capability IO for a queried side.
         *
         * @param side queried side, or {@code null} for internal access
         * @return effective long FE IO
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the native long FE capability token.
         *
         * @return long FE capability
         */
        @Override
        public Capability<ILongFeEnergyContainer> getCapability() {
            return LongFeEnergyCapability.CAPABILITY;
        }

        /**
         * Creates a side-filtered long FE container wrapper.
         *
         * @param capbilityIO effective IO for the queried side
         * @return wrapper over this trait's container
         */
        @Override
        public ILongFeEnergyContainer getCapContent(IO capbilityIO) {
            return new LongFeContainerAdapters.Wrapper(container, capbilityIO);
        }

        /**
         * Merges multiple long FE providers into one combined view.
         *
         * @param contents long FE containers collected from compatible traits
         * @return combined long FE view
         */
        @Override
        public ILongFeEnergyContainer mergeContents(List<ILongFeEnergyContainer> contents) {
            return new LongFeContainerAdapters.ListView(contents);
        }
    }

    /**
     * Forge Energy compatibility provider for the long FE container.
     */
    public class ForgeEnergyCap implements ICapabilityProviderTrait<IEnergyStorage> {
        /**
         * Resolves Forge Energy capability IO for a queried side.
         *
         * @param side queried side, or {@code null} for internal access
         * @return effective Forge Energy IO
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the Forge Energy capability token.
         *
         * @return Forge Energy capability
         */
        @Override
        public Capability<IEnergyStorage> getCapability() {
            return ForgeCapabilities.ENERGY;
        }

        /**
         * Creates an int-limited Forge Energy wrapper over the long container.
         *
         * @param capbilityIO effective IO for the queried side
         * @return Forge Energy adapter
         */
        @Override
        public IEnergyStorage getCapContent(IO capbilityIO) {
            return new ForgeEnergyAdapters.Wrapper(container, capbilityIO);
        }

        /**
         * Merges multiple Forge Energy views into one combined adapter.
         *
         * @param contents Forge Energy storages collected from compatible traits
         * @return combined Forge Energy adapter
         */
        @Override
        public IEnergyStorage mergeContents(List<IEnergyStorage> contents) {
            return new ForgeEnergyAdapters.Combined(contents);
        }
    }

    /**
     * Optional Flux Networks long-energy capability provider.
     */
    public class FluxFnEnergyCap implements ICapabilityProviderTrait<Object> {
        /**
         * Resolves Flux capability IO for a queried side.
         *
         * @param side queried side, or {@code null} for internal access
         * @return effective energy IO
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the optional Flux Networks capability token.
         *
         * @return Flux capability, or {@code null} when unavailable
         */
        @Override
        public Capability<Object> getCapability() {
            return (Capability<Object>) FluxNetworksLongEnergyCompat.getCapability().orElse(null);
        }

        /**
         * Creates a Flux Networks proxy over the long FE container.
         *
         * @param capbilityIO effective IO for the queried side
         * @return Flux-compatible storage proxy
         */
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

    /**
     * Optional Mekanism strict-energy capability provider.
     */
    public class MekStrictEnergyCap implements ICapabilityProviderTrait<Object> {
        /**
         * Resolves Mekanism capability IO for a queried side.
         *
         * <p>Mekanism strict handlers are side-sensitive; unsided queries are
         * rejected with {@link IO#NONE}.</p>
         *
         * @param side queried side, or {@code null}
         * @return effective energy IO, or {@link IO#NONE} for unsided access
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            if (side == null) return IO.NONE;
            return LongFeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the optional Mekanism strict-energy capability token.
         *
         * @return Mekanism capability, or {@code null} when unavailable
         */
        @Override
        public Capability<Object> getCapability() {
            return (Capability<Object>) MekanismStrictEnergyCompat.getCapability().orElse(null);
        }

        /**
         * Creates a Mekanism strict-energy proxy over the long FE container.
         *
         * @param capbilityIO effective IO for the queried side
         * @return Mekanism-compatible strict energy handler proxy
         */
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
