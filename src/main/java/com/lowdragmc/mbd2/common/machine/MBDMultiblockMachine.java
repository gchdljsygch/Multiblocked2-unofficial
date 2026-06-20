package com.lowdragmc.mbd2.common.machine;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.block.ProxyPartBlock;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.blockentity.ProxyPartBlockEntity;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeHandlerSlotsProxy;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.client.renderer.MultiblockInWorldPreviewRenderer;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.event.*;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.lowdragmc.mbd2.common.capability.recipe.RecipeCapabilitiesProxyCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controller runtime for a pattern-formed multiblock machine.
 * <p>
 * This subclass extends {@link MBDMachine} with structure matching, formed
 * state tracking, part membership, proxy-part replacement, and controller-wide
 * recipe capability aggregation. A formed controller can route recipe handlers
 * from matching parts and can hide/replace blocks that the pattern marks as
 * rendered by the controller.
 * <p>
 * Structure matching is coordinated with {@link MultiblockWorldSavedData}.
 * Pattern checks may be requested asynchronously by world saved data, while
 * formed-structure validation is batched on the server tick to avoid checking
 * large structures in one tick. Mutations of multiblock state should happen on
 * the logical server main thread or while holding {@link #getPatternLock()}.
 */
public class MBDMultiblockMachine extends MBDMachine implements IMultiController {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MBDMultiblockMachine.class, MBDMachine.MANAGED_FIELD_HOLDER);
    private static final int FORMED_VALIDATION_BATCH_SIZE = 64;
    private static final int FORMED_VALIDATION_FULL_CHECK_DELAY = 20;

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private MultiblockState multiblockState;
    private final List<IMultiPart> parts = new ArrayList<>();
    @Getter
    @DescSynced
    @UpdateListener(methodName = "onPartsUpdated")
    private BlockPos[] partPositions = new BlockPos[0];
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean isFormed;
    @Getter
    protected Set<BlockPos> renderingDisabledPositions = new HashSet<>();
    @Getter
    private final Lock patternLock = new ReentrantLock();
    @Getter
    @Setter
    @Persisted
    @Nullable
    private BlockState originalBlock; // original block Before Structure Formed
    // runtime
    @Getter
    private boolean isFormedValid = false;
    private transient BlockPos[] formedValidationPositions = new BlockPos[0];
    private transient int formedValidationIndex;

    /**
     * Creates a multiblock controller bound to a block entity holder.
     *
     * @param machineHolder block entity holder that owns this controller
     * @param definition    multiblock definition that supplies pattern and settings
     * @param args          optional subclass-specific creation arguments
     */
    public MBDMultiblockMachine(IMachineBlockEntity machineHolder, MultiblockMachineDefinition definition, Object... args) {
        super(machineHolder, definition, args);
    }

    /**
     * on machine valid in the chunk.
     * <br>
     * We will add the async pattern checking logic into the next tick.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    /**
     * on machine invalid in the chunk.
     * <br>
     * You should call it in yourselves {@link BlockEntity#setRemoved()}.
     */
    @Override
    public void onUnload() {
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeAsyncLogic(this);
        }
    }

    /**
     * Server tick for the controller.
     * <p>
     * Performs a small batch of formed-structure validation before running the
     * base machine tick. If validation discovers that a cached predicate no
     * longer matches, the controller keeps its error state and waits for normal
     * invalidation/recheck flow.
     */
    @Override
    public void serverTick() {
        long start = System.nanoTime();
        try {
            validateFormedStructureIncrementally();
            super.serverTick();
        } finally {
            updateGameDelay(System.nanoTime() - start);
        }
    }

    private void validateFormedStructureIncrementally() {
        if (!isFormed || isFormedValid || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var lock = getPatternLock();
        if (!lock.tryLock()) {
            return;
        }
        try {
            if (getOffsetTimer() % FORMED_VALIDATION_FULL_CHECK_DELAY != 0 && formedValidationPositions.length == 0) {
                return;
            }
            var state = getMultiblockState();
            if (formedValidationPositions.length == 0) {
                formedValidationPositions = state.getCache().toArray(BlockPos[]::new);
                formedValidationIndex = 0;
                if (formedValidationPositions.length == 0) {
                    formedValidationPositions = new BlockPos[0];
                    refreshFormedStructure(serverLevel);
                    return;
                }
            }
            if (!validateFormedStructureBatch(serverLevel)) {
                return;
            }
            if (formedValidationIndex < formedValidationPositions.length) {
                return;
            }
            formedValidationPositions = new BlockPos[0];
            formedValidationIndex = 0;
            refreshFormedStructure(serverLevel);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-checks one slice of the cached formed structure against the predicates saved during formation.
     *
     * @return {@code false} if a cached position no longer matches and the structure was invalidated
     */
    private boolean validateFormedStructureBatch(ServerLevel serverLevel) {
        var state = getMultiblockState();
        Map<BlockPos, TraceabilityPredicate> predicateMap = state.getMatchContext().get("predicates");
        Direction patternFacing = state.getPatternFacing();
        Direction patternBaseFacing = state.getPatternBaseFacing();
        int end = Math.min(formedValidationPositions.length, formedValidationIndex + FORMED_VALIDATION_BATCH_SIZE);
        for (; formedValidationIndex < end; formedValidationIndex++) {
            BlockPos pos = formedValidationPositions[formedValidationIndex];
            TraceabilityPredicate predicate = predicateMap == null ? null : predicateMap.get(pos);
            if (predicate == null) {
                formedValidationPositions = new BlockPos[0];
                formedValidationIndex = 0;
                return true;
            }
            MultiblockState checkState = new MultiblockState(getLevel(), getPos());
            if (!checkState.testPredicateAt(pos, predicate, patternFacing, patternBaseFacing)) {
                state.setError(checkState.error == null ? MultiblockState.UNINIT_ERROR : checkState.error);
                formedValidationPositions = new BlockPos[0];
                formedValidationIndex = 0;
                invalidateFormedStructure(serverLevel);
                return false;
            }
        }
        return true;
    }

    /**
     * Rebuilds world-saved-data mappings after the cached formed structure still matches the world.
     */
    private void refreshFormedStructure(ServerLevel serverLevel) {
        if (checkPatternWithTryLock()) {
            onStructureFormed();
            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
            mwsd.addMapping(getMultiblockState());
            mwsd.removeAsyncLogic(this);
        }
    }

    /**
     * Runs the full invalidation path and queues async pattern checking so a later valid structure can form again.
     */
    private void invalidateFormedStructure(ServerLevel serverLevel) {
        onStructureInvalid();
        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
        mwsd.removeMapping(getMultiblockState());
        mwsd.addAsyncLogic(this);
    }

    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) super.getDefinition();
    }

    /**
     * Called when recipe logic status changed.
     * <br>
     * By default, We will update the machine state to match the recipe logic status.
     * <br>
     * It will also be called when the machine is formed {@link #onStructureFormed()} while the oldStatus is same as the newStatus.
     */
    @Override
    public void notifyRecipeStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        IMultiController.super.notifyRecipeStatusChanged(oldStatus, newStatus);
        if (isFormed) {
            switch (newStatus) {
                case WORKING -> setMachineState("working");
                case IDLE -> setMachineState("formed");
                case WAITING -> setMachineState("waiting");
                case SUSPEND -> setMachineState("suspend");
            }
        } else {
            setMachineState("base");
        }
        MinecraftForge.EVENT_BUS.post(new MachineRecipeStatusChangedEvent(this, oldStatus, newStatus).postCustomEvent());
    }

    @Override
    public @Nullable MBDRecipe getModifiedRecipe(@Nonnull MBDRecipe recipe) {
        return IMultiController.super.getModifiedRecipe(
                getDefinition().recipeLogicSettings().recipeModifiers().applyModifiers(getRecipeLogic(), recipe));
    }

    @Override
    public ContentModifier getMaxParallel(@Nonnull MBDRecipe recipe) {
        var maxParallel = getDefinition().recipeLogicSettings().recipeModifiers().getMaxParallel(getRecipeLogic(), recipe);
        return maxParallel.merge(IMultiController.super.getMaxParallel(recipe)).merge(getDynamicMaxParallelModifier());
    }

    @Override
    public boolean alwaysTryModifyRecipe() {
        return super.alwaysTryModifyRecipe() || IMultiController.super.alwaysTryModifyRecipe();
    }

    @Override
    public boolean beforeWorking(MBDRecipe recipe) {
        if (super.beforeWorking(recipe)) {
            return true;
        }
        return IMultiController.super.beforeWorking(recipe);
    }

    @Override
    public boolean onWorking() {
        if (super.onWorking()) {
            return true;
        }
        return IMultiController.super.onWorking();
    }

    @Override
    public void onWaiting() {
        super.onWaiting();
        IMultiController.super.onWaiting();
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        IMultiController.super.afterWorking();
    }

    /**
     * Resolves the structure pattern used by this controller.
     * <p>
     * The definition may return a static pattern, a controller-specific dynamic pattern, a combined pattern, or
     * {@code null} when no pattern is configured. Structure checking calls this on the logical server or in fake preview
     * scenes while holding the pattern lock as appropriate.
     *
     * @return pattern used for structure matching, or {@code null} when this controller has no structure definition
     */
    @Override
    public BlockPattern getPattern() {
        return getDefinition().getPattern(this);
    }

    /**
     * Returns the mutable state object that records structure-matching information.
     * <p>
     * Side effect: lazily creates the state when it does not exist. The state stores match caches, pattern facings,
     * errors, and context data such as part IO/slot mappings. Callers mutating the state during structure checks should
     * coordinate with {@link #getPatternLock()}.
     *
     * @return non-null multiblock state for this controller
     */
    @Override
    @Nonnull
    public MultiblockState getMultiblockState() {
        if (multiblockState == null) {
            multiblockState = new MultiblockState(getLevel(), getPos());
        }
        return multiblockState;
    }

    /**
     * Rebuilds the client-side part cache after synced part positions change.
     * <p>
     * This is invoked by LowDragLib sync through {@code partPositions}. Side effects: clears the cached part list and
     * resolves every new position to a live {@link IMultiPart} if the chunk is loaded. Missing parts are skipped and can
     * be resolved again by {@link #getParts()} when chunks reload.
     *
     * @param newValue synced part positions from the server
     * @param oldValue previous synced part positions; supplied by the sync system for listener compatibility
     */
    @SuppressWarnings("unused")
    protected void onPartsUpdated(BlockPos[] newValue, BlockPos[] oldValue) {
        parts.clear();
        for (var pos : newValue) {
            IMultiPart.ofPart(getLevel(), pos).ifPresent(parts::add);
        }
    }

    /**
     * Updates the synced position array from the current live part list.
     * <p>
     * Side effect: replaces {@link #partPositions}. Call on the server after parts are formed, invalidated, or sorted so
     * clients can reconstruct their part cache.
     */
    protected void updatePartPositions() {
        this.partPositions = this.parts.isEmpty() ? new BlockPos[0] : this.parts.stream().map(IMachine::getPos).toArray(BlockPos[]::new);
    }

    /**
     * Returns the live multiblock parts currently associated with this controller.
     * <p>
     * On the client, if the cached part list no longer matches the synced position array, the method attempts to rebuild
     * the cache from loaded chunks. The returned list is the internal mutable list; callers should not mutate it unless
     * they own the controller structure lifecycle.
     *
     * @return internal part list in controller-defined order
     */
    @Override
    public List<IMultiPart> getParts() {
        // for the client side, when the chunk unloaded
        if (parts.size() != this.partPositions.length) {
            parts.clear();
            for (var pos : this.partPositions) {
                IMultiPart.ofPart(getLevel(), pos).ifPresent(parts::add);
            }
        }
        return this.parts;
    }

    /**
     * Updates the formed flag and switches the visible machine state.
     * <p>
     * This method only changes local state. Use
     * {@link #onStructureFormed()} or {@link #onStructureInvalid(boolean)} when
     * part membership, proxy blocks, recipe handlers, and events must also be
     * updated.
     *
     * @param formed whether the structure should be considered formed
     */
    public void setFormed(boolean formed) {
        this.isFormed = formed;
        setMachineState(isFormed ? "formed" : getDefinition().stateMachine().getRootState().name());
    }

    /**
     * Shall we run the recipe logic during the server tick?
     * <br>
     * if the machine has no recipe logic or using the {@link MBDRecipeType#DUMMY}, it will return false.
     * <br>
     * if the controller is not formed, it will return false.
     */
    @Override
    public boolean runRecipeLogic() {
        return super.runRecipeLogic() && isFormed() && isFormedValid();
    }

    /**
     * Initialize the capabilities proxy for recipe logic. see {@link IRecipeCapabilityHolder#getRecipeCapabilitiesProxy()}
     * <br>
     * For a formed multiblock, it will collect all the recipe handlers from all parts.
     */
    @Override
    public void initCapabilitiesProxy() {
        super.initCapabilitiesProxy();
        if (isFormed()) {
            var capabilitiesProxy = getRecipeCapabilitiesProxy();
            Map<Long, IO> ioMap = getMultiblockState().getMatchContext().getOrCreate("ioMap", Long2ObjectMaps::emptyMap);
            Map<Long, Set<String>> slots = getMultiblockState().getMatchContext().getOrDefault("slots", Long2ObjectMaps.emptyMap());
            for (IMultiPart part : getParts()) {
                IO io = ioMap.getOrDefault(part.getPos().asLong(), IO.BOTH);
                Set<String> slotNames = slots.getOrDefault(part.getPos().asLong(), Collections.emptySet());
                if (io == IO.NONE) continue;
                for (var handler : part.getRecipeHandlers()) {
                    // If IO not compatible
                    if (io != IO.BOTH && handler.getHandlerIO() != IO.BOTH && io != handler.getHandlerIO()) continue;
                    var handlerIO = io == IO.BOTH ? handler.getHandlerIO() : io;
                    if (!capabilitiesProxy.contains(handlerIO, handler.getRecipeCapability())) {
                        capabilitiesProxy.put(handlerIO, handler.getRecipeCapability(), new ArrayList<>());
                    }
                    if (slotNames.isEmpty()) {
                        capabilitiesProxy.get(handlerIO, handler.getRecipeCapability()).add(handler);
                    } else {
                        var mergedSlots = new HashSet<>(slotNames);
                        mergedSlots.addAll(handler.getSlotNames());
                        capabilitiesProxy.get(handlerIO, handler.getRecipeCapability()).add(new RecipeHandlerSlotsProxy<>(handler, mergedSlots));
                    }
                }
            }
        }
        RecipeCapabilitiesProxyCompat.apply(getRecipeCapabilitiesProxy());
    }

    /**
     * Called when structure is formed, have to be called after {@link #checkPattern()}. (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed but still formed.
     * <br>
     * 2 - Literally, structure formed.
     */
    @Override
    public void onStructureFormed() {
        resetFormedValidation();
        setFormed(true);
        this.isFormedValid = true;
        this.parts.clear();
        this.renderingDisabledPositions.clear();
        // disable rendering for formed parts
        LongSet disabled = getMultiblockState().getMatchContext().getOrDefault("renderMask", LongSets.EMPTY_SET);
        for (var pos : disabled) {
            var blockPos = BlockPos.of(pos);
            renderingDisabledPositions.add(blockPos);
            // if it not a part, replace it with the proxy part block
            if (IMultiPart.ofPart(getLevel(), blockPos).isEmpty()) {
                // do not replace the proxy part block
                if (getLevel().getBlockEntity(blockPos) instanceof ProxyPartBlockEntity proxyPartBlockEntity) {
                    // setup proxy part block with correct machine
                    proxyPartBlockEntity.setControllerData(this.getPos());
                } else {
                    ProxyPartBlock.replaceOriginalBlock(this.getPos(), getLevel(), blockPos);
                }
            }
        }
        Set<IMultiPart> set = getMultiblockState().getMatchContext().getOrCreate("parts", Collections::emptySet);
        for (IMultiPart part : set) {
            if (shouldAddPartToController(part)) {
                this.parts.add(part);
            }
        }
        getDefinition().sortParts(this.parts);
        for (var part : parts) {
            part.addedToController(this);
        }
        updatePartPositions();
        // refresh traits
        initCapabilitiesProxy();
        // post event
        MinecraftForge.EVENT_BUS.post(new MachineStructureFormedEvent(this).postCustomEvent());
        // notify recipe logic
        notifyRecipeStatusChanged(getRecipeLogic().getStatus(), getRecipeLogic().getStatus());
    }

    /**
     * Called when structure is invalid. (server-side / fake scene only)
     * <br>
     * Trigger points:
     * <br>
     * 1 - Blocks in structure changed.
     * <br>
     * 2 - Before controller machine removed.
     */
    @Override
    public void onStructureInvalid(boolean isControllerRemoved) {
        resetFormedValidation();
        setFormed(false);
        this.isFormedValid = false;
        // reset recipe Logic
        getRecipeLogic().resetRecipeLogic();
        var invalidParts = List.copyOf(parts);
        // clear parts
        for (IMultiPart part : parts) {
            part.removedFromController(this);
        }
        this.parts.clear();
        updatePartPositions();
        // refresh traits
        initCapabilitiesProxy();
        // restore original blocks
        for (var pos : renderingDisabledPositions) {
            if (getLevel().getBlockEntity(pos) instanceof ProxyPartBlockEntity proxyPartBlockEntity) {
                proxyPartBlockEntity.restoreOriginalBlock();
            }
        }
        this.renderingDisabledPositions.clear();
        // post event
        MinecraftForge.EVENT_BUS.post(new MachineStructureInvalidEvent(this, invalidParts).postCustomEvent());
        // back to original block
        if (!isControllerRemoved && originalBlock != null) {
            getLevel().setBlockAndUpdate(getPos(), originalBlock);
        }
    }

    /**
     * Invalidates the formed structure when a tracked part unloads before normal block updates can report the change.
     */
    @Override
    public void onPartUnload() {
        resetFormedValidation();
        getMultiblockState().setError(MultiblockState.UNLOAD_ERROR);
        if (getLevel() instanceof ServerLevel serverLevel) {
            invalidateFormedStructure(serverLevel);
        }
    }

    /**
     * Called when the machine is rotated.
     * <br>
     * It has to be triggered somewhere yourself.
     */
    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        if (oldFacing != newFacing && getLevel() instanceof ServerLevel serverLevel) {
            // invalid structure
            invalidateFormedStructure(serverLevel);
        }
    }

    private void resetFormedValidation() {
        formedValidationPositions = new BlockPos[0];
        formedValidationIndex = 0;
    }

    /**
     * Should open UI.
     */
    @Override
    public boolean shouldOpenUI(InteractionHand hand, BlockHitResult hit) {
        return super.shouldOpenUI(hand, hit) && (!getDefinition().multiblockSettings().showUIOnlyFormed() || isFormed());
    }

    /**
     * On hand is using on the machine.
     * <br>
     * We will check the catalyst and consume it if it's valid.
     */
    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isFormed() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (world.isClientSide()) {
                MultiblockInWorldPreviewRenderer.showPreview(pos, this, ConfigHolder.multiblockPreviewDuration * 20);
            }
            return InteractionResult.SUCCESS;
        }
        if (!isFormed() && getDefinition().multiblockSettings().catalyst().isEnable()) {
            var catalyst = getDefinition().multiblockSettings().catalyst();
            var held = player.getItemInHand(hand);
            if (catalyst.test(held)) {
                if (world instanceof ServerLevel serverLevel && checkPatternWithLock()) { // formed
                    var success = onCatalystUsed(player, hand, held);
                    if (success) {
                        onStructureFormed();
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.addMapping(getMultiblockState());
                        mwsd.removeAsyncLogic(this);
                        return InteractionResult.CONSUME;
                    } else {
                        return InteractionResult.FAIL;
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }

    /**
     * Applies catalyst consumption after the pattern has matched.
     * <p>
     * The method posts {@link MachineUseCatalystEvent}; canceling the event
     * rejects formation. Creative players do not consume the catalyst. Survival
     * players either lose items or durability according to the multiblock
     * catalyst settings.
     *
     * @param player player using the catalyst
     * @param hand   hand containing the catalyst
     * @param held   catalyst stack currently held by the player
     * @return {@code true} when formation may proceed
     */
    public boolean onCatalystUsed(Player player, InteractionHand hand, ItemStack held) {
        var catalyst = getDefinition().multiblockSettings().catalyst();
        var event = new MachineUseCatalystEvent(this, held, player, hand);
        MinecraftForge.EVENT_BUS.post(event.postCustomEvent());
        if (event.isCanceled()) {
            return false;
        }
        if (player.isCreative()) return true;
        return switch (catalyst.getCatalystType()) {
            case CONSUME_ITEM -> {
                if (held.getCount() >= catalyst.getConsumeItemAmount()) {
                    held.shrink(catalyst.getConsumeItemAmount());
                    yield true;
                }
                yield false;
            }
            case CONSUME_DURABILITY -> {
                if (catalyst.getConsumeDurabilityValue() <= held.getMaxDamage() - held.getDamageValue()) {
                    held.hurtAndBreak(catalyst.getConsumeDurabilityValue(), player, p -> p.broadcastBreakEvent(hand));
                    yield true;
                }
                yield false;
            }
        };
    }

    /**
     * Returns the block/item dropped when the controller is broken.
     * <p>
     * Controllers that replaced an original block during structure formation
     * drop the original block instead of the machine definition item.
     */
    @Override
    public ItemStack getDropItem() {
        if (originalBlock != null) {
            return originalBlock.getBlock().asItem().getDefaultInstance();
        }
        return super.getDropItem();
    }
}
