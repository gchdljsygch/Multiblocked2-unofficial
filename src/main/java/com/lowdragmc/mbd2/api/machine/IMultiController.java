package com.lowdragmc.mbd2.api.machine;

import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * Machine contract for a multiblock controller.
 *
 * <p>The business goal is to coordinate structure validation, part membership,
 * async pattern checks, and controller-level recipe behavior. Structure checks
 * can run off-thread, but world mutation, part-list mutation, and recipe state
 * changes must be performed on the logical server thread while holding the
 * controller's pattern lock when structure state is involved.</p>
 */
public interface IMultiController extends IMachine {

    int BASE_ASYNC_CHECK_INTERVAL = 4;
    int LARGE_PATTERN_BLOCKS = 512;
    int MAX_ASYNC_CHECK_INTERVAL = 80;

    /**
     * Resolves a multiblock controller capability from a block entity.
     *
     * @param blockEntity block entity to inspect; {@code null} yields an empty
     *                    optional
     * @return resolved controller when the machine capability implements this
     * interface
     */
    static Optional<IMultiController> ofController(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? Optional.empty() : blockEntity.getCapability(MBDCapabilities.CAPABILITY_MACHINE).resolve()
                .filter(IMultiController.class::isInstance)
                .map(IMultiController.class::cast);
    }

    /**
     * Resolves a multiblock controller capability at a world position.
     *
     * @param level block getter used to read the block entity
     * @param pos   position to inspect
     * @return resolved controller when present
     */
    static Optional<IMultiController> ofController(@Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return ofController(level.getBlockEntity(pos));
    }

    /**
     * Checks whether the multiblock pattern can be formed.
     *
     * <p>Preconditions: this method may be called from an async thread, so
     * implementations must not mutate world, controller, or part state here.
     * Prefer {@link #checkPatternWithLock()} or {@link #checkPatternWithTryLock()}
     * from external callers. Side effects should be limited to temporary
     * {@link MultiblockState} check data.</p>
     *
     * @return {@code true} when the pattern currently matches
     */
    default boolean checkPattern() {
        BlockPattern pattern = getPattern();
        return pattern != null && pattern.checkPatternAt(getMultiblockState(), true);
    }

    /**
     * Computes how often async pattern checks should run.
     *
     * <p>Business goal: reduce background pressure for large patterns while
     * keeping small structures responsive.</p>
     *
     * @return interval in ticks, from {@link #BASE_ASYNC_CHECK_INTERVAL} up to
     * {@link #MAX_ASYNC_CHECK_INTERVAL}
     */
    default int getAsyncCheckPatternInterval() {
        BlockPattern pattern = getPattern();
        if (pattern == null) {
            return BASE_ASYNC_CHECK_INTERVAL;
        }
        int estimatedBlocks = pattern.getEstimatedBlockCount();
        if (estimatedBlocks <= LARGE_PATTERN_BLOCKS) {
            return BASE_ASYNC_CHECK_INTERVAL;
        }
        int scale = Math.max(1, (estimatedBlocks + LARGE_PATTERN_BLOCKS - 1) / LARGE_PATTERN_BLOCKS);
        return Math.min(MAX_ASYNC_CHECK_INTERVAL, BASE_ASYNC_CHECK_INTERVAL * scale);
    }

    /**
     * Checks the pattern while holding the pattern lock.
     *
     * <p>Side effects: acquires and releases {@link #getPatternLock()} around
     * {@link #checkPattern()}.</p>
     *
     * @return result of the locked pattern check
     */
    default boolean checkPatternWithLock() {
        var lock = getPatternLock();
        lock.lock();
        try {
            return checkPattern();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to check the pattern without blocking on the lock.
     *
     * @return {@code false} when the lock cannot be acquired or the pattern does
     * not match; {@code true} only for a successful locked check
     */
    default boolean checkPatternWithTryLock() {
        var lock = getPatternLock();
        if (lock.tryLock()) {
            try {
                return checkPattern();
            } finally {
                lock.unlock();
            }
        } else {
            return false;
        }
    }

    /**
     * Returns the structure pattern for this controller.
     *
     * <p>Implementations may compute dynamic patterns but should keep this method
     * side-effect free because callers may use it during async checks.</p>
     *
     * @return pattern to validate, or {@code null} when the controller currently
     * has no valid pattern definition
     */
    BlockPattern getPattern();

    /**
     * Returns whether the structure has previously formed.
     *
     * <p>A formed structure is not necessarily workable: parts may be unloaded or
     * invalid. Use {@link #isFormedValid()} for recipe/work checks.</p>
     *
     * @return {@code true} after successful formation until invalidated
     */
    boolean isFormed();

    /**
     * Returns whether the formed structure is currently workable.
     *
     * @return {@code true} when the structure is formed and all required parts
     * are loaded/valid
     */
    boolean isFormedValid();

    /**
     * Returns the mutable structure state record.
     *
     * @return non-null state containing controller position, matched parts, and
     * pattern check diagnostics
     */
    @Nonnull
    MultiblockState getMultiblockState();

    /**
     * Applies controller-part recipe modifications.
     *
     * <p>Business goal: let parts contribute overclocking, tier checks, or
     * content changes to controller recipes. Side effects should be avoided
     * because recipe logic calls this during candidate validation.</p>
     *
     * @param recipe candidate recipe detected by this controller's recipe type
     * @return recipe after all parts modify it, or {@code null} when any part
     * rejects it
     */
    @Nullable
    default MBDRecipe getModifiedRecipe(@Nonnull MBDRecipe recipe) {
        for (var part : getParts()) {
            recipe = part.modifyControllerRecipe(recipe, getRecipeLogic());
            if (recipe == null) return null;
        }
        return recipe;
    }

    /**
     * Merges controller and part parallelization modifiers.
     *
     * @param recipe candidate recipe
     * @return merged modifier contributed by the controller and all parts
     */
    @Override
    default ContentModifier getMaxParallel(@NotNull MBDRecipe recipe) {
        var maxParallel = IMachine.super.getMaxParallel(recipe);
        for (var part : getParts()) {
            maxParallel = maxParallel.merge(part.getMaxControllerParallel(recipe, getRecipeLogic()));
        }
        return maxParallel;
    }

    /**
     * Returns whether any part requires recipe remodification between cycles.
     *
     * @return {@code true} when at least one part requests
     * always-try-modify behavior
     */
    @Override
    default boolean alwaysTryModifyRecipe() {
        for (var part : getParts()) {
            if (part.alwaysTryModifyControllerRecipe()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs periodic async pattern validation and schedules formation on the
     * server thread.
     *
     * <p>Preconditions: called by {@link MultiblockWorldSavedData}'s async
     * logic. Implementations that use this default should register with
     * {@link MultiblockWorldSavedData#addAsyncLogic(IMultiController)} from
     * {@link #onLoad()} and unregister from
     * {@link MultiblockWorldSavedData#removeAsyncLogic(IMultiController)} from
     * {@link #onUnload()}. Side effects: may run a try-locked async check and,
     * when the pattern matches, schedules a locked server-thread confirmation
     * that calls {@link #onStructureFormed()} and updates multiblock saved-data
     * mappings.</p>
     *
     * @param periodID periodic tick id supplied by async saved-data logic
     */
    default void asyncCheckPattern(long periodID) {
        long periodOffset = getOffset() + periodID;
        if ((getMultiblockState().hasError() || !isFormed())
                && periodOffset % BASE_ASYNC_CHECK_INTERVAL == 0
                && periodOffset % getAsyncCheckPatternInterval() == 0
                && asyncPreviewPatternMatch()) {
            if (getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    var lock = getPatternLock();
                    lock.lock();
                    try {
                        if (checkPattern()) { // formed
                            onStructureFormed();
                            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                            mwsd.addMapping(getMultiblockState());
                            mwsd.removeAsyncLogic(this);
                        }
                    } finally {
                        lock.unlock();
                    }
                });
            }
        }
    }

    private boolean asyncPreviewPatternMatch() {
        var lock = getPatternLock();
        if (!lock.tryLock()) {
            return false;
        }
        var state = getMultiblockState();
        try {
            state.setCommitSuccessfulMatches(false);
            return checkPattern();
        } finally {
            state.setCommitSuccessfulMatches(true);
            lock.unlock();
        }
    }

    /**
     * Handles successful structure formation.
     *
     * <p>Preconditions: call after a successful {@link #checkPattern()} on the
     * server side or in a fake scene. Side effects usually include marking the
     * controller formed, attaching parts, syncing state, and notifying the world.
     * Triggered when the structure first forms or block changes keep it formed.</p>
     */
    void onStructureFormed();

    /**
     * Invalidates the structure without marking the controller as removed.
     *
     * <p>Side effects are delegated to
     * {@link #onStructureInvalid(boolean)}.</p>
     */
    default void onStructureInvalid() {
        onStructureInvalid(false);
    }

    /**
     * Handles structure invalidation.
     *
     * <p>Preconditions: call on the server side or in a fake scene when blocks
     * change or before the controller is removed. Side effects usually include
     * detaching parts, clearing formed state, updating saved-data mappings, and
     * interrupting recipe logic.</p>
     *
     * @param isControllerRemoved {@code true} when invalidation is caused by
     *                            controller removal
     */
    void onStructureInvalid(boolean isControllerRemoved);

    /**
     * Returns currently attached parts.
     *
     * @return mutable or immutable implementation-defined list of parts; callers
     * should not mutate it unless the implementation documents that behavior
     */
    List<IMultiPart> getParts();

    /**
     * Called by a part when it unloads or is broken.
     *
     * <p>Side effects: implementation-specific, usually invalidating the
     * structure or scheduling a recheck.</p>
     */
    void onPartUnload();

    /**
     * Returns the lock guarding pattern checks and structure state mutation.
     *
     * @return shared lock used by async and server-thread pattern logic
     */
    Lock getPatternLock();

    /**
     * Decides whether a matched part should be attached to this controller.
     *
     * @param part candidate part from the matched pattern
     * @return {@code true} to add the part to the controller's part list
     */
    default boolean shouldAddPartToController(IMultiPart part) {
        return true;
    }

    /**
     * Supplies a controller-defined appearance for a part.
     *
     * @param part        part being rendered or queried
     * @param side        queried side
     * @param sourceState original part block state
     * @param sourcePos   original part position
     * @return replacement appearance, or {@code null} to use the part default
     */
    @Nullable
    default BlockState getPartAppearance(IMultiPart part, Direction side, BlockState sourceState, BlockPos sourcePos) {
        return null;
    }

    /**
     * Notifies attached parts that controller recipe status changed.
     *
     * @param oldStatus previous status
     * @param newStatus new status
     */
    default void notifyRecipeStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        for (IMultiPart part : getParts()) {
            part.notifyControllerRecipeStatusChanged(this, oldStatus, newStatus);
        }
    }

    /**
     * Lets attached parts veto controller recipe setup.
     *
     * @param recipe recipe about to start
     * @return {@code true} when any part interrupts setup
     */
    @Override
    default boolean beforeWorking(MBDRecipe recipe) {
        IMachine.super.beforeWorking(recipe);
        for (IMultiPart part : getParts()) {
            if (part.beforeControllerWorking(this)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lets attached parts interrupt an active controller recipe tick.
     *
     * @return {@code true} when any part interrupts work
     */
    @Override
    default boolean onWorking() {
        IMachine.super.onWorking();
        for (IMultiPart part : getParts()) {
            if (part.onControllerWorking(this)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notifies attached parts that controller recipe logic is waiting.
     */
    @Override
    default void onWaiting() {
        IMachine.super.onWaiting();
        for (IMultiPart part : getParts()) {
            part.onControllerWaiting(this);
        }
    }

    /**
     * Notifies attached parts that controller recipe work is cleaning up before
     * outputs are produced or work is interrupted.
     */
    @Override
    default void afterWorking() {
        IMachine.super.afterWorking();
        for (IMultiPart part : getParts()) {
            part.afterControllerWorking(this);
        }
    }
}
