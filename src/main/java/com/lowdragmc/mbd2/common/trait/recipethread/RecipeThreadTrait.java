package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Adds multiple logical recipe lanes to a machine.
 *
 * <p>Thread id {@code 0} is always the machine's original {@link RecipeLogic}; ids {@code 1..n} are
 * {@link ThreadedRecipeLogic} instances stored by this trait. The lanes are not Java background threads: they are
 * coordinated and ticked from the server tick, with {@link RecipeThreadContext} marking callbacks so recipe
 * selection events can apply lane-specific allowlists and blocklists.</p>
 *
 * <p>The trait owns scheduling side effects such as creating/removing extra lanes when the definition changes,
 * assigning candidate recipes to lanes, and preventing duplicate recipe starts when configured.</p>
 */
public class RecipeThreadTrait implements ITrait {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MBDMachine machine;
    private final RecipeThreadTraitDefinition definition;

    @DropSaved
    @Persisted
    private final List<ThreadedRecipeLogic> extraThreads = new ArrayList<>();

    private boolean allowlistDirty = true;
    private boolean lastOriginalWorking;
    private boolean lastAllowSameRecipe;
    private List<String> assignedRecipeIdLowercaseByThread = List.of();
    private List<String> candidateRecipeIdsLowercase = List.of();
    private int lastCandidateRecipeCount;
    private boolean tickFailureLogged;
    private List<RecipeThreadFilter> threadRecipeFilters = List.of();
    private long cachedRecipeFilterRevision = Long.MIN_VALUE;
    private long appliedRecipeFilterRevision = Long.MIN_VALUE;
    private long appliedThreadConfigRevision = Long.MIN_VALUE;
    private int appliedRecipeFilterThreadCount = -1;

    /**
     * Creates a recipe-thread coordinator for one machine and synchronizes its lane count.
     *
     * @param machine    machine whose recipe logic will be coordinated
     * @param definition persisted definition containing lane count, texts, and recipe filters
     */
    public RecipeThreadTrait(MBDMachine machine, RecipeThreadTraitDefinition definition) {
        this.machine = machine;
        this.definition = definition;
        this.lastAllowSameRecipe = definition.allowSameRecipe;
        updateThreads();
    }

    /**
     * Requests recipe lane assignments to be recomputed before the next tick/search.
     */
    public void markRecipeAssignmentDirty() {
        allowlistDirty = true;
    }

    /**
     * Consumes candidates already found by a real recipe-logic search.
     *
     * <p>Only exhaustive searches replace the shared candidate snapshot. Restricted lane searches are deliberately
     * ignored because they contain at most one assigned id and would otherwise collapse assignments for other lanes.
     * The assignment is refreshed synchronously so the same candidate batch is filtered with the new lane plan.</p>
     *
     * @param source     logic that initiated the search
     * @param candidates matching recipes produced by that search
     * @param exhaustive whether every recipe candidate was considered before matching
     */
    public void onRecipeSearchResults(RecipeLogic source, List<MBDRecipe> candidates, boolean exhaustive) {
        if (!exhaustive || !ownsRecipeLogic(source)) return;
        try {
            List<String> candidateIds = normalizeCandidateRecipeIds(candidates);
            if (!candidateIds.equals(candidateRecipeIdsLowercase)) {
                candidateRecipeIdsLowercase = candidateIds;
                allowlistDirty = true;
            }
            if (allowlistDirty) {
                updateThreadRecipeAllowlists();
                allowlistDirty = false;
            }
        } catch (RuntimeException e) {
            handleTickFailure("recipe search result", e);
        }
    }

    /**
     * Synchronizes extra lane count and recomputes assignment allowlists when necessary.
     *
     * <p>This method is safe to call from event hooks during recipe search. Runtime exceptions are logged once and
     * suppressed to avoid repeatedly breaking machine ticks when an upstream simulation path fails.</p>
     */
    public void ensureRecipeAssignment() {
        try {
            refreshSchedulingConfig();
            if (updateThreads()) {
                allowlistDirty = true;
            }
            refreshThreadRecipeFilters(Math.max(1, definition.maxThreads));
            if (allowlistDirty) {
                updateThreadRecipeAllowlists();
                allowlistDirty = false;
            }
        } catch (RuntimeException e) {
            handleTickFailure("ensureRecipeAssignment", e);
        }
    }

    /**
     * Reports whether multiple lanes may run the same recipe id concurrently.
     *
     * @return {@code true} when duplicate recipe ids are allowed
     */
    public boolean isAllowSameRecipe() {
        return definition.allowSameRecipe;
    }

    /**
     * Returns the number of distinct recipe ids seen during the last assignment refresh.
     *
     * @return candidate recipe count, or {@code 0} when assignments are disabled or no recipe matched
     */
    public int getLastCandidateRecipeCount() {
        return lastCandidateRecipeCount;
    }

    /**
     * Returns the current recipe id assignment by thread id.
     *
     * <p>Index {@code 0} represents the base machine logic. Empty strings mean no recipe is assigned. The returned
     * list is a defensive copy and cannot mutate this trait.</p>
     *
     * @return immutable assignment snapshot
     */
    public List<String> getAssignedRecipeIdLowercaseByThread() {
        return assignedRecipeIdLowercaseByThread.isEmpty() ? List.of() : List.copyOf(assignedRecipeIdLowercaseByThread);
    }

    @Override
    public MBDMachine getMachine() {
        return machine;
    }

    @Override
    public TraitDefinition getDefinition() {
        return definition;
    }

    /**
     * Resizes the persisted extra recipe lane list to match the definition.
     *
     * <p>{@code maxThreads} is clamped to at least one logical lane. Only extra lanes are stored here, so the target
     * list size is {@code maxThreads - 1}. Older persisted data that accidentally contains thread id {@code 0} in the
     * extra list is discarded.</p>
     */
    private boolean updateThreads() {
        int targetCount = Math.max(1, definition.maxThreads);
        int targetExtra = Math.max(0, targetCount - 1);
        boolean changed = false;

        if (!extraThreads.isEmpty() && extraThreads.get(0).getThreadId() == 0) {
            extraThreads.clear();
            changed = true;
        }

        while (extraThreads.size() < targetExtra) {
            extraThreads.add(new ThreadedRecipeLogic(machine, extraThreads.size() + 1));
            changed = true;
        }

        while (extraThreads.size() > targetExtra) {
            extraThreads.remove(extraThreads.size() - 1);
            changed = true;
        }
        return changed;
    }

    /**
     * Ticks all extra recipe lanes and refreshes recipe assignment state.
     *
     * <p>The base machine recipe logic is ticked by the normal machine path. This method handles only additional
     * lanes, applying definition config first, then setting external duplicate-prevention blocklists around each
     * lane's {@link ThreadedRecipeLogic#serverTick()} call. Assignment refreshes are intentionally repeated after
     * lanes stop so waiting lanes can receive newly available recipes in the same server tick.</p>
     */
    @Override
    public void serverTick() {
        try {
            RecipeThreadContext.clearIfMachine(machine);
            refreshSchedulingConfig();
            boolean originalWorking = machine.getRecipeLogic().isWorking();
            if (lastOriginalWorking != originalWorking) {
                allowlistDirty = true;
            }
            lastOriginalWorking = originalWorking;
            boolean threadsChanged = updateThreads();
            if (threadsChanged) {
                allowlistDirty = true;
            }
            int totalThreads = Math.max(1, definition.maxThreads);
            List<RecipeThreadFilter> filters = refreshThreadRecipeFilters(totalThreads);
            boolean configurationNeedsApplying = threadsChanged
                    || appliedRecipeFilterRevision != cachedRecipeFilterRevision
                    || appliedThreadConfigRevision != definition.getThreadConfigRevision()
                    || appliedRecipeFilterThreadCount != totalThreads;
            for (ThreadedRecipeLogic logic : extraThreads) {
                applyDefinitionConfig(logic, filters.get(logic.getThreadId()), configurationNeedsApplying);
            }
            if (configurationNeedsApplying) {
                appliedRecipeFilterRevision = cachedRecipeFilterRevision;
                appliedThreadConfigRevision = definition.getThreadConfigRevision();
                appliedRecipeFilterThreadCount = totalThreads;
            }

            if (allowlistDirty) {
                updateThreadRecipeAllowlists();
                allowlistDirty = false;
            }

            Set<String> runningRecipeIdsLowercase = collectRunningRecipeIdsLowercase();
            for (ThreadedRecipeLogic logic : extraThreads) {
                boolean wasWorking = logic.isWorking();
                if (!definition.allowSameRecipe) {
                    logic.setExternalRecipeBlocklist(runningRecipeIdsLowercase);
                } else {
                    logic.setExternalRecipeBlocklist(Set.of());
                }
                try {
                    RecipeThreadContext.runWith(machine, logic.getThreadId(), logic::serverTick);
                } catch (RuntimeException e) {
                    handleTickFailure("thread " + logic.getThreadId() + " serverTick", e);
                }
                if (wasWorking && !logic.isWorking()) {
                    allowlistDirty = true;
                }
                if (!definition.allowSameRecipe) {
                    String id = getCurrentRecipeIdLowercase(logic);
                    if (!id.isEmpty()) {
                        runningRecipeIdsLowercase.add(id);
                    }
                }
            }

            if (allowlistDirty) {
                updateThreadRecipeAllowlists();
                allowlistDirty = false;
            }
        } catch (RuntimeException e) {
            handleTickFailure("serverTick", e);
        }
    }

    private void handleTickFailure(String stage, RuntimeException error) {
        if (!tickFailureLogged) {
            tickFailureLogged = true;
            Object machineId;
            try {
                machineId = machine.getBlockState().getBlock();
            } catch (RuntimeException ignored) {
                machineId = "<unknown>";
            }
            LOGGER.warn("[mbd2] {} failed for machine {} at {}; suppressing further reports for this trait. Likely caused by an upstream simulate-path bug (e.g. hotai transformer + ItemSlotCapabilityTrait). Original error: {}",
                    stage, machineId, machine.getPos(), error.toString(), error);
        }
    }

    /**
     * Chooses one recipe logic for compact external displays.
     *
     * <p>The base machine logic wins while working or waiting, otherwise the first working extra lane wins, then the
     * first waiting extra lane. This preserves vanilla-style display priority for the original recipe logic.</p>
     *
     * @return recipe logic to show in compact external UI integrations
     */
    public RecipeLogic getRecipeLogicForExternalDisplay() {
        RecipeLogic machineLogic = machine.getRecipeLogic();
        if (machineLogic.isWorking() || machineLogic.isWaiting()) return machineLogic;

        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWorking()) return logic;
        }
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWaiting()) return logic;
        }
        return machineLogic;
    }

    /**
     * Chooses one recipe logic for Jade's primary display line.
     *
     * <p>Extra working lanes are preferred so parallel work is visible even when the base lane is idle. Waiting lanes
     * are then considered before falling back to the base machine logic.</p>
     *
     * @return recipe logic to expose to Jade
     */
    public RecipeLogic getRecipeLogicForJadeDisplay() {
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWorking()) return logic;
        }
        RecipeLogic machineLogic = machine.getRecipeLogic();
        if (machineLogic.isWorking()) return machineLogic;
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWaiting()) return logic;
        }
        if (machineLogic.isWaiting()) return machineLogic;
        return machineLogic;
    }

    /**
     * Returns every lane for detailed Jade display.
     *
     * @return ordered list beginning with thread id {@code 0}
     */
    public List<RecipeLogic> getThreadLogicsForJadeDetail() {
        return getRecipeLogics();
    }

    /**
     * Returns all recipe logic lanes owned by the machine.
     *
     * @return new ordered list beginning with the base machine logic followed by extra lanes
     */
    public List<RecipeLogic> getRecipeLogics() {
        List<RecipeLogic> logics = new ArrayList<>(1 + extraThreads.size());
        logics.add(machine.getRecipeLogic());
        logics.addAll(extraThreads);
        return logics;
    }

    /**
     * Looks up a recipe logic by logical thread id.
     *
     * @param threadId {@code 0} for the base machine logic, {@code 1..n} for extra lanes
     * @return matching recipe logic, or {@code null} when the id is outside the enabled lane range
     */
    @Nullable
    public RecipeLogic getRecipeLogic(int threadId) {
        return getLogicByThreadId(threadId);
    }

    /**
     * Returns the recipe logic associated with the current recipe-thread context.
     *
     * <p>If no context is active, or the context id no longer maps to an enabled lane, the base machine logic is
     * returned.</p>
     *
     * @return current-context logic or the base machine logic
     */
    public RecipeLogic getCurrentRecipeLogic() {
        Integer threadId = RecipeThreadContext.isCurrentMachine(machine) ? RecipeThreadContext.getThreadId() : null;
        RecipeLogic logic = threadId == null ? null : getRecipeLogic(threadId);
        return logic == null ? machine.getRecipeLogic() : logic;
    }

    /**
     * Returns the configured maximum lane count after clamping.
     *
     * @return at least {@code 1}
     */
    public int getMaxThreads() {
        return Math.max(1, definition.maxThreads);
    }

    /**
     * Counts lanes currently executing recipes.
     *
     * @return number of working lanes in {@code 0..getMaxThreads()}
     */
    public int getRunningThreadsCount() {
        int count = machine.getRecipeLogic().isWorking() ? 1 : 0;
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWorking()) count++;
        }
        return count;
    }

    /**
     * Counts lanes currently waiting for recipe completion conditions.
     *
     * @return number of waiting lanes in {@code 0..getMaxThreads()}
     */
    public int getWaitingThreadsCount() {
        int count = machine.getRecipeLogic().isWaiting() ? 1 : 0;
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWaiting()) count++;
        }
        return count;
    }

    /**
     * Returns the configured status text for a lane.
     *
     * @param threadId logical lane id
     * @return localization key/literal text for idle, running, waiting, or disabled state
     */
    public String getThreadStatusText(int threadId) {
        RecipeLogic logic = getLogicByThreadId(threadId);
        if (logic == null) return "mbd2.gui.thread_disabled";
        String idleText = definition.getThreadIdleText(threadId);
        String runningText = definition.getThreadRunningText(threadId);
        String waitingText = definition.getThreadWaitingText(threadId);
        if (logic.isIdle()) {
            return !idleText.isEmpty() ? idleText : definition.defaultIdleText;
        } else if (logic.isWorking()) {
            return !runningText.isEmpty() ? runningText : definition.defaultRunningText;
        } else if (logic.isWaiting()) {
            return !waitingText.isEmpty() ? waitingText : definition.defaultWaitingText;
        }
        return definition.defaultIdleText;
    }

    /**
     * Returns progress text for compatibility with widgets that expect a string.
     *
     * @param threadId logical lane id
     * @return integer percent text while working, otherwise an empty string
     */
    public String getThreadProgressText(int threadId) {
        return getThreadProgressDigits(threadId);
    }

    /**
     * Returns whole-number progress percentage for a lane.
     *
     * @param threadId logical lane id
     * @return {@code "0"} through {@code "100"} while working, or an empty string when disabled/not working
     */
    public String getThreadProgressDigits(int threadId) {
        RecipeLogic logic = getLogicByThreadId(threadId);
        if (logic == null) return "";
        if (!logic.isWorking()) return "";
        return String.valueOf((int) (logic.getProgressPercent() * 100));
    }

    /**
     * Returns the recipe id shown in a lane hover tooltip.
     *
     * @param threadId logical lane id
     * @return full recipe id string, or an empty string when the lane is idle/disabled/unknown
     */
    public String getThreadHoverRecipeIdText(int threadId) {
        RecipeLogic logic = getLogicByThreadId(threadId);
        if (logic == null) return "";
        if (logic.isIdle()) return "";
        MBDRecipe recipe = logic.getLastOriginRecipe() != null ? logic.getLastOriginRecipe() : logic.getLastRecipe();
        if (recipe == null || recipe.getId() == null) return "";
        return recipe.getId().toString();
    }

    /**
     * Finds the recipe-thread trait attached to a concrete MBD machine.
     *
     * @param machine machine to inspect
     * @return attached trait, or {@code null} when absent
     */
    public static RecipeThreadTrait get(MBDMachine machine) {
        for (ITrait trait : machine.getAdditionalTraits()) {
            if (trait instanceof RecipeThreadTrait t) return t;
        }
        return null;
    }

    /**
     * Finds the recipe-thread trait attached to a generic machine interface.
     *
     * @param machine machine to inspect
     * @return attached trait when {@code machine} is an {@link MBDMachine}, otherwise {@code null}
     */
    @Nullable
    public static RecipeThreadTrait get(IMachine machine) {
        return machine instanceof MBDMachine mbdMachine ? get(mbdMachine) : null;
    }

    /**
     * Resolves the recipe logic for the current context of a machine.
     *
     * @param machine machine whose trait should be consulted
     * @return current-context logic when the machine has this trait, otherwise the base machine logic
     */
    public static RecipeLogic getCurrentRecipeLogic(MBDMachine machine) {
        RecipeThreadTrait trait = get(machine);
        return trait == null ? machine.getRecipeLogic() : trait.getCurrentRecipeLogic();
    }

    /**
     * Tests whether a recipe may run in a lane under current assignment and filter rules.
     *
     * <p>Recipes without ids and out-of-range thread ids are allowed so unrelated recipe paths are not blocked.
     * Otherwise the method enforces transient assignment, persisted blacklist/whitelist, and duplicate-running rules
     * when same-recipe execution is disabled.</p>
     *
     * @param threadId logical lane id
     * @param recipe   recipe candidate, possibly {@code null}
     * @return {@code true} when the recipe should remain available to the lane
     */
    public boolean isRecipeAllowedForThread(int threadId, MBDRecipe recipe) {
        if (recipe == null || recipe.getId() == null) return true;
        if (threadId < 0 || threadId >= Math.max(1, definition.maxThreads)) return true;
        String id = recipe.getId().toString().toLowerCase(Locale.ROOT);
        boolean recoveringAssignment = threadId > 0
                && threadId <= extraThreads.size()
                && extraThreads.get(threadId - 1).isRecoveringAssignment();
        if (!recoveringAssignment
                && !assignedRecipeIdLowercaseByThread.isEmpty()
                && threadId < assignedRecipeIdLowercaseByThread.size()) {
            String assigned = assignedRecipeIdLowercaseByThread.get(threadId);
            if (assigned != null && !assigned.isEmpty() && !assigned.equals(id)) return false;
        }
        RecipeThreadFilter filter = refreshThreadRecipeFilters(Math.max(1, definition.maxThreads)).get(threadId);
        if (!filter.allows(id)) {
            return false;
        }
        if (!definition.allowSameRecipe && isRecipeIdRunningInOtherThreads(id, threadId)) {
            return false;
        }
        return true;
    }

    private void applyDefinitionConfig(ThreadedRecipeLogic logic,
                                       RecipeThreadFilter filter,
                                       boolean configurationNeedsApplying) {
        if (!configurationNeedsApplying) return;
        int threadId = logic.getThreadId();
        logic.setIdleText(definition.getThreadIdleText(threadId));
        logic.setRunningText(definition.getThreadRunningText(threadId));
        logic.setWaitingText(definition.getThreadWaitingText(threadId));
        logic.replaceConfiguredRecipeFilters(filter);
    }

    private void updateThreadRecipeAllowlists() {
        int totalThreads = Math.max(1, definition.maxThreads);
        if (totalThreads <= 1) {
            assignedRecipeIdLowercaseByThread = List.of();
            lastCandidateRecipeCount = 0;
            applyAssignmentsToExtraThreads(List.of());
            return;
        }

        List<String> runningRecipeIdsByThread = collectRunningRecipeIdsByThread(totalThreads);
        RecipeThreadAssignmentPlanner.Assignment assignment = RecipeThreadAssignmentPlanner.plan(
                totalThreads,
                candidateRecipeIdsLowercase,
                assignedRecipeIdLowercaseByThread,
                runningRecipeIdsByThread,
                refreshThreadRecipeFilters(totalThreads),
                definition.allowSameRecipe);
        lastCandidateRecipeCount = assignment.candidateRecipeCount();
        if (assignment.recipeIdsByThread().isEmpty()) {
            assignedRecipeIdLowercaseByThread = List.of();
            applyAssignmentsToExtraThreads(List.of());
            return;
        }

        applyAssignmentsToExtraThreads(assignment.recipeIdsByThread());
        assignedRecipeIdLowercaseByThread = assignment.recipeIdsByThread();
    }

    private void applyAssignmentsToExtraThreads(List<String> recipeIdsByThread) {
        boolean discoveryAssigned = false;
        boolean needsExtraDiscovery = !candidateRecipeIdsLowercase.isEmpty()
                || !machine.getRecipeLogic().isIdle();
        for (int i = 0; i < extraThreads.size(); i++) {
            String recipeId = (i + 1) < recipeIdsByThread.size() ? recipeIdsByThread.get(i + 1) : "";
            boolean discoverWhenUnassigned = false;
            if ((recipeId == null || recipeId.isEmpty()) && needsExtraDiscovery && !discoveryAssigned) {
                discoverWhenUnassigned = true;
                discoveryAssigned = true;
            }
            extraThreads.get(i).setAssignedRecipeId(recipeId, discoverWhenUnassigned);
        }
    }

    private static List<String> normalizeCandidateRecipeIds(List<MBDRecipe> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<String> recipeIds = new ArrayList<>(candidates.size());
        Set<String> seen = new HashSet<>(Math.max(16, candidates.size()));
        for (MBDRecipe recipe : candidates) {
            if (recipe == null || recipe.getId() == null) continue;
            String recipeId = recipe.getId().toString().toLowerCase(Locale.ROOT);
            if (seen.add(recipeId)) {
                recipeIds.add(recipeId);
            }
        }
        return recipeIds.isEmpty() ? List.of() : List.copyOf(recipeIds);
    }

    private List<String> collectRunningRecipeIdsByThread(int totalThreads) {
        List<String> runningRecipeIds = new ArrayList<>(Collections.nCopies(totalThreads, ""));
        RecipeLogic original = machine.getRecipeLogic();
        if (original.isWorking()) {
            runningRecipeIds.set(0, getCurrentRecipeIdLowercase(original));
        }
        for (ThreadedRecipeLogic logic : extraThreads) {
            int threadId = logic.getThreadId();
            if (logic.isWorking() && threadId >= 1 && threadId < totalThreads) {
                runningRecipeIds.set(threadId, getCurrentRecipeIdLowercase(logic));
            }
        }
        return runningRecipeIds;
    }

    private List<RecipeThreadFilter> refreshThreadRecipeFilters(int totalThreads) {
        long definitionRevision = definition.getRecipeFilterRevision();
        if (cachedRecipeFilterRevision != definitionRevision || threadRecipeFilters.size() != totalThreads) {
            threadRecipeFilters = definition.createThreadRecipeFilters(totalThreads);
            cachedRecipeFilterRevision = definitionRevision;
            allowlistDirty = true;
        }
        return threadRecipeFilters;
    }

    private void refreshSchedulingConfig() {
        boolean allowSameRecipe = definition.allowSameRecipe;
        if (lastAllowSameRecipe != allowSameRecipe) {
            lastAllowSameRecipe = allowSameRecipe;
            allowlistDirty = true;
        }
    }

    private Set<String> collectRunningRecipeIdsLowercase() {
        Set<String> ids = new HashSet<>();
        RecipeLogic original = machine.getRecipeLogic();
        if (original.isWorking()) {
            String id = getCurrentRecipeIdLowercase(original);
            if (!id.isEmpty()) ids.add(id);
        }
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (!logic.isWorking()) continue;
            String id = getCurrentRecipeIdLowercase(logic);
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static String getCurrentRecipeIdLowercase(RecipeLogic logic) {
        MBDRecipe recipe = logic.getLastOriginRecipe() != null ? logic.getLastOriginRecipe() : logic.getLastRecipe();
        if (recipe == null || recipe.getId() == null) return "";
        return recipe.getId().toString().toLowerCase(Locale.ROOT);
    }

    private boolean isRecipeIdRunningInOtherThreads(String recipeIdLowercase, int currentThreadId) {
        if (recipeIdLowercase == null || recipeIdLowercase.isEmpty()) return false;
        RecipeLogic original = machine.getRecipeLogic();
        if (currentThreadId != 0 && original.isWorking()) {
            String id = getCurrentRecipeIdLowercase(original);
            if (!id.isEmpty() && id.equals(recipeIdLowercase)) return true;
        }
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.getThreadId() == currentThreadId) continue;
            if (!logic.isWorking()) continue;
            String id = getCurrentRecipeIdLowercase(logic);
            if (!id.isEmpty() && id.equals(recipeIdLowercase)) return true;
        }
        return false;
    }

    private RecipeLogic getLogicByThreadId(int threadId) {
        if (threadId == 0) return machine.getRecipeLogic();
        if (threadId < 1 || threadId > extraThreads.size()) return null;
        return extraThreads.get(threadId - 1);
    }

    private boolean ownsRecipeLogic(RecipeLogic logic) {
        if (logic == machine.getRecipeLogic()) return true;
        if (!(logic instanceof ThreadedRecipeLogic threaded)) return false;
        int threadId = threaded.getThreadId();
        return threadId >= 1
                && threadId <= extraThreads.size()
                && extraThreads.get(threadId - 1) == threaded;
    }
}
