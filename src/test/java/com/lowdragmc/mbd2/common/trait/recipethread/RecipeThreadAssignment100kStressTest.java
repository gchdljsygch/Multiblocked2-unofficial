package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.mbd2.performance.StressTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Exercises candidate indexing, filtering, balancing, and sticky refreshes at the repository's 100k-machine scale.
 */
@Tag("performance")
class RecipeThreadAssignment100kStressTest {

    private static final int THREAD_COUNT = 20;
    private static final int DISTINCT_CANDIDATE_COUNT = 128;
    private static final int WARMUP_UPDATES = 4_096;

    @Test
    void updatesRecipeThreadAssignmentsForOneHundredThousandMachines() {
        StressTestSupport.requireStressScale();
        List<String> candidates = createCandidatesWithDuplicates();
        List<RecipeThreadFilter> filters = createFilters();
        List<String> noRunningRecipes = emptyAssignments();
        List<String> runningRecipes = createRunningAssignments();

        RecipeThreadAssignmentPlanner.Assignment initial = RecipeThreadAssignmentPlanner.plan(
                THREAD_COUNT, candidates, List.of(), noRunningRecipes, filters, false);
        assertEquals(DISTINCT_CANDIDATE_COUNT, initial.candidateRecipeCount());
        assertEquals(THREAD_COUNT, initial.recipeIdsByThread().size());
        assertEquals(THREAD_COUNT, new HashSet<>(initial.recipeIdsByThread()).size());

        RecipeThreadAssignmentPlanner.Assignment withRunningRecipes = RecipeThreadAssignmentPlanner.plan(
                THREAD_COUNT, candidates, initial.recipeIdsByThread(), runningRecipes, filters, false);
        assertEquals(runningRecipes.subList(0, 5), withRunningRecipes.recipeIdsByThread().subList(0, 5));
        assertEquals(THREAD_COUNT, new HashSet<>(withRunningRecipes.recipeIdsByThread()).size());

        warmUp(candidates, filters, noRunningRecipes, runningRecipes, initial.recipeIdsByThread());

        long[] checksum = {0};
        StressTestSupport.measure(
                "recipe-thread-allowlist-cold",
                StressTestSupport.MACHINE_COUNT,
                1,
                (long) StressTestSupport.MACHINE_COUNT * THREAD_COUNT,
                () -> {
                    for (int machine = 0; machine < StressTestSupport.MACHINE_COUNT; machine++) {
                        var assignment = RecipeThreadAssignmentPlanner.plan(
                                THREAD_COUNT, candidates, List.of(), noRunningRecipes, filters, false);
                        checksum[0] += assignment.recipeIdsByThread().get(machine % THREAD_COUNT).hashCode();
                    }
                });

        StressTestSupport.measure(
                "recipe-thread-allowlist-running",
                StressTestSupport.MACHINE_COUNT,
                1,
                (long) StressTestSupport.MACHINE_COUNT * THREAD_COUNT,
                () -> {
                    for (int machine = 0; machine < StressTestSupport.MACHINE_COUNT; machine++) {
                        var assignment = RecipeThreadAssignmentPlanner.plan(
                                THREAD_COUNT,
                                candidates,
                                initial.recipeIdsByThread(),
                                runningRecipes,
                                filters,
                                false);
                        checksum[0] += assignment.recipeIdsByThread().get(machine % THREAD_COUNT).hashCode();
                    }
                });

        StressTestSupport.measure(
                "recipe-thread-allowlist-sticky",
                StressTestSupport.MACHINE_COUNT,
                1,
                (long) StressTestSupport.MACHINE_COUNT * THREAD_COUNT,
                () -> {
                    for (int machine = 0; machine < StressTestSupport.MACHINE_COUNT; machine++) {
                        var assignment = RecipeThreadAssignmentPlanner.plan(
                                THREAD_COUNT,
                                candidates,
                                initial.recipeIdsByThread(),
                                noRunningRecipes,
                                filters,
                                true);
                        checksum[0] += assignment.recipeIdsByThread().get(machine % THREAD_COUNT).hashCode();
                    }
                });

        assertNotEquals(0, checksum[0], "assignment results must remain observable during the timed loops");
    }

    private static void warmUp(List<String> candidates,
                               List<RecipeThreadFilter> filters,
                               List<String> noRunningRecipes,
                               List<String> runningRecipes,
                               List<String> previousAssignments) {
        long checksum = 0;
        for (int update = 0; update < WARMUP_UPDATES; update++) {
            boolean allowSameRecipe = (update & 1) == 0;
            List<String> previous = allowSameRecipe ? previousAssignments : List.of();
            List<String> running = update % 3 == 0 ? runningRecipes : noRunningRecipes;
            var assignment = RecipeThreadAssignmentPlanner.plan(
                    THREAD_COUNT, candidates, previous, running, filters, allowSameRecipe);
            checksum += assignment.recipeIdsByThread().get(update % THREAD_COUNT).hashCode();
        }
        assertNotEquals(0, checksum);
    }

    private static List<String> createCandidatesWithDuplicates() {
        List<String> candidates = new ArrayList<>(DISTINCT_CANDIDATE_COUNT + 8);
        for (int candidate = 0; candidate < DISTINCT_CANDIDATE_COUNT; candidate++) {
            String recipeId = recipeId(candidate);
            candidates.add(recipeId);
            if ((candidate & 15) == 0) {
                candidates.add(recipeId);
            }
        }
        return List.copyOf(candidates);
    }

    private static List<RecipeThreadFilter> createFilters() {
        List<RecipeThreadFilter> filters = new ArrayList<>(THREAD_COUNT);
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            filters.add(new RecipeThreadFilter(
                    Set.of(),
                    Set.of(recipeId(threadId), recipeId(threadId + THREAD_COUNT))));
        }
        return List.copyOf(filters);
    }

    private static List<String> emptyAssignments() {
        List<String> assignments = new ArrayList<>(THREAD_COUNT);
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            assignments.add("");
        }
        return List.copyOf(assignments);
    }

    private static List<String> createRunningAssignments() {
        List<String> assignments = new ArrayList<>(emptyAssignments());
        for (int threadId = 0; threadId < 5; threadId++) {
            assignments.set(threadId, recipeId(threadId));
        }
        return List.copyOf(assignments);
    }

    private static String recipeId(int candidate) {
        return "mbd2:stress/recipe_" + candidate;
    }
}
