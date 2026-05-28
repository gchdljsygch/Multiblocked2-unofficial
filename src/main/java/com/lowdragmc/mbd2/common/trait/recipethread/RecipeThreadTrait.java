package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RecipeThreadTrait implements ITrait {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MBDMachine machine;
    private final RecipeThreadTraitDefinition definition;

    @Persisted
    private final List<ThreadedRecipeLogic> extraThreads = new ArrayList<>();

    private boolean allowlistDirty = true;
    private boolean lastOriginalWorking;
    private List<String> assignedRecipeIdLowercaseByThread = List.of();
    private int lastCandidateRecipeCount;
    private boolean tickFailureLogged;

    public RecipeThreadTrait(MBDMachine machine, RecipeThreadTraitDefinition definition) {
        this.machine = machine;
        this.definition = definition;
        updateThreads();
    }

    public void markRecipeAssignmentDirty() {
        allowlistDirty = true;
    }

    public void ensureRecipeAssignment() {
        try {
            int beforeExtraCount = extraThreads.size();
            updateThreads();
            if (beforeExtraCount != extraThreads.size()) {
                allowlistDirty = true;
            }
            if (allowlistDirty) {
                updateThreadRecipeAllowlists();
                allowlistDirty = false;
            }
        } catch (RuntimeException e) {
            handleTickFailure("ensureRecipeAssignment", e);
        }
    }

    public boolean isAllowSameRecipe() {
        return definition.allowSameRecipe;
    }

    public int getLastCandidateRecipeCount() {
        return lastCandidateRecipeCount;
    }

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

    private void updateThreads() {
        int targetCount = Math.max(1, definition.maxThreads);
        int targetExtra = Math.max(0, targetCount - 1);

        if (!extraThreads.isEmpty() && extraThreads.get(0).getThreadId() == 0) {
            extraThreads.clear();
        }

        while (extraThreads.size() < targetExtra) {
            extraThreads.add(new ThreadedRecipeLogic(machine, extraThreads.size() + 1));
        }

        while (extraThreads.size() > targetExtra) {
            extraThreads.remove(extraThreads.size() - 1);
        }
    }

    @Override
    public void serverTick() {
        try {
            RecipeThreadContext.clearIfMachine(machine);
            if (machine.getOffsetTimer() % 5 == 0) {
                allowlistDirty = true;
            }
            boolean originalWorking = machine.getRecipeLogic().isWorking();
            if (lastOriginalWorking && !originalWorking) {
                allowlistDirty = true;
            }
            lastOriginalWorking = originalWorking;
            int beforeExtraCount = extraThreads.size();
            updateThreads();
            if (beforeExtraCount != extraThreads.size()) {
                allowlistDirty = true;
            }
            for (ThreadedRecipeLogic logic : extraThreads) {
                applyDefinitionConfig(logic);
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

    public List<RecipeLogic> getThreadLogicsForJadeDetail() {
        List<RecipeLogic> logics = new ArrayList<>(1 + extraThreads.size());
        logics.add(machine.getRecipeLogic());
        logics.addAll(extraThreads);
        return logics;
    }

    public int getMaxThreads() {
        return Math.max(1, definition.maxThreads);
    }

    public int getRunningThreadsCount() {
        int count = machine.getRecipeLogic().isWorking() ? 1 : 0;
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWorking()) count++;
        }
        return count;
    }

    public int getWaitingThreadsCount() {
        int count = machine.getRecipeLogic().isWaiting() ? 1 : 0;
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (logic.isWaiting()) count++;
        }
        return count;
    }

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

    public String getThreadProgressText(int threadId) {
        return getThreadProgressDigits(threadId);
    }

    public String getThreadProgressDigits(int threadId) {
        RecipeLogic logic = getLogicByThreadId(threadId);
        if (logic == null) return "";
        if (!logic.isWorking()) return "";
        return String.valueOf((int) (logic.getProgressPercent() * 100));
    }

    public String getThreadHoverRecipeIdText(int threadId) {
        RecipeLogic logic = getLogicByThreadId(threadId);
        if (logic == null) return "";
        if (logic.isIdle()) return "";
        MBDRecipe recipe = logic.getLastOriginRecipe() != null ? logic.getLastOriginRecipe() : logic.getLastRecipe();
        if (recipe == null || recipe.getId() == null) return "";
        return recipe.getId().toString();
    }

    public static RecipeThreadTrait get(MBDMachine machine) {
        for (ITrait trait : machine.getAdditionalTraits()) {
            if (trait instanceof RecipeThreadTrait t) return t;
        }
        return null;
    }

    public boolean isRecipeAllowedForThread(int threadId, MBDRecipe recipe) {
        if (recipe == null || recipe.getId() == null) return true;
        if (threadId < 0 || threadId >= Math.max(1, definition.maxThreads)) return true;
        String id = recipe.getId().toString().toLowerCase(Locale.ROOT);
        if (!assignedRecipeIdLowercaseByThread.isEmpty() && threadId < assignedRecipeIdLowercaseByThread.size()) {
            String assigned = assignedRecipeIdLowercaseByThread.get(threadId);
            if (assigned != null && !assigned.isEmpty() && !assigned.equals(id)) return false;
        }
        List<String> blacklist = definition.getThreadBlacklistIdsLowercase(threadId);
        if (!blacklist.isEmpty() && blacklist.contains(id)) {
            return false;
        }
        List<String> whitelist = definition.getThreadWhitelistIdsLowercase(threadId);
        if (!whitelist.isEmpty() && !whitelist.contains(id)) {
            return false;
        }
        if (!definition.allowSameRecipe && isRecipeIdRunningInOtherThreads(id, threadId)) {
            return false;
        }
        return true;
    }

    private void applyDefinitionConfig(ThreadedRecipeLogic logic) {
        int threadId = logic.getThreadId();
        logic.setIdleText(definition.getThreadIdleText(threadId));
        logic.setRunningText(definition.getThreadRunningText(threadId));
        logic.setWaitingText(definition.getThreadWaitingText(threadId));
        logic.getWhitelist().clear();
        for (String id : definition.getThreadWhitelistIdsLowercase(threadId)) {
            logic.getWhitelist().add(id);
        }
        logic.getBlacklist().clear();
        for (String id : definition.getThreadBlacklistIdsLowercase(threadId)) {
            logic.getBlacklist().add(id);
        }
    }

    private void updateThreadRecipeAllowlists() {
        int totalThreads = Math.max(1, definition.maxThreads);
        if (totalThreads <= 1) {
            assignedRecipeIdLowercaseByThread = List.of();
            lastCandidateRecipeCount = 0;
            for (ThreadedRecipeLogic logic : extraThreads) {
                logic.disableExternalRecipeAllowlist();
            }
            return;
        }

        List<MBDRecipe> matches = machine.getRecipeType().searchRecipe(machine.getRecipeLogic().getRecipeManager(), machine);
        List<String> recipeIdsLowercase = new ArrayList<>();
        for (MBDRecipe recipe : matches) {
            if (recipe == null || recipe.getId() == null) continue;
            recipeIdsLowercase.add(recipe.getId().toString().toLowerCase(Locale.ROOT));
        }

        recipeIdsLowercase = recipeIdsLowercase.stream().distinct().toList();
        lastCandidateRecipeCount = recipeIdsLowercase.size();
        if (recipeIdsLowercase.isEmpty()) {
            assignedRecipeIdLowercaseByThread = List.of();
            for (ThreadedRecipeLogic logic : extraThreads) {
                logic.disableExternalRecipeAllowlist();
            }
            return;
        }

        List<String> assigned = new ArrayList<>(Collections.nCopies(totalThreads, ""));
        Map<String, Integer> counts = new HashMap<>();

        String originalWorkingId = machine.getRecipeLogic().isWorking() ? getCurrentRecipeIdLowercase(machine.getRecipeLogic()) : "";
        if (!originalWorkingId.isEmpty()) {
            assigned.set(0, originalWorkingId);
            counts.put(originalWorkingId, counts.getOrDefault(originalWorkingId, 0) + 1);
        }
        for (ThreadedRecipeLogic logic : extraThreads) {
            if (!logic.isWorking()) continue;
            String id = getCurrentRecipeIdLowercase(logic);
            if (id.isEmpty()) continue;
            int threadId = logic.getThreadId();
            if (threadId >= 1 && threadId < totalThreads) {
                assigned.set(threadId, id);
                counts.put(id, counts.getOrDefault(id, 0) + 1);
            }
        }

        for (int threadId = 0; threadId < totalThreads; threadId++) {
            if (!assigned.get(threadId).isEmpty()) continue;
            String sticky = "";
            if (sticky != null && !sticky.isEmpty() && recipeIdsLowercase.contains(sticky) && isRecipeIdAllowedByThreadConfig(threadId, sticky)) {
                int stickyCount = counts.getOrDefault(sticky, 0);
                if (!definition.allowSameRecipe && stickyCount > 0) {
                    continue;
                }
                int bestCount = Integer.MAX_VALUE;
                for (String id : recipeIdsLowercase) {
                    if (!isRecipeIdAllowedByThreadConfig(threadId, id)) continue;
                    if (!definition.allowSameRecipe && counts.getOrDefault(id, 0) > 0) continue;
                    bestCount = Math.min(bestCount, counts.getOrDefault(id, 0));
                }
                if (stickyCount <= bestCount + 1) {
                    assigned.set(threadId, sticky);
                    counts.put(sticky, stickyCount + 1);
                    continue;
                }
            }

            String bestId = "";
            int bestCount = Integer.MAX_VALUE;
            for (String id : recipeIdsLowercase) {
                if (!isRecipeIdAllowedByThreadConfig(threadId, id)) continue;
                int c = counts.getOrDefault(id, 0);
                if (!definition.allowSameRecipe && c > 0) continue;
                if (c < bestCount) {
                    bestCount = c;
                    bestId = id;
                }
            }
            if (!bestId.isEmpty()) {
                assigned.set(threadId, bestId);
                counts.put(bestId, counts.getOrDefault(bestId, 0) + 1);
            }
        }

        for (int i = 0; i < extraThreads.size(); i++) {
            String id = (i + 1) < assigned.size() ? assigned.get(i + 1) : "";
            extraThreads.get(i).setExternalRecipeAllowlist(id == null || id.isEmpty() ? Set.of() : Set.of(id));
        }
        assignedRecipeIdLowercaseByThread = assigned;
    }

    private boolean isRecipeIdAllowedByThreadConfig(int threadId, String idLowercase) {
        if (idLowercase == null || idLowercase.isEmpty()) return true;
        List<String> blacklist = definition.getThreadBlacklistIdsLowercase(threadId);
        if (!blacklist.isEmpty() && blacklist.contains(idLowercase)) return false;
        List<String> whitelist = definition.getThreadWhitelistIdsLowercase(threadId);
        if (!whitelist.isEmpty() && !whitelist.contains(idLowercase)) return false;
        return true;
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
}
