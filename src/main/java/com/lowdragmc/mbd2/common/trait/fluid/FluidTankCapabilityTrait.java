package com.lowdragmc.mbd2.common.trait.fluid;

import com.google.common.base.Predicates;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.fluid.*;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.mbd2.common.capability.recipe.FluidRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.*;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Runtime trait that gives a machine fluid storage, fluid recipe handling, Forge
 * fluid-handler capability access, and optional automatic fluid transfer.
 *
 * <p>The business goal is to use one configured fluid tank definition for editor
 * tanks, recipe input/output matching, side-aware Forge fluid IO, neighboring
 * block transfer, and world source-fluid pickup or placement. The trait owns
 * mutable tank state and is not thread-safe; all mutation should run on the
 * owning machine's logical server thread, while preview initialization runs on
 * the editor/client thread.</p>
 */
public class FluidTankCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(FluidTankCapabilityTrait.class);

    /**
     * Returns the sync-data field holder for fluid tank traits.
     *
     * @return static managed field holder used by LowDragLib sync/persistence
     */
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Persisted
    @DescSynced
    public final FluidStorage[] storages;
    @Setter
    protected boolean allowSameFluids; // Can different tanks be filled with the same fluid. It should be determined while creating tanks.
    private Boolean isEmpty;
    private final FluidRecipeHandler recipeHandler = new FluidRecipeHandler();
    private final FluidHandlerCap fluidHandlerCap = new FluidHandlerCap();

    /**
     * Creates fluid storages and binds them to a machine.
     *
     * <p>Side effects: allocates configured tanks, applies optional fluid
     * validators, and registers content-change callbacks that invalidate the
     * empty cache and notify recipe listeners.</p>
     *
     * @param machine    owning machine
     * @param definition fluid tank definition controlling size, capacity, filters,
     *                   UI, and auto IO
     */
    public FluidTankCapabilityTrait(MBDMachine machine, FluidTankCapabilityTraitDefinition definition) {
        super(machine, definition);
        storages = createStorages();
    }

    /**
     * Returns this trait's concrete definition type.
     *
     * @return fluid tank capability definition
     */
    @Override
    public FluidTankCapabilityTraitDefinition getDefinition() {
        return (FluidTankCapabilityTraitDefinition) super.getDefinition();
    }

    /**
     * Seeds preview storage with representative water.
     *
     * <p>Editor-only side effect: fills the first tank with half capacity or one
     * unit so the preview UI and renderer have visible content.</p>
     */
    @Override
    public void onLoadingTraitInPreview() {
        if (storages.length > 0) {
            storages[0].setFluid(FluidStack.create(Fluids.WATER, Math.max(getDefinition().getCapacity() / 2, 1)));
        }
    }

    /**
     * Creates backing fluid storages for this trait.
     *
     * <p>Side effects: applies the configured capacity, change callback, and
     * optional fluid filter to every tank.</p>
     *
     * @return new array of empty fluid storages
     */
    protected FluidStorage[] createStorages() {
        var storages = new FluidStorage[getDefinition().getTankSize()];
        for (int i = 0; i < storages.length; i++) {
            storages[i] = new FluidStorage(getDefinition().getCapacity());
            storages[i].setOnContentsChanged(this::onContentsChanged);
            if (getDefinition().getFluidFilterSettings().isEnable()) {
                storages[i].setValidator(getDefinition().getFluidFilterSettings());
            }
        }
        return storages;
    }

    /**
     * Handles tank mutations.
     *
     * <p>Side effects: invalidates the cached empty-state value and notifies
     * recipe listeners so active recipe logic can re-check fluid availability.</p>
     */
    public void onContentsChanged() {
        isEmpty = null;
        notifyListeners();
    }

    /**
     * Returns whether all tanks are empty.
     *
     * <p>Side effects: lazily computes and caches the answer until
     * {@link #onContentsChanged()} invalidates it.</p>
     *
     * @return {@code true} when every tank contains an empty fluid stack
     */
    public boolean isEmpty() {
        if (isEmpty == null) {
            isEmpty = true;
            for (FluidStorage storage : storages) {
                if (!storage.getFluid().isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
        }
        return isEmpty;
    }

    /**
     * Performs side auto IO and world source-fluid pickup/placement automation.
     *
     * <p>Server-side side effects include moving fluids through neighboring fluid
     * handlers, removing source liquid blocks when fully accepted, destroying
     * replaceable blocks before placing fluid outputs, and draining tanks by one
     * bucket per placed block. Work is throttled by configured intervals and the
     * machine offset timer.</p>
     */
    @Override
    public void serverTick() {
        IAutoIOTrait.super.serverTick();
        var timer = getMachine().getOffsetTimer();
        var autoInput = getDefinition().getAutoInput();
        var autoOutput = getDefinition().getAutoOutput();
        var level = getMachine().getLevel();
        if (autoInput.isEnable() && timer % autoInput.getInterval() == 0) {
            var leftBlocks = autoInput.getSpeed();
            var range = autoOutput.getRotatedRange(getMachine().getFrontFacing().orElse(Direction.NORTH)).move(getMachine().getPos());
            for (int x = (int) Math.round(range.minX); x < (int) Math.round(range.maxX); x++) {
                if (leftBlocks <= 0) break;
                for (int y = (int) Math.round(range.minY); y < (int) Math.round(range.maxY); y++) {
                    if (leftBlocks <= 0) break;
                    for (int z = (int) Math.round(range.minZ); z < (int) Math.round(range.maxZ); z++) {
                        if (leftBlocks <= 0) break;
                        var pos = new BlockPos(x, y, z);
                        var state = level.getBlockState(pos);
                        var block = state.getBlock();
                        if (block instanceof LiquidBlock liquidBlock && state.getFluidState().isSource()) {
                            var toFilled = FluidStack.create(liquidBlock.getFluid().getSource(), 1000);
                            for (FluidStorage storage : storages) {
                                if (storage.fill(toFilled, true) == 1000) {
                                    storage.fill(toFilled, false);
                                    leftBlocks--;
                                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (autoOutput.isEnable() && timer % autoOutput.getInterval() == 0) {
            var leftBlocks = autoOutput.getSpeed();
            var range = autoOutput.getRotatedRange(getMachine().getFrontFacing().orElse(Direction.NORTH)).move(getMachine().getPos());

            for (int x = (int) Math.round(range.minX); x < (int) Math.round(range.maxX); x++) {
                if (leftBlocks <= 0 || isEmpty()) break;
                for (int y = (int) Math.round(range.minY); y < (int) Math.round(range.maxY); y++) {
                    if (leftBlocks <= 0 || isEmpty()) break;
                    for (int z = (int) Math.round(range.minZ); z < (int) Math.round(range.maxZ); z++) {
                        if (leftBlocks <= 0 || isEmpty()) break;
                        var pos = new BlockPos(x, y, z);
                        var state = level.getBlockState(pos);
                        for (FluidStorage storage : storages) {
                            var drained = storage.drain(1000, true);
                            if (drained.getAmount() == 1000 && drained.getFluid().getFluidType().canBePlacedInLevel(level, pos, FluidHelperImpl.toFluidStack(drained))) {
                                if (!(state.getFluidState().isSource()) && state.canBeReplaced(drained.getFluid())) {
                                    if (!level.isClientSide) {
                                        level.destroyBlock(pos, true);
                                    }
                                    level.setBlockAndUpdate(pos, drained.getFluid().defaultFluidState().createLegacyBlock());
                                    leftBlocks--;
                                    storage.drain(1000, false);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns recipe handlers contributed by this fluid storage.
     *
     * @return fluid recipe handler
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

    /**
     * Returns Forge capabilities contributed by this fluid storage.
     *
     * @return fluid handler capability provider
     */
    @Override
    public List<ICapabilityProviderTrait<?>> getCapabilityProviderTraits() {
        return List.of(fluidHandlerCap);
    }

    /**
     * Returns enabled side-based automatic fluid IO configuration.
     *
     * @return auto IO configuration, or {@code null} when side auto IO is
     * disabled
     */
    @Override
    public @Nullable AutoIO getAutoIO() {
        return getDefinition().getAutoIO().isEnable() ? getDefinition().getAutoIO() : null;
    }

    /**
     * Performs one neighboring block fluid transfer pass.
     *
     * <p>Side effects: for input IO, imports fluids from the neighbor into this
     * storage using the configured fluid filter; for output IO, exports from this
     * storage to the neighbor. Calls are expected on the logical server thread.</p>
     *
     * @param port position whose neighbor is accessed
     * @param side side of {@code port} used for transfer
     * @param io   transfer direction to perform
     */
    @Override
    public void handleAutoIO(BlockPos port, Direction side, IO io) {
        if (io.support(IO.IN)) {
            FluidTransferHelper.importToTarget(new FluidTransferList(storages), Integer.MAX_VALUE,
                    getDefinition().getFluidFilterSettings().isEnable() ? getDefinition().getFluidFilterSettings() : Predicates.alwaysTrue(),
                    getMachine().getLevel(), port.relative(side), side.getOpposite());
        }
        if (io.support(IO.OUT)) {
            FluidTransferHelper.exportToTarget(new FluidTransferList(storages), Integer.MAX_VALUE, Predicates.alwaysTrue(),
                    getMachine().getLevel(), port.relative(side), side.getOpposite());
        }
    }

    /**
     * Recipe handler that consumes or produces fluid ingredients.
     *
     * <p>Business goal: match fluid recipe content against this trait's tanks.
     * Simulation uses copied tanks; commit mode mutates the real tanks and records
     * consumed input fluids through {@link RecipeConsumptionTracker}.</p>
     */
    public class FluidRecipeHandler extends RecipeHandlerTrait<FluidIngredient> {
        /**
         * Creates the fluid recipe handler bound to the outer trait.
         */
        protected FluidRecipeHandler() {
            super(FluidTankCapabilityTrait.this, FluidRecipeCapability.CAP);
        }

        /**
         * Matches or commits fluid recipe content.
         *
         * <p>For {@link IO#IN}, matching drains accepted fluids from tanks. For
         * {@link IO#OUT}, matching fills tanks with the first concrete stack
         * exposed by the ingredient. Returning {@code null} marks all content
         * satisfied.</p>
         *
         * @param io       recipe IO direction
         * @param recipe   recipe being matched or committed
         * @param left     mutable fluid ingredient list still unsatisfied
         * @param slotName optional recipe slot name, passed to consumption
         *                 tracking
         * @param simulate {@code true} to check using copied tanks; {@code false}
         *                 to mutate real tanks
         * @return remaining unsatisfied ingredients, or {@code null} when
         * complete
         */
        @Override
        public List<FluidIngredient> handleRecipeInner(IO io, MBDRecipe recipe, List<FluidIngredient> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            var capabilities = simulate ? Arrays.stream(storages).map(FluidStorage::copy).toArray(FluidStorage[]::new) : storages;
            for (FluidStorage capability : capabilities) {
                Iterator<FluidIngredient> iterator = left.iterator();
                if (io == IO.IN) {
                    while (iterator.hasNext()) {
                        FluidIngredient fluidStack = iterator.next();
                        if (fluidStack.isEmpty()) {
                            iterator.remove();
                            continue;
                        }
                        boolean found = false;
                        FluidStack foundStack = null;
                        for (int i = 0; i < capability.getTanks(); i++) {
                            FluidStack stored = capability.getFluidInTank(i);
                            if (!fluidStack.test(stored)) {
                                continue;
                            }
                            found = true;
                            foundStack = stored;
                        }
                        if (!found) continue;
                        FluidStack drained = capability.drain(foundStack.copy(fluidStack.getAmount()), false);
                        if (!simulate && !drained.isEmpty()) {
                            RecipeConsumptionTracker.record(FluidRecipeCapability.CAP, drained.copy(), slotName);
                        }

                        fluidStack.setAmount(fluidStack.getAmount() - drained.getAmount());
                        if (fluidStack.getAmount() <= 0) {
                            iterator.remove();
                        }
                    }
                } else if (io == IO.OUT) {
                    while (iterator.hasNext()) {
                        FluidIngredient fluidStack = iterator.next();
                        if (fluidStack.isEmpty()) {
                            iterator.remove();
                            continue;
                        }
                        var fluids = fluidStack.getStacks();
                        if (fluids.length == 0) {
                            iterator.remove();
                            continue;
                        }
                        FluidStack output = fluids[0];
                        long filled = capability.fill(output.copy(), false);
                        if (!fluidStack.isEmpty()) {
                            fluidStack.setAmount(fluidStack.getAmount() - filled);
                        }
                        if (fluidStack.getAmount() <= 0) {
                            iterator.remove();
                        }
                    }
                }
                if (left.isEmpty()) break;
            }
            return left.isEmpty() ? null : left;
        }
    }

    /**
     * Forge capability provider for this trait's fluid storage.
     */
    public class FluidHandlerCap implements ICapabilityProviderTrait<IFluidHandler> {
        /**
         * Resolves fluid capability IO for a queried side.
         *
         * @param side queried side, or {@code null} for internal access
         * @return effective fluid handler IO
         */
        @Override
        public IO getCapabilityIO(@Nullable Direction side) {
            return FluidTankCapabilityTrait.this.getCapabilityIO(side);
        }

        /**
         * Returns the Forge fluid handler capability token.
         *
         * @return fluid handler capability
         */
        @Override
        public Capability<IFluidHandler> getCapability() {
            return ForgeCapabilities.FLUID_HANDLER;
        }

        /**
         * Creates a side-filtered fluid handler wrapper.
         *
         * @param capbilityIO effective IO for the queried side
         * @return wrapper over this trait's tanks
         */
        @Override
        public IFluidHandler getCapContent(IO capbilityIO) {
            return new FluidHandlerWrapper(storages, capbilityIO, getDefinition().isAllowSameFluids());
        }

        /**
         * Merges multiple fluid handler providers into one flattened handler.
         *
         * @param contents fluid handlers collected from compatible traits
         * @return concatenated fluid handler list
         */
        @Override
        public IFluidHandler mergeContents(List<IFluidHandler> contents) {
            return new FluidHandlerList(contents.toArray(new IFluidHandler[0]));
        }
    }
}
