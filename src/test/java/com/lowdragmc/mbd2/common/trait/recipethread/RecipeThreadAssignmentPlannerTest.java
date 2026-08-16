package com.lowdragmc.mbd2.common.trait.recipethread;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeThreadAssignmentPlannerTest {

    private static final String A = "mbd2:a";
    private static final String B = "mbd2:b";
    private static final String C = "mbd2:c";

    @Test
    void balancesDuplicateCandidatesInStableOrder() {
        var assignment = plan(5, List.of(A, B, A), List.of(), List.of(), allowAll(5), true);

        assertEquals(2, assignment.candidateRecipeCount());
        assertEquals(List.of(A, B, A, B, A), assignment.recipeIdsByThread());
    }

    @Test
    void leavesExcessThreadsUnassignedWhenDuplicateRecipesAreDisabled() {
        var assignment = plan(4, List.of(A, B), List.of(), List.of(), allowAll(4), false);

        assertEquals(List.of(A, B, "", ""), assignment.recipeIdsByThread());
    }

    @Test
    void appliesWhitelistAndBlacklistSnapshotsPerThread() {
        List<RecipeThreadFilter> filters = List.of(
                new RecipeThreadFilter(Set.of(B), Set.of()),
                new RecipeThreadFilter(Set.of(), Set.of(B)),
                RecipeThreadFilter.ALLOW_ALL);

        var assignment = plan(3, List.of(A, B), List.of(), List.of(), filters, false);

        assertEquals(List.of(B, A, ""), assignment.recipeIdsByThread());
    }

    @Test
    void preservesRunningRecipesAndFallsBackWhenStickyRecipeIsOccupied() {
        var assignment = plan(
                3,
                List.of(A, B, C),
                List.of("", A, B),
                List.of(A, "", ""),
                allowAll(3),
                false);

        assertEquals(List.of(A, B, C), assignment.recipeIdsByThread());
        assertEquals(3, new HashSet<>(assignment.recipeIdsByThread()).size());
    }

    @Test
    void retainsValidStickyAssignmentsWithinOneLaneOfBestLoad() {
        var assignment = plan(
                3,
                List.of(A, B),
                List.of(B, A, ""),
                List.of(),
                allowAll(3),
                true);

        assertEquals(List.of(B, A, A), assignment.recipeIdsByThread());
    }

    @Test
    void returnsEmptyAssignmentWhenNoUsableCandidateExists() {
        List<String> invalidCandidates = new ArrayList<>();
        invalidCandidates.add(null);
        invalidCandidates.add("");
        invalidCandidates.add(null);
        var assignment = plan(20, invalidCandidates, List.of(), List.of(), allowAll(20), true);

        assertEquals(0, assignment.candidateRecipeCount());
        assertEquals(List.of(), assignment.recipeIdsByThread());
    }

    private static RecipeThreadAssignmentPlanner.Assignment plan(int threadCount,
                                                                 List<String> candidateIds,
                                                                 List<String> previousIds,
                                                                 List<String> runningIds,
                                                                 List<RecipeThreadFilter> filters,
                                                                 boolean allowSameRecipe) {
        return RecipeThreadAssignmentPlanner.plan(
                threadCount, candidateIds, previousIds, runningIds, filters, allowSameRecipe);
    }

    private static List<RecipeThreadFilter> allowAll(int threadCount) {
        List<RecipeThreadFilter> filters = new ArrayList<>(threadCount);
        for (int threadId = 0; threadId < threadCount; threadId++) {
            filters.add(RecipeThreadFilter.ALLOW_ALL);
        }
        return filters;
    }
}
