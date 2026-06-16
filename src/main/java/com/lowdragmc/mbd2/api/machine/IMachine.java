package com.lowdragmc.mbd2.api.machine;

import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumption;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Common contract for a block-entity-backed MBD machine.
 *
 * <p>The business goal is to expose machine lifecycle, orientation, recipe
 * execution hooks, and recipe capability access through one capability-backed
 * interface. Most mutating callbacks are expected to run on the logical server
 * thread that owns the block entity. Rendering helpers may be called on the
 * client side when refreshing visual state.</p>
 */
public interface IMachine extends IRecipeCapabilityHolder {

    /**
     * Resolves a machine capability from a block entity.
     *
     * @param blockEntity block entity to inspect; {@code null} yields an empty
     *                    optional
     * @return resolved machine capability when present
     */
    static Optional<IMachine> ofMachine(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? Optional.empty() : blockEntity.getCapability(MBDCapabilities.CAPABILITY_MACHINE).resolve();
    }

    /**
     * Resolves a machine capability at a world position.
     *
     * @param level block getter used to read the block entity
     * @param pos   position to inspect
     * @return resolved machine capability when a machine block entity exists
     */
    static Optional<IMachine> ofMachine(@Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return ofMachine(level.getBlockEntity(pos));
    }

    /**
     * Returns the backing block entity.
     *
     * @return block entity that owns this machine instance
     */
    BlockEntity getHolder();

    /**
     * Returns the current level for the backing block entity.
     *
     * @return level, or {@code null} while the block entity is detached
     */
    default Level getLevel() {
        return getHolder().getLevel();
    }

    /**
     * Returns the machine's block position.
     *
     * @return immutable position of the backing block entity
     */
    default BlockPos getPos() {
        return getHolder().getBlockPos();
    }

    /**
     * Returns the machine's current block state.
     *
     * @return block state stored by the backing block entity
     */
    default BlockState getBlockState() {
        return getHolder().getBlockState();
    }

    /**
     * Convert a machine-relative position to an absolute world position.
     *
     * <p>Relative axes are {@code +X = right}, {@code +Y = up}, and
     * {@code +Z = front}. Machines without a front facing are treated as facing
     * north. Side effects: none.</p>
     *
     * @param relativePos relative position in machine coordinates
     * @return absolute world position for the same point
     */
    default BlockPos getAbsolutePos(@Nonnull BlockPos relativePos) {
        Objects.requireNonNull(relativePos, "relativePos");
        var front = getFrontFacing().orElse(Direction.NORTH);
        return getPos()
                .relative(getRelativeRight(front), relativePos.getX())
                .relative(getRelativeUp(front), relativePos.getY())
                .relative(front, relativePos.getZ());
    }

    /**
     * Convert machine-relative coordinates to an absolute world position.
     *
     * @param x relative right offset
     * @param y relative upward offset
     * @param z relative forward offset
     * @return absolute world position for the same point
     */
    default BlockPos getAbsolutePos(int x, int y, int z) {
        return getAbsolutePos(new BlockPos(x, y, z));
    }

    private static Direction getRelativeRight(Direction front) {
        return front.getAxis() == Direction.Axis.Y ? Direction.EAST : front.getClockWise();
    }

    private static Direction getRelativeUp(Direction front) {
        return switch (front) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }

    /**
     * Returns this machine's stable tick offset.
     *
     * <p>Business goal: spread expensive periodic work across server ticks.
     * Implementations should keep this stable for the lifetime of the machine.</p>
     *
     * @return non-negative or otherwise stable offset value
     */
    long getOffset();

    /**
     * Returns game time shifted by the machine's offset.
     *
     * @return {@code level.getGameTime() + getOffset()} when attached to a level;
     * otherwise just {@link #getOffset()}
     */
    default long getOffsetTimer() {
        var level = getLevel();
        return level == null ? getOffset() : (level.getGameTime() + getOffset());
    }

    /**
     * Get this machine's latest server tick cost in microseconds.
     *
     * <p>The value records time spent by this machine's tick hooks, recipe logic,
     * and trait logic.</p>
     *
     * @return latest tick cost in microseconds, or {@code 0} when not tracked
     */
    default long getGameDelayMicroseconds() {
        return 0;
    }

    /**
     * Get this machine's average server tick cost over the last ten seconds in microseconds.
     *
     * @return rolling average tick cost in microseconds, or {@code 0} when not
     * tracked
     */
    default long getTenSecondAverageGameDelayMicroseconds() {
        return 0;
    }

    /**
     * Short alias for {@link #getGameDelayMicroseconds()}.
     *
     * @return latest tick cost in microseconds
     */
    default long getGameDelay() {
        return getGameDelayMicroseconds();
    }

    /**
     * Short alias for {@link #getTenSecondAverageGameDelayMicroseconds()}.
     *
     * @return rolling average tick cost in microseconds
     */
    default long getTenSecondAverageGameDelay() {
        return getTenSecondAverageGameDelayMicroseconds();
    }

    /**
     * Marks the backing block entity as changed on the server thread.
     *
     * <p>Side effects: schedules {@link BlockEntity#setChanged()} through the
     * server executor when the machine is attached to a server level. Client-side
     * and detached machines are ignored.</p>
     */
    default void markDirty() {
        var level = getLevel();
        // make sure mark dirty in the server thread.
        if (level != null && !level.isClientSide && level.getServer() != null) {
            level.getServer().execute(() -> getHolder().setChanged());
        }
    }

    /**
     * Returns whether the backing block entity has been removed.
     *
     * @return {@code true} when the block entity is invalid/removed
     */
    default boolean isInValid() {
        return getHolder().isRemoved();
    }

    /**
     * Saves custom persisted data that cannot be represented by the sync-data
     * system.
     *
     * <p>Business goal: preserve optional integration or implementation-specific
     * state without changing the shared sync model. Side effects: implementations
     * may write additional keys into {@code tag}.</p>
     *
     * @param tag     destination NBT tag
     * @param forDrop {@code true} when serializing data into a dropped machine
     *                item rather than world storage
     */
    default void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {

    }

    /**
     * Loads custom persisted data that cannot be represented by the sync-data
     * system.
     *
     * <p>Side effects: implementations may mutate machine fields from
     * {@code tag}. Call on the owning server/load thread.</p>
     *
     * @param tag source NBT tag
     */
    default void loadCustomPersistedData(CompoundTag tag) {

    }

    /**
     * Returns this machine's front direction.
     *
     * @return optional front facing; empty means the machine is orientation
     * independent
     */
    Optional<Direction> getFrontFacing();

    /**
     * Returns whether this machine has orientation-dependent behavior.
     *
     * @return {@code true} when {@link #getFrontFacing()} is present; {@code false}
     * when all sides are structurally equivalent
     */
    default boolean hasFrontFacing() {
        return getFrontFacing().isPresent();
    }

    /**
     * Checks whether a proposed front direction is valid.
     *
     * @param facing candidate facing
     * @return {@code true} when the machine can be configured to face that
     * direction
     */
    boolean isFacingValid(Direction facing);

    /**
     * Sets the machine's front direction.
     *
     * <p>Preconditions: callers should check {@link #isFacingValid(Direction)}
     * before changing the facing. Side effects are implementation-specific and
     * typically include synced state changes, dirty marking, and render updates.</p>
     *
     * @param facing new front direction
     */
    void setFrontFacing(Direction facing);

    /**
     * Handles a completed machine rotation.
     *
     * <p>Side effects: implementation-specific. Callers are responsible for
     * invoking this after they change the stored facing.</p>
     *
     * @param oldFacing previous front direction
     * @param newFacing new front direction
     */
    default void onRotated(Direction oldFacing, Direction newFacing) {

    }

    /**
     * Requests a client-side block render refresh.
     *
     * <p>Side effects: on the client, calls {@link Level#sendBlockUpdated} for
     * this machine position. Server-side calls are ignored by the default
     * implementation.</p>
     */
    default void scheduleRenderUpdate() {
        var pos = getPos();
        var level = getLevel();
        if (level != null) {
            var state = level.getBlockState(pos);
            if (level.isClientSide) {
                level.sendBlockUpdated(pos, state, state, 1 << 3);
            }
        }
    }

    /**
     * Notifies neighboring blocks that this machine updated.
     *
     * <p>Side effects: calls {@link Level#updateNeighborsAt(BlockPos,
     * net.minecraft.world.level.block.Block)} when attached to a level.</p>
     */
    default void notifyBlockUpdate() {
        var pos = getPos();
        var level = getLevel();
        if (level != null) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    /**
     * Handles chunk unload for the machine.
     *
     * <p>Side effects: invalidates recipe working hooks through
     * {@link RecipeLogic#inValid()}. Implementations should call this from
     * {@link BlockEntity#onChunkUnloaded()}.</p>
     */
    default void onChunkUnloaded() {
        getRecipeLogic().inValid();
    }

    /**
     * Handles block entity removal.
     *
     * <p>Side effects: invalidates recipe working hooks through
     * {@link RecipeLogic#inValid()}. Implementations should call this from
     * {@link BlockEntity#setRemoved()}.</p>
     */
    default void onUnload() {
        getRecipeLogic().inValid();
    }

    /**
     * Handles block entity load or revalidation.
     *
     * <p>Default side effects: none. Implementations should call this from
     * {@link BlockEntity#clearRemoved()} when they need to re-register runtime
     * services.</p>
     */
    default void onLoad() {
    }

    //////////////////////////////////////
    //********   RECIPE LOGIC  *********//
    //////////////////////////////////////

    /**
     * Returns the recipe type executed by this machine.
     *
     * @return non-null recipe type; use {@link MBDRecipeType#DUMMY} for machines
     * without recipe logic
     */
    @Nonnull
    MBDRecipeType getRecipeType();

    /**
     * Called after recipe logic status changes.
     *
     * <p>Side effects: implementation-specific, usually UI, render, redstone, or
     * part notification updates.</p>
     *
     * @param oldStatus previous status
     * @param newStatus new status
     */
    default void notifyRecipeStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
    }

    /**
     * Returns whether this machine should tick recipe logic.
     *
     * @return {@code true} for normal recipe-capable machines; {@code false} for
     * {@link MBDRecipeType#DUMMY}
     */
    default boolean runRecipeLogic() {
        return getRecipeType() != MBDRecipeType.DUMMY;
    }

    /**
     * Returns the recipe logic state machine for this machine.
     *
     * @return non-null recipe logic instance owned by this machine
     */
    @Nonnull
    RecipeLogic getRecipeLogic();

    /**
     * Modifies a fuel recipe before it is consumed.
     *
     * <p>Business goal: let machines scale fuel duration, check tiers, or reject
     * fuel dynamically. Side effects should be limited to returning a modified
     * recipe; do not consume IO here.</p>
     *
     * @param recipe matched fuel recipe
     * @return modified fuel recipe, original recipe, or {@code null} to skip it
     */
    @Nullable
    default MBDRecipe modifyFuelRecipe(MBDRecipe recipe) {
        return recipe;
    }

    /**
     * Called when the current fuel burn interval ends.
     *
     * <p>Side effects: implementation-specific. Runs before recipe logic searches
     * for the next fuel source.</p>
     *
     * @param recipe fuel recipe that supplied the burn interval, or {@code null}
     *               for non-recipe fuel sources
     */
    default void onFuelBurningFinish(@Nullable MBDRecipe recipe) {
    }

    /**
     * Applies recipe modification and parallelization.
     *
     * <p>Override {@link #getModifiedRecipe(MBDRecipe)} for recipe-specific
     * changes and {@link #getMaxParallel(MBDRecipe)} for parallel scaling.
     * Side effects should be avoided; recipe logic calls this during candidate
     * validation.</p>
     *
     * @param recipe candidate recipe
     * @return modified recipe, or {@code null} when the machine cannot run it
     */
    @Nullable
    default MBDRecipe doModifyRecipe(@Nonnull MBDRecipe recipe) {
        recipe = getModifiedRecipe(recipe);
        if (recipe == null) {
            return null;
        }
        return applyParallel(recipe, getMaxParallel(recipe).apply(1).intValue());
    }

    /**
     * Modifies a candidate recipe before setup.
     *
     * <p>Business goal: apply overclocking, tier checks, chance changes, or other
     * machine-specific transforms. Side effects should be avoided because this
     * may run during recipe search/validation.</p>
     *
     * @param recipe candidate recipe detected by the recipe type
     * @return modified recipe, original recipe, or {@code null} when unavailable
     */
    @Nullable
    default MBDRecipe getModifiedRecipe(@Nonnull MBDRecipe recipe) {
        return recipe;
    }

    /**
     * Returns the maximum parallelization modifier for a recipe.
     *
     * @param recipe candidate recipe
     * @return content modifier whose value is applied to base parallel count
     */
    default ContentModifier getMaxParallel(@Nonnull MBDRecipe recipe) {
        return ContentModifier.IDENTITY;
    }

    /**
     * Applies parallel scaling to a recipe.
     *
     * <p>Side effects: none on the machine; returned recipe may be a copied or
     * scaled recipe from {@link MBDRecipe#accurateParallel}.</p>
     *
     * @param recipe      recipe to scale
     * @param maxParallel maximum parallel amount; values {@code <= 1} leave the
     *                    recipe unchanged
     * @return scaled recipe when parallelization is possible; otherwise the
     * original recipe
     */
    @Nonnull
    default MBDRecipe applyParallel(@Nonnull MBDRecipe recipe, int maxParallel) {
        // apply parallel here
        if (maxParallel > 1) {
            var result = MBDRecipe.accurateParallel(this, recipe, maxParallel, false);
            return result.getFirst();
        }
        return recipe;
    }

    /**
     * Called before recipe setup commits to working state.
     *
     * <p>Side effects: implementation-specific. Return {@code true} to veto
     * setup and reset recipe progress.</p>
     *
     * @param recipe recipe about to start
     * @return {@code true} to interrupt setup; {@code false} to continue
     */
    default boolean beforeWorking(MBDRecipe recipe) {
        return false;
    }

    /**
     * Called once per active recipe tick before progress is incremented.
     *
     * <p>Side effects: implementation-specific. Return {@code true} to interrupt
     * the active recipe.</p>
     *
     * @return {@code true} to interrupt work; {@code false} to continue
     */
    default boolean onWorking() {
        return false;
    }

    /**
     * Called when recipe logic enters or remains in waiting state.
     *
     * <p>Side effects: implementation-specific, usually visual, sound, or
     * diagnostic updates.</p>
     */
    default void onWaiting() {

    }

    /**
     * Called during recipe completion before outputs are produced.
     *
     * <p>Side effects: implementation-specific. Also used by interruption paths
     * for working cleanup.</p>
     */
    default void afterWorking() {

    }

    /**
     * Called after delayed input consumption and before outputs are produced.
     *
     * <p>Deprecated compatibility-style hook with no consumption details. Prefer
     * {@link #onConsumeInputsAfterWorking(RecipeConsumption)} when implementing
     * new logic.</p>
     */
    default void onConsumeInputsAfterWorking() {

    }

    /**
     * Called after recipe inputs are actually consumed.
     *
     * <p>Side effects: implementation-specific accounting, statistics, or
     * capability updates. The {@code consumedInputs} object describes what was
     * committed by recipe IO.</p>
     *
     * @param recipe         recipe whose inputs were consumed
     * @param consumedInputs committed input-consumption details
     * @param afterWorking   {@code true} when inputs were consumed after progress
     *                       completed; {@code false} for normal pre-working consumption
     */
    default void onRecipeInputsConsumed(MBDRecipe recipe, RecipeConsumption consumedInputs, boolean afterWorking) {

    }

    /**
     * Called after delayed input consumption and before outputs are produced.
     *
     * @param consumedInputs committed input-consumption details
     */
    default void onConsumeInputsAfterWorking(RecipeConsumption consumedInputs) {
        onConsumeInputsAfterWorking();
    }

    /**
     * Called after recipe outputs have been produced.
     *
     * <p>Side effects: implementation-specific completion logic such as stats,
     * sounds, render changes, or chaining behavior.</p>
     */
    default void onRecipeFinish() {

    }

    /**
     * Returns whether waiting should reduce current recipe progress.
     *
     * @return {@code true} to decay progress when waiting for per-tick resources;
     * {@code false} to preserve progress
     */
    default boolean dampingWhenWaiting() {
        return true;
    }

    /**
     * Returns whether recipe finish should remodify the original recipe before
     * restarting.
     *
     * @return {@code true} to keep an origin recipe and rerun
     * {@link #doModifyRecipe(MBDRecipe)} between cycles; {@code false} to keep
     * reusing the already modified last recipe
     */
    default boolean alwaysTryModifyRecipe() {
        return false;
    }

    /**
     * Returns whether finishing a recipe should force a fresh recipe search.
     *
     * @return {@code true} to mark the cached recipe dirty after completion
     */
    default boolean alwaysReSearchRecipe() {
        return false;
    }

    /**
     * Returns how many progress ticks are lost per waiting tick.
     *
     * @return non-negative damping amount in ticks
     */
    default int getRecipeDampingValue() {
        return 2;
    }

    /**
     * Chooses whether inputs are consumed before or after recipe progress.
     *
     * <p>When this returns {@code true}, recipe logic keeps validating inputs
     * during processing. If they stop matching, the active recipe is discarded.</p>
     *
     * @param recipe recipe about to start
     * @return {@code false} to consume inputs before work starts; {@code true} to
     * consume inputs after work completes and before outputs are produced
     */
    default boolean consumeInputsAfterWorking(MBDRecipe recipe) {
        return false;
    }

    /**
     * Returns this machine's tier or level for recipe conditions.
     *
     * @return machine level used by conditions such as
     * {@link com.lowdragmc.mbd2.common.recipe.MachineLevelCondition}; default is
     * {@code 0}
     */
    default int getMachineLevel() {
        return 0;
    }

    /**
     * Returns the chance tier used by recipe content chance calculations.
     *
     * @return same value as {@link #getMachineLevel()} by default
     */
    @Override
    default int getChanceTier() {
        return getMachineLevel();
    }

    /**
     * Returns a custom display name for this machine.
     *
     * @return optional component shown by UI integrations; {@code null} means use
     * the normal block/item name
     */
    default @Nullable Component getCustomName() {
        return null;
    }

}
