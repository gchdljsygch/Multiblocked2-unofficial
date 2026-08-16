package com.lowdragmc.mbd2.common.trait.recipethread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes stable, balanced recipe-id assignments without touching machine state.
 *
 * <p>The planner keeps candidate order stable, uses indexed integer counters in the hot loop, and stops scanning as
 * soon as it finds an eligible zero-load candidate. This keeps the common case bounded by active lane count rather
 * than total recipe count while leaving room for additional assignment policies.</p>
 */
final class RecipeThreadAssignmentPlanner {

    private static final String UNASSIGNED = "";

    private RecipeThreadAssignmentPlanner() {
    }

    static Assignment plan(int requestedThreadCount,
                           List<String> rawCandidateRecipeIdsLowercase,
                           List<String> previousRecipeIdsByThread,
                           List<String> runningRecipeIdsByThread,
                           List<RecipeThreadFilter> filtersByThread,
                           boolean allowSameRecipe) {
        int threadCount = Math.max(1, requestedThreadCount);
        CandidateIndex candidates = CandidateIndex.create(rawCandidateRecipeIdsLowercase);
        if (candidates.recipeIds().isEmpty()) {
            return new Assignment(List.of(), 0);
        }

        String[] assigned = new String[threadCount];
        Arrays.fill(assigned, UNASSIGNED);
        int[] assignmentCounts = new int[candidates.recipeIds().size()];
        seedRunningAssignments(assigned, assignmentCounts, candidates.indexByRecipeId(), runningRecipeIdsByThread);

        for (int threadId = 0; threadId < threadCount; threadId++) {
            if (!assigned[threadId].isEmpty()) continue;

            RecipeThreadFilter filter = filterForThread(filtersByThread, threadId);
            int bestCandidate = findBestCandidate(candidates.recipeIds(), assignmentCounts, filter, allowSameRecipe);
            if (bestCandidate < 0) continue;

            int stickyCandidate = candidateIndex(previousRecipeIdsByThread, threadId, candidates.indexByRecipeId());
            int selectedCandidate = bestCandidate;
            if (stickyCandidate >= 0 && filter.allows(candidates.recipeIds().get(stickyCandidate))) {
                int stickyCount = assignmentCounts[stickyCandidate];
                if ((allowSameRecipe || stickyCount == 0)
                        && stickyCount <= assignmentCounts[bestCandidate] + 1) {
                    selectedCandidate = stickyCandidate;
                }
            }

            assigned[threadId] = candidates.recipeIds().get(selectedCandidate);
            assignmentCounts[selectedCandidate]++;
        }

        return new Assignment(List.copyOf(Arrays.asList(assigned)), candidates.recipeIds().size());
    }

    private static void seedRunningAssignments(String[] assigned,
                                               int[] assignmentCounts,
                                               Map<String, Integer> candidateIndexes,
                                               List<String> runningRecipeIdsByThread) {
        if (runningRecipeIdsByThread == null || runningRecipeIdsByThread.isEmpty()) return;
        int limit = Math.min(assigned.length, runningRecipeIdsByThread.size());
        for (int threadId = 0; threadId < limit; threadId++) {
            String runningRecipeId = runningRecipeIdsByThread.get(threadId);
            if (runningRecipeId == null || runningRecipeId.isEmpty()) continue;
            assigned[threadId] = runningRecipeId;
            Integer candidateIndex = candidateIndexes.get(runningRecipeId);
            if (candidateIndex != null) {
                assignmentCounts[candidateIndex]++;
            }
        }
    }

    private static int candidateIndex(List<String> recipeIdsByThread,
                                      int threadId,
                                      Map<String, Integer> candidateIndexes) {
        if (recipeIdsByThread == null || threadId >= recipeIdsByThread.size()) return -1;
        String recipeId = recipeIdsByThread.get(threadId);
        if (recipeId == null || recipeId.isEmpty()) return -1;
        Integer candidateIndex = candidateIndexes.get(recipeId);
        return candidateIndex == null ? -1 : candidateIndex;
    }

    private static RecipeThreadFilter filterForThread(List<RecipeThreadFilter> filtersByThread, int threadId) {
        if (filtersByThread == null || threadId >= filtersByThread.size()) return RecipeThreadFilter.ALLOW_ALL;
        RecipeThreadFilter filter = filtersByThread.get(threadId);
        return filter == null ? RecipeThreadFilter.ALLOW_ALL : filter;
    }

    private static int findBestCandidate(List<String> candidateRecipeIds,
                                         int[] assignmentCounts,
                                         RecipeThreadFilter filter,
                                         boolean allowSameRecipe) {
        int bestCandidate = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < candidateRecipeIds.size(); candidate++) {
            if (!filter.allows(candidateRecipeIds.get(candidate))) continue;
            int count = assignmentCounts[candidate];
            if (!allowSameRecipe && count > 0) continue;
            if (count < bestCount) {
                bestCandidate = candidate;
                bestCount = count;
                if (count == 0) break;
            }
        }
        return bestCandidate;
    }

    record Assignment(List<String> recipeIdsByThread, int candidateRecipeCount) {
    }

    private record CandidateIndex(List<String> recipeIds, Map<String, Integer> indexByRecipeId) {

        private static CandidateIndex create(List<String> rawRecipeIds) {
            if (rawRecipeIds == null || rawRecipeIds.isEmpty()) {
                return new CandidateIndex(List.of(), Map.of());
            }
            var recipeIds = new ArrayList<String>(rawRecipeIds.size());
            var indexes = new HashMap<String, Integer>(Math.max(16, rawRecipeIds.size()));
            for (String recipeId : rawRecipeIds) {
                if (recipeId == null || recipeId.isEmpty() || indexes.containsKey(recipeId)) continue;
                indexes.put(recipeId, recipeIds.size());
                recipeIds.add(recipeId);
            }
            return new CandidateIndex(recipeIds, indexes);
        }
    }
}
