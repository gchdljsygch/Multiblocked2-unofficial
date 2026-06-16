package com.lowdragmc.mbd2.api.recipe;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTrait;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTraitDefinition;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.VisibleForTesting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime state machine that finds, starts, ticks, waits, finishes, and
 * interrupts recipes for one machine.
 *
 * <p>The business goal is to bind an {@link IMachine}'s recipe type and
 * capability handlers to persistent progress state. Most methods mutate synced
 * fields, machine dirty/render state, inventories, tanks, fuel counters, or
 * recipe handler callbacks and should run on the logical server thread that
 * owns the machine. Asynchronous recipe search is limited to finding candidate
 * recipes; setup and IO handling return to the game thread.</p>
 */
public class RecipeLogic implements IEnhancedManaged {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RecipeLogic.class);

    /**
     * Returns the managed-field metadata used by LowDragLib sync/persistence.
     *
     * @return static holder for {@link RecipeLogic}'s annotated fields
     */
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * Marks the owning machine dirty after a managed field changes.
     *
     * <p>Side effects: delegates to {@link IMachine#markDirty()}.</p>
     */
    @Override
    public void onChanged() {
        machine.markDirty();
    }

    /**
     * Requests a render refresh for the owning machine.
     *
     * <p>Side effects: delegates to {@link IMachine#scheduleRenderUpdate()}.</p>
     */
    @Override
    public void scheduleRenderUpdate() {
        machine.scheduleRenderUpdate();
    }

    /**
     * High-level recipe execution state exposed to UI and sync.
     */
    public enum Status {
        /**
         * No active recipe is currently progressing.
         */
        IDLE,
        /**
         * A recipe is active and progress advances each server tick.
         */
        WORKING,
        /**
         * A recipe is selected but cannot currently progress because a
         * condition, tick IO, or fuel requirement failed.
         */
        WAITING,
        /**
         * Working is disabled externally while a recipe may still be retained.
         */
        SUSPEND
    }

    @Getter
    public final IMachine machine;
    public List<MBDRecipe> lastFailedMatches;

    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    private Status status = Status.IDLE;

    @Nullable
    @Persisted
    @DescSynced
    @Getter
    private Component waitingReason = null;
    /**
     * unsafe, it may not be found from {@link RecipeManager}. Do not index it.
     */
    @Nullable
    @Getter
    @Persisted
    @Setter
    protected MBDRecipe lastRecipe;
    /**
     * safe, it is the origin recipe before {@link IMachine#doModifyRecipe(MBDRecipe)}' which can be found from {@link RecipeManager}.
     */
    @Nullable
    @Getter
    @Persisted
    protected MBDRecipe lastOriginRecipe;
    @Getter
    @Persisted
    @Setter
    protected boolean consumeInputsAfterWorking;
    @Persisted
    @Getter
    @Setter
    protected int progress;
    @Getter
    @Persisted
    @Setter
    protected int duration;
    @Getter
    @Persisted
    @Setter
    protected int fuelTime;
    @Nullable
    @Getter
    @Persisted
    @Setter
    protected MBDRecipe lastFuelRecipe;
    @Getter
    @Persisted
    @Setter
    protected int fuelMaxTime;
    @Getter(onMethod_ = @VisibleForTesting)
    protected boolean recipeDirty;
    @Persisted
    @Getter
    @Setter
    protected long totalContinuousRunningTime;
    @Nullable
    protected CompletableFuture<List<MBDRecipe>> completableFuture = null;

    /**
     * Creates recipe logic for a machine.
     *
     * <p>Preconditions: {@code machine} must be non-null and remain the owner of
     * this logic instance. Side effects: none beyond storing the reference.</p>
     *
     * @param machine machine whose recipe state is managed
     */
    public RecipeLogic(IMachine machine) {
        this.machine = machine;
    }

    /**
     * Aborts the current recipe and resets persisted runtime state.
     *
     * <p>Business goal: return the machine to a clean idle recipe state after
     * invalidation, reconfiguration, or manual reset. Side effects: interrupts
     * the active recipe, clears cached recipes and progress/fuel counters, and
     * sets status to {@link Status#IDLE}. Already-consumed inputs are not
     * restored.</p>
     */
    public void resetRecipeLogic() {
        interruptRecipe();
        recipeDirty = false;
        lastRecipe = null;
        lastOriginRecipe = null;
        progress = 0;
        duration = 0;
        fuelTime = 0;
        lastFailedMatches = null;
        consumeInputsAfterWorking = false;
        setStatus(Status.IDLE);
    }

    /**
     * Returns current recipe progress as a ratio.
     *
     * @return value in {@code [0.0, +inf)}; {@code 0.0} when no duration is set
     */
    public double getProgressPercent() {
        return duration == 0 ? 0.0 : progress / (duration * 1.0);
    }

    /**
     * Returns current fuel burn progress as a ratio.
     *
     * @return value in {@code [0.0, +inf)}; {@code 0.0} when no fuel is active
     */
    public double getFuelProgressPercent() {
        return fuelMaxTime == 0 ? 0.0 : fuelTime / (fuelMaxTime * 1.0);
    }

    /**
     * Determines whether recipe work currently requires fuel.
     *
     * <p>Business goal: support both explicit MBD fuel requirements and the
     * vanilla-fuel fallback configured on recipe-thread traits. Side effects:
     * when the fallback is enabled and no fuel is burning, this may consume one
     * vanilla fuel item, insert its crafting remainder, drop overflow, and set
     * {@link #fuelTime}/{@link #fuelMaxTime}.</p>
     *
     * @return {@code true} when the machine requires or has obtained fuel for
     * work; {@code false} when no fuel line is required or available
     */
    public boolean needFuel() {
        if (machine.getRecipeType().isRequireFuelForWorking()) {
            return true;
        }
        return handleVanillaFuelFallback();
    }

    /**
     * Attempts to satisfy the optional vanilla fuel-line fallback.
     *
     * <p>Business goal: let machines that are configured with a
     * {@link RecipeThreadTraitDefinition} burn ordinary furnace fuels without a
     * dedicated MBD fuel recipe. Preconditions: called from recipe ticking on
     * the logical server thread. Side effects: when a configured item trait
     * contains a burnable item, this may remove one item, insert or drop its
     * crafting remainder, set fuel counters, and clear {@link #lastFuelRecipe}.</p>
     *
     * @return {@code true} when existing or newly consumed vanilla fuel can power
     * work; {@code false} when no fallback is configured or usable
     */
    private boolean handleVanillaFuelFallback() {
        if (fuelTime > 0) return true;
        if (!(machine instanceof MBDMachine mbdMachine)) return false;
        RecipeThreadTraitDefinition cfg = null;
        for (var def : mbdMachine.getDefinition().machineSettings().traitDefinitions()) {
            if (def instanceof RecipeThreadTraitDefinition d) {
                cfg = d;
                break;
            }
        }
        if (cfg == null || !cfg.enableVanillaFuelLineA) return false;
        String traitName = cfg.vanillaFuelItemTraitName;
        if (traitName == null || traitName.isBlank()) return false;
        ItemSlotCapabilityTrait trait = mbdMachine.getTraitByName(ItemSlotCapabilityTrait.class, traitName);
        if (trait == null) return false;
        ItemStackTransfer storage = trait.storage;
        if (storage == null) return false;
        if (!tryBurnOneFuel(storage, mbdMachine)) return false;
        lastFuelRecipe = null;
        return true;
    }

    /**
     * Consumes a single burnable stack from the configured fuel storage.
     *
     * <p>Preconditions: {@code storage} must belong to {@code machine} and be
     * accessed on the logical server thread. Side effects: may mutate item
     * storage, spawn overflow remainder items in the world, and set
     * {@link #fuelTime}/{@link #fuelMaxTime} to a positive burn duration.</p>
     *
     * @param storage item handler searched slot by slot for a burnable item
     * @param machine owning machine used for overflow drops
     * @return {@code true} after one fuel item is consumed and burn time is set
     */
    private boolean tryBurnOneFuel(ItemStackTransfer storage, MBDMachine machine) {
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int burn = ForgeHooks.getBurnTime(stack, null);
            if (burn <= 0) continue;
            ItemStack extracted = storage.extractItem(slot, 1, false);
            if (extracted.isEmpty()) continue;
            int finalBurn = ForgeHooks.getBurnTime(extracted, null);
            if (finalBurn <= 0) {
                ItemStack back = storage.insertItem(slot, extracted, false);
                if (!back.isEmpty()) dropItem(machine, back);
                continue;
            }
            ItemStack remain = extracted.getCraftingRemainingItem();
            if (!remain.isEmpty()) {
                ItemStack left = storage.insertItem(slot, remain, false);
                if (!left.isEmpty()) dropItem(machine, left);
            }
            fuelMaxTime = finalBurn;
            fuelTime = finalBurn;
            return true;
        }
        return false;
    }

    /**
     * Drops an item stack at the owning machine's block position.
     *
     * <p>Preconditions: should run on the server side; client-side or empty
     * stacks are ignored. Side effects: creates an {@link ItemEntity} in the
     * world when the machine level is a {@link ServerLevel}.</p>
     *
     * @param machine machine that supplies the level and drop position
     * @param stack   stack to drop; ignored when empty
     */
    private void dropItem(MBDMachine machine, ItemStack stack) {
        if (stack.isEmpty()) return;
        var level = machine.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = machine.getPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.6;
        double z = pos.getZ() + 0.5;
        ItemEntity entity = new ItemEntity(serverLevel, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        serverLevel.addFreshEntity(entity);
    }

    /**
     * Returns the server recipe manager used for recipe lookup.
     *
     * <p>Preconditions: must be called on the server side after the Minecraft
     * server exists. Side effects: none.</p>
     *
     * @return active server recipe manager
     */
    public RecipeManager getRecipeManager() {
        return Platform.getMinecraftServer().getRecipeManager();
    }

    /**
     * Advances recipe logic by one server tick.
     *
     * <p>Business goal: continue active recipes, find new recipes periodically,
     * manage failed-match retries, tick fuel burn time, and cancel async search
     * while suspended. Side effects: may mutate recipe status, progress, fuel
     * counters, pending async search state, and machine callbacks.</p>
     */
    public void serverTick() {
        if (!isSuspend()) {
            if (!isIdle() && lastRecipe != null) {
                if (progress < duration) {
                    handleRecipeWorking();
                }
                if (isIdle() || duration == 0) {
                    // interrupt recipe
                } else if (progress >= duration) {
                    onRecipeFinish();
                }
            } else if (lastRecipe != null) {
                findAndHandleRecipe();
            } else if (getMachine().getOffsetTimer() % 5 == 0) {
                findAndHandleRecipe();
                if (lastFailedMatches != null) {
                    for (MBDRecipe match : lastFailedMatches) {
                        if (checkMatchedRecipeAvailable(match)) break;
                    }
                }
            }
        }
        if (fuelTime > 0) {
            fuelTime--;
            if (fuelTime == 0) {
                getMachine().onFuelBurningFinish(lastFuelRecipe);
            }
        } else {
            if (isSuspend()) {
                if (completableFuture != null) {
                    completableFuture.cancel(true);
                    completableFuture = null;
                }
            }
        }
    }

    /**
     * Revalidates a recipe candidate after machine-side recipe modification.
     *
     * <p>Business goal: let machines overclock, tier-check, or otherwise modify
     * a candidate before it becomes the active recipe. Side effects: may call
     * {@link #setupRecipe(MBDRecipe)}, update {@link #lastOriginRecipe}, clear
     * failed-match cache, and transition status to working through setup.</p>
     *
     * @param match candidate recipe from the recipe manager
     * @return {@code true} when the modified recipe was accepted and setup
     * reached {@link Status#WORKING}
     */
    protected boolean checkMatchedRecipeAvailable(MBDRecipe match) {
        var modified = machine.doModifyRecipe(match);
        if (modified != null) {
            if (modified.checkConditions(this).isSuccess() &&
                    modified.matchRecipe(machine).isSuccess() &&
                    modified.matchTickRecipe(machine).isSuccess()) {
                setupRecipe(modified);
            }
            if (lastRecipe != null && getStatus() == Status.WORKING) {
                lastOriginRecipe = match;
                lastFailedMatches = null;
                return true;
            }
        }
        return false;
    }

    /**
     * Processes the active recipe for one server tick.
     *
     * <p>Preconditions: {@link #lastRecipe} must be non-null. The method checks
     * deferred input availability, recipe conditions, fuel, and per-tick IO
     * before advancing progress. Side effects: may consume per-tick resources,
     * update waiting reason, damp progress, call machine working callbacks,
     * transition status, and invoke recipe pre/post working hooks when crossing
     * the working boundary.</p>
     */
    public void handleRecipeWorking() {
        Status last = this.status;
        assert lastRecipe != null;
        if (consumeInputsAfterWorking) {
            if (!lastRecipe.matchRecipe(machine).isSuccess()) {
                interruptRecipe();
                return;
            }
        }
        var result = lastRecipe.checkConditions(this);
        if (result.isSuccess()) {
            if (handleFuelRecipe()) {
                result = handleTickRecipe(lastRecipe);
                if (result.isSuccess()) {
                    setStatus(Status.WORKING);
                    if (machine.onWorking()) {
                        this.interruptRecipe();
                        return;
                    }
                    progress++;
                    totalContinuousRunningTime++;
                } else {
                    setWaiting(result.reason().get());
                }
            } else {
                setWaiting(Component.translatable("mbd2.recipe_logic.insufficient_fuel"));
            }
        } else {
            setWaiting(result.reason().get());
        }
        if (isWaiting()) {
            doDamping();
        }
        if (last == Status.WORKING && getStatus() != Status.WORKING) {
            lastRecipe.postWorking(machine);
        } else if (last != Status.WORKING && getStatus() == Status.WORKING) {
            lastRecipe.preWorking(machine);
        }
    }

    /**
     * Applies waiting-state progress damping when the machine allows it.
     *
     * <p>Side effects: decreases {@link #progress} by the machine damping value
     * and clamps it to {@code 0}.</p>
     */
    protected void doDamping() {
        if (progress > 0 && machine.dampingWhenWaiting()) {
            this.progress = Math.max(0, progress - getMachine().getRecipeDampingValue());
        }
    }

    /**
     * Searches candidate recipes for the owning machine.
     *
     * <p>Side effects are delegated to the recipe type's search
     * implementation. Returned recipes are candidates and still require machine
     * modification, condition checks, and IO matching.</p>
     *
     * @return candidate recipes in recipe-type-defined order
     */
    protected List<MBDRecipe> searchRecipe() {
        return machine.getRecipeType().searchRecipe(getRecipeManager(), this.machine);
    }


    /**
     * Finds a valid recipe and sets it up when the machine is idle or dirty.
     *
     * <p>Business goal: reuse a previously valid recipe when possible, otherwise
     * search synchronously or asynchronously depending on config. Side effects:
     * clears active recipe references before searching, may start/cancel/consume
     * async search futures, fills {@link #lastFailedMatches}, calls
     * {@link #setupRecipe(MBDRecipe)}, and resets {@link #recipeDirty}.</p>
     */
    public void findAndHandleRecipe() {
        lastFailedMatches = null;
        // try to execute last recipe if possible
        if (!recipeDirty && lastRecipe != null &&
                lastRecipe.matchRecipe(this.machine).isSuccess() &&
                lastRecipe.matchTickRecipe(this.machine).isSuccess() &&
                lastRecipe.checkConditions(this).isSuccess()) {
            MBDRecipe recipe = lastRecipe;
            lastRecipe = null;
            lastOriginRecipe = null;
            setupRecipe(recipe);
        } else { // try to find and handle a new recipe
            lastRecipe = null;
            lastOriginRecipe = null;
            if (completableFuture == null) {
                // try to search recipe in threads.
                if (ConfigHolder.asyncRecipeSearching) {
                    completableFuture = supplyAsyncSearchingTask();
                } else {
                    handleSearchingRecipes(searchRecipe());
                }
            } else if (completableFuture.isDone()) {
                var lastFuture = this.completableFuture;
                completableFuture = null;
                if (!lastFuture.isCancelled()) {
                    // if searching task is done, try to handle searched recipes.
                    try {
                        var matches = lastFuture.join().stream().filter(match -> match.matchRecipe(machine).isSuccess()).toList();
                        if (!matches.isEmpty()) {
                            handleSearchingRecipes(matches);
                        }
                    } catch (Throwable throwable) {
                        // if error occurred, schedule a new async task.
                        completableFuture = supplyAsyncSearchingTask();
                    }
                } else {
                    handleSearchingRecipes(searchRecipe());
                }
            }
        }
        recipeDirty = false;
    }

    /**
     * Starts an asynchronous candidate-recipe search.
     *
     * <p>Thread safety: the task runs on Minecraft's background executor and
     * should only perform recipe lookup. The caller remains responsible for
     * handling returned recipes and mutating machine state on the game thread.</p>
     *
     * @return future that completes with candidate recipes in search order
     */
    private CompletableFuture<List<MBDRecipe>> supplyAsyncSearchingTask() {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("Searching recipes", this::searchRecipe), Util.backgroundExecutor());
    }

    /**
     * Validates searched recipe candidates until one can be started.
     *
     * <p>Business goal: choose the first candidate that survives machine
     * modification, condition checks, and IO matching. Preconditions: called on
     * the logical server thread. Side effects: may start a recipe via
     * {@link #setupRecipe(MBDRecipe)} and records candidates that matched the
     * recipe search but could not currently run in {@link #lastFailedMatches}.</p>
     *
     * @param matches candidate recipes from synchronous or asynchronous search
     */
    private void handleSearchingRecipes(List<MBDRecipe> matches) {
        for (MBDRecipe match : matches) {
            // try to modify recipe by machine, such as overclock, tier checking.
            if (checkMatchedRecipeAvailable(match)) break;
            // cache matching recipes.
            if (lastFailedMatches == null) {
                lastFailedMatches = new ArrayList<>();
            }
            lastFailedMatches.add(match);
        }
    }

    /**
     * Ensures that a fuel source is available for recipe work.
     *
     * <p>Business goal: maintain one active fuel burn interval for recipe types
     * that require fuel. Preconditions: called on the logical server thread.
     * Side effects: may search fuel recipes, run machine fuel modification,
     * consume fuel inputs, notify the machine of consumed inputs, update
     * {@link #lastFuelRecipe}, and set {@link #fuelTime}/{@link #fuelMaxTime}.</p>
     *
     * @return {@code true} when no fuel is required, fuel is already burning, or
     * a fuel recipe was consumed; {@code false} when required fuel is unavailable
     */
    public boolean handleFuelRecipe() {
        if (!needFuel() || fuelTime > 0) return true;
        lastFuelRecipe = null;
        for (MBDRecipe recipe : machine.getRecipeType().searchFuelRecipe(getRecipeManager(), machine)) {
            recipe = getMachine().modifyFuelRecipe(recipe);
            if (recipe.checkConditions(this).isSuccess()) {
                var inputResult = recipe.handleRecipeIOWithResult(IO.IN, this.machine);
                if (!inputResult.success()) {
                    continue;
                }
                fuelMaxTime = recipe.duration;
                fuelTime = fuelMaxTime;
                lastFuelRecipe = recipe;
                machine.onRecipeInputsConsumed(recipe, inputResult.consumption(), false);
            }
            if (fuelTime > 0) return true;
        }
        return false;
    }

    /**
     * Applies per-tick recipe IO for recipes with tick ingredients or outputs.
     *
     * <p>Preconditions: {@code recipe} is the current active recipe candidate
     * and this method is called on the logical server thread. Side effects: when
     * tick matching succeeds, consumes tick inputs and emits tick outputs through
     * the machine's recipe capability handlers.</p>
     *
     * @param recipe recipe whose tick IO should be processed
     * @return {@link MBDRecipe.ActionResult#SUCCESS} when no tick IO is required
     * or matching succeeded; otherwise the failed match result with its reason
     */
    public MBDRecipe.ActionResult handleTickRecipe(MBDRecipe recipe) {
        if (recipe.hasTick()) {
            var result = recipe.matchTickRecipe(this.machine);
            if (result.isSuccess()) {
                recipe.handleTickRecipeIO(IO.IN, this.machine);
                recipe.handleTickRecipeIO(IO.OUT, this.machine);
            } else {
                return result;
            }
        }
        return MBDRecipe.ActionResult.SUCCESS;
    }

    /**
     * Starts a recipe and initializes its runtime progress state.
     *
     * <p>Business goal: centralize the transition from a matched recipe to an
     * actively working machine. Preconditions: {@code recipe} has already been
     * matched against the machine or is being scheduled deliberately by trusted
     * code. Side effects: may consume recipe inputs immediately, notify machine
     * callbacks, call recipe pre-working hooks, update last recipe state, reset
     * progress, set duration from {@link MBDRecipe#duration}, and transition to
     * {@link Status#WORKING}. If the machine vetoes work, progress and duration
     * are cleared and status returns to {@link Status#IDLE}.</p>
     *
     * @param recipe recipe to start; duration should be non-negative and in
     *               server ticks
     */
    public void setupRecipe(MBDRecipe recipe) {
        if (handleFuelRecipe()) {
            if (machine.beforeWorking(recipe)) {
                setStatus(Status.IDLE);
                progress = 0;
                duration = 0;
                consumeInputsAfterWorking = false;
                return;
            }
            recipe.preWorking(this.machine);
            consumeInputsAfterWorking = machine.consumeInputsAfterWorking(recipe);
            MBDRecipe.HandleResult inputResult = null;
            if (!consumeInputsAfterWorking) {
                inputResult = recipe.handleRecipeIOWithResult(IO.IN, this.machine);
            }
            if (consumeInputsAfterWorking || inputResult.success()) {
                if (inputResult != null) {
                    machine.onRecipeInputsConsumed(recipe, inputResult.consumption(), false);
                }
                recipeDirty = false;
                lastRecipe = recipe;
                setStatus(Status.WORKING);
                progress = 0;
                duration = recipe.duration;
            }
        }
    }

    /**
     * Changes the synced recipe status and notifies the owning machine.
     *
     * <p>Side effects: invokes
     * {@link IMachine#notifyRecipeStatusChanged(Status, Status)}, resets
     * continuous running time when leaving {@link Status#WORKING}, and clears the
     * waiting reason for non-waiting states.</p>
     *
     * @param status target status; must be non-null
     */
    public void setStatus(Status status) {
        if (this.status != status) {
            if (this.status == Status.WORKING) {
                this.totalContinuousRunningTime = 0;
            }
            machine.notifyRecipeStatusChanged(this.status, status);
            this.status = status;
            if (this.status != Status.WAITING) {
                waitingReason = null;
            }
        }
    }

    /**
     * Puts the machine in the waiting state with an optional human-readable
     * reason.
     *
     * <p>Business goal: expose blocked recipe progress to UI and machine hooks.
     * Side effects: transitions to {@link Status#WAITING}, stores the reason,
     * and invokes {@link IMachine#onWaiting()}.</p>
     *
     * @param reason optional blocked-progress reason; {@code null} means no
     *               specific reason is available
     */
    public void setWaiting(@Nullable Component reason) {
        setStatus(Status.WAITING);
        waitingReason = reason;
        machine.onWaiting();
    }

    /**
     * Marks the cached active recipe as requiring a fresh search or recheck.
     *
     * <p>Side effects: sets {@link #recipeDirty}; the next recipe search will
     * not blindly reuse {@link #lastRecipe}.</p>
     */
    public void markLastRecipeDirty() {
        this.recipeDirty = true;
    }

    /**
     * Returns whether the machine is currently advancing recipe progress.
     *
     * @return {@code true} only in {@link Status#WORKING}
     */
    public boolean isWorking() {
        return status == Status.WORKING;
    }

    /**
     * Returns whether no recipe is currently active.
     *
     * @return {@code true} only in {@link Status#IDLE}
     */
    public boolean isIdle() {
        return status == Status.IDLE;
    }

    /**
     * Returns whether recipe progress is blocked but the recipe is retained.
     *
     * @return {@code true} only in {@link Status#WAITING}
     */
    public boolean isWaiting() {
        return status == Status.WAITING;
    }

    /**
     * Returns whether recipe work has been externally disabled.
     *
     * @return {@code true} only in {@link Status#SUSPEND}
     */
    public boolean isSuspend() {
        return status == Status.SUSPEND;
    }

    /**
     * Enables or suspends recipe work for the machine.
     *
     * <p>Business goal: let redstone, UI controls, or other machine policy pause
     * recipe execution without discarding resumable progress. Side effects:
     * transitions to {@link Status#SUSPEND} when disabled, or restores
     * {@link Status#WORKING}/{@link Status#IDLE} when enabled based on cached
     * recipe progress.</p>
     *
     * @param isWorkingAllowed {@code true} to allow work to resume; {@code false}
     *                         to suspend current work
     */
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (!isWorkingAllowed) {
            setStatus(Status.SUSPEND);
        } else {
            if (lastRecipe != null && duration > 0) {
                setStatus(Status.WORKING);
            } else {
                setStatus(Status.IDLE);
            }
        }
    }

    /**
     * Returns the current recipe duration.
     *
     * @return maximum progress in server ticks; {@code 0} when no recipe is
     * active
     */
    public int getMaxProgress() {
        return duration;
    }

    /**
     * Returns whether the machine should be treated as recipe-active.
     *
     * @return {@code true} while working, waiting, or suspended with a retained
     * recipe and positive duration
     */
    public boolean isActive() {
        return isWorking() || isWaiting() || (isSuspend() && lastRecipe != null && duration > 0);
    }

    /**
     * Legacy waiting-state alias kept for API compatibility.
     *
     * @return {@code true} when the machine is waiting; energy is no longer the
     * only possible wait reason
     * @deprecated use {@link #isWaiting()} and {@link #getWaitingReason()} to
     * distinguish blocked recipe causes
     */
    @Deprecated
    public boolean isHasNotEnoughEnergy() {
        return isWaiting();
    }

    /**
     * Completes the active recipe and prepares the next recipe cycle.
     *
     * <p>Business goal: commit recipe completion atomically from the perspective
     * of machine state: finish callbacks, delayed input consumption, output IO,
     * optional recipe remodification, and immediate restart all happen here.
     * Preconditions: called on the logical server thread after
     * {@link #progress} reaches {@link #duration}. Side effects: may consume
     * delayed inputs, emit outputs, call recipe and machine completion hooks,
     * update cached recipe references, mark recipes dirty, start the next recipe,
     * or reset status/progress/duration to idle.</p>
     */
    public void onRecipeFinish() {
        if (lastRecipe != null) {
            machine.afterWorking();
            if (consumeInputsAfterWorking) {
                var inputResult = lastRecipe.handleRecipeIOWithResult(IO.IN, this.machine);
                consumeInputsAfterWorking = false;
                machine.onRecipeInputsConsumed(lastRecipe, inputResult.consumption(), true);
                machine.onConsumeInputsAfterWorking(inputResult.consumption());
            }
            lastRecipe.postWorking(this.machine);
            lastRecipe.handleRecipeIO(IO.OUT, this.machine);
            machine.onRecipeFinish();
            if (machine.alwaysReSearchRecipe()) {
                markLastRecipeDirty();
            }
            if (!recipeDirty && machine.alwaysTryModifyRecipe()) {
                if (lastOriginRecipe != null) {
                    var modified = machine.doModifyRecipe(lastOriginRecipe);
                    if (modified == null) {
                        markLastRecipeDirty();
                    } else {
                        lastRecipe = modified;
                    }
                } else {
                    markLastRecipeDirty();
                }
            }
            // try it again
            if (!recipeDirty &&
                    lastRecipe.matchRecipe(this.machine).isSuccess() &&
                    lastRecipe.matchTickRecipe(this.machine).isSuccess() &&
                    lastRecipe.checkConditions(this).isSuccess()) {
                setupRecipe(lastRecipe);
            } else {
                setStatus(Status.IDLE);
                progress = 0;
                duration = 0;
            }
        }
    }

    /**
     * Interrupts the current recipe without performing completion IO.
     *
     * <p>Business goal: abort work because the machine was reset, invalidated,
     * or can no longer continue. Preconditions: should be called on the logical
     * server thread. Side effects: calls working cleanup hooks when applicable,
     * posts the recipe's working-end hook, sets status to idle, clears progress
     * and duration, and cancels delayed input consumption. Inputs already
     * consumed before the interruption are not restored and outputs are not
     * produced.</p>
     */
    public void interruptRecipe() {
        if (lastRecipe != null) {
            if (!isIdle()) {
                machine.afterWorking();
            }
            lastRecipe.postWorking(this.machine);
            setStatus(Status.IDLE);
            progress = 0;
            duration = 0;
            consumeInputsAfterWorking = false;
        }
    }

    /**
     * Handles machine invalidation while preserving persisted state.
     *
     * <p>Side effects: if a recipe is actively working, invokes the recipe
     * post-working hook so transient working effects can be cleaned up. This
     * method does not reset progress, consume IO, or change status.</p>
     */
    public void inValid() {
        if (lastRecipe != null && isWorking()) {
            lastRecipe.postWorking(machine);
        }
    }

}
