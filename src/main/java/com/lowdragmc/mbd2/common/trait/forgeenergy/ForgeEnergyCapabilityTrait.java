package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.*;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Runtime trait that gives a machine Forge Energy storage, FE recipe handling,
 * Forge energy capability access, and optional automatic energy transfer.
 *
 * <p>The business goal is to use one configured FE buffer for recipe input or
 * output, side-aware Forge energy capability exposure, neighboring block energy
 * transfer, GUI display, and renderer state. The storage is mutable and not
 * thread-safe; all mutation should run on the owning machine's logical server
 * thread, while preview initialization runs on the editor/client thread.</p>
 */
@Getter
public class ForgeEnergyCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ForgeEnergyCapabilityTrait.class);

    /**
     * Returns the sync-data field holder for Forge Energy traits.
     *
     * @return static managed field holder used by LowDragLib sync/persistence
     */
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Persisted
    @DescSynced
    public final CopiableEnergyStorage storage;
    private final ForgeEnergyRecipeHandler recipeHandler = new ForgeEnergyRecipeHandler();
    private final EnergyStorageCap energyStorageCap = new EnergyStorageCap();

    /**
     * Creates FE storage and binds it to a machine.
     *
     * <p>Side effects: allocates configured storage and registers a content-change
     * callback that notifies recipe listeners.</p>
     *
     * @param machine    owning machine
     * @param definition FE definition controlling capacity, transfer limits,
     *                   UI, rendering, and auto IO
     */
    public ForgeEnergyCapabilityTrait(MBDMachine machine, ForgeEnergyCapabilityTraitDefinition definition) {
        super(machine, definition);
        storage = createStorages();
        storage.setOnContentsChanged(this::notifyListeners);
    }

    /**
     * Returns this trait's concrete definition type.
     *
     * @return Forge Energy capability definition
     */
    @Override
    public ForgeEnergyCapabilityTraitDefinition getDefinition() {
        return (ForgeEnergyCapabilityTraitDefinition) super.getDefinition();
    }

    /**
     * Seeds preview storage with representative energy.
     *
     * <p>Editor-only side effect: fills the buffer to half capacity so the UI and
     * renderer preview show visible progress.</p>
     */
    @Override
    public void onLoadingTraitInPreview() {
        storage.receiveEnergy(getDefinition().getCapacity() / 2, false);
    }

    /**
     * Creates the backing FE storage for this trait.
     *
     * @return new empty storage using configured capacity
     */
    protected CopiableEnergyStorage createStorages() {
        return new CopiableEnergyStorage(getDefinition().getCapacity());
    }

    /**
     * Returns recipe handlers contributed by this energy buffer.
     *
     * @return Forge Energy recipe handler
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

    /**
     * Returns Forge capabilities contributed by this energy buffer.
     *
     * @return energy storage capability provider
     */
    @Override
    public List<ICapabilityProviderTrait<?>> getCapabilityProviderTraits() {
        return List.of(energyStorageCap);
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
     * Performs one neighboring block energy transfer pass.
     *
     * <p>Side effects: for input IO, simulates extraction from the neighbor,
     * receives what this storage can accept, then extracts the accepted amount
     * from the neighbor. For output IO, simulates neighbor receiving, extracts
     * what the neighbor can accept from this storage, then sends it. Calls are
     * expected on the logical server thread.</p>
     *
     * @param port position whose neighbor is accessed
     * @param side side of {@code port} used for transfer
     * @param io   transfer direction to perform
     */
    @Override
    public void handleAutoIO(BlockPos port, Direction side, IO io) {
        if (io.support(IO.IN)) {
            Optional.ofNullable(getMachine().getLevel().getBlockEntity(port.relative(side)))
                    .flatMap(be -> be.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve())
                    .ifPresent(source -> source.extractEnergy(
                            storage.receiveEnergy(source.extractEnergy(getDefinition().getMaxReceive(), true),
                                    false),
                            false));
        }
        if (io.support(IO.OUT)) {
            Optional.ofNullable(getMachine().getLevel().getBlockEntity(port.relative(side)))
                    .flatMap(be -> be.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve())
                    .ifPresent(target -> target.receiveEnergy(
                            storage.extractEnergy(target.receiveEnergy(getDefinition().getMaxExtract(), true),
                                    false),
                            false));
        }
    }

    /**
     * Recipe handler that consumes or produces Forge Energy amounts.
     *
     * <p>Business goal: match integer FE recipe content against this trait's
     * storage. Simulation uses a copied storage; commit mode mutates the real
     * storage and records consumed input energy through
     * {@link RecipeConsumptionTracker}.</p>
     */
    public class ForgeEnergyRecipeHandler extends RecipeHandlerTrait<Integer> {
        /**
         * Creates the FE recipe handler bound to the outer trait.
         */
        protected ForgeEnergyRecipeHandler() {
            super(ForgeEnergyCapabilityTrait.this, ForgeEnergyRecipeCapability.CAP);
        }

        /**
         * Matches or commits FE recipe content.
         *
         * <p>All integer entries in {@code left} are summed into one required
         * amount. For {@link IO#IN}, matching extracts FE from storage. For output
         * IO, matching receives FE into storage. Returning {@code null} marks all
         * content satisfied.</p>
         *
         * @param io       recipe IO direction
         * @param recipe   recipe being matched or committed
         * @param left     integer FE amounts still unsatisfied
         * @param slotName optional recipe slot name, passed to consumption
         *                 tracking
         * @param simulate {@code true} to check using copied storage;
         *                 {@code false} to mutate real storage
         * @return one-entry list containing the remaining FE amount, or
         * {@code null} when complete
         */
        @Override
        public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            int required = left.stream().reduce(0, Integer::sum);
            var capability = simulate ? storage.copy() : storage;
            if (io == IO.IN) {
                var extracted = capability.extractEnergy(required, simulate);
                if (!simulate && extracted > 0) {
                    RecipeConsumptionTracker.record(ForgeEnergyRecipeCapability.CAP, extracted, slotName);
                }
                required -= extracted;
            } else {
                var received = capability.receiveEnergy(required, simulate);
                required -= received;
            }
            return required > 0 ? List.of(required) : null;
        }
    }

    /**
     * Forge capability provider for this trait's FE storage.
     */
    public class EnergyStorageCap implements ICapabilityProviderTrait<IEnergyStorage> {
        /**
         * Resolves energy capability IO for a queried side.
         *
         * @param side queried side, or {@code null} for internal access
         * @return effective energy IO
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return ForgeEnergyCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the Forge energy capability token.
         *
         * @return energy capability
         */
        @Override
        public Capability<IEnergyStorage> getCapability() {
            return ForgeCapabilities.ENERGY;
        }

        /**
         * Creates a side-filtered energy storage wrapper.
         *
         * @param capbilityIO effective IO for the queried side
         * @return wrapper over this trait's storage
         */
        @Override
        public IEnergyStorage getCapContent(IO capbilityIO) {
            return new EnergyStorageWrapper(storage, capbilityIO, getDefinition().getMaxReceive(), getDefinition().getMaxExtract());
        }

        /**
         * Merges multiple energy providers into one combined storage view.
         *
         * @param contents energy storages collected from compatible traits
         * @return combined energy storage list
         */
        @Override
        public IEnergyStorage mergeContents(List<IEnergyStorage> contents) {
            return new EnergyStorageList(contents.toArray(new IEnergyStorage[0]));
        }
    }
}
