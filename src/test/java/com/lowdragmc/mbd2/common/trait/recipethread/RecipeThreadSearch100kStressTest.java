package com.lowdragmc.mbd2.common.trait.recipethread;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.performance.StressTestSupport;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Exercises the stable assigned-recipe search path at the repository's 100k-machine scale.
 */
@Tag("performance")
class RecipeThreadSearch100kStressTest {

    private static final int THREAD_COUNT = 20;
    private static final int CANDIDATE_COUNT = 128;
    private static final int WARMUP_SEARCHES = 8_192;

    @Test
    void searchesAssignedRecipesForOneHundredThousandMachinesWithoutFullScans() {
        StressTestSupport.requireStressScale();
        Map<ResourceLocation, MBDRecipe> candidates = createCandidates();
        SearchProbeLogic[] lanes = createLanes(candidates);
        warmUp(lanes);
        resetCounters(lanes);

        long[] checksum = {0};
        long operations = (long) StressTestSupport.MACHINE_COUNT * THREAD_COUNT;
        StressTestSupport.measure(
                "recipe-thread-stable-id-search",
                StressTestSupport.MACHINE_COUNT,
                1,
                operations,
                () -> {
                    for (int machine = 0; machine < StressTestSupport.MACHINE_COUNT; machine++) {
                        for (SearchProbeLogic lane : lanes) {
                            List<MBDRecipe> matches = lane.runSearch();
                            checksum[0] += matches.get(0).getId().hashCode();
                        }
                    }
                });

        assertNotEquals(0, checksum[0]);
        assertEquals(operations, sumExactLookups(lanes));
        assertEquals(operations, sumSimulatedMatches(lanes));
        assertEquals(0, sumExhaustiveSearches(lanes),
                "stable assignments must never scan every recipe candidate");

        SearchProbeLogic recoveryLane = lanes[0];
        recoveryLane.completeSearch(false);
        assertEquals(CANDIDATE_COUNT, recoveryLane.runSearch().size(),
                "a rejected assigned recipe must retain the exhaustive recovery path");
        assertEquals(1, recoveryLane.exhaustiveSearchCount);
    }

    private static SearchProbeLogic[] createLanes(Map<ResourceLocation, MBDRecipe> candidates) {
        SearchProbeLogic[] lanes = new SearchProbeLogic[THREAD_COUNT];
        List<ResourceLocation> candidateIds = List.copyOf(candidates.keySet());
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            ProbeMachine machine = new ProbeMachine();
            SearchProbeLogic logic = new SearchProbeLogic(machine, threadId + 1, candidates);
            logic.setAssignedRecipeId(candidateIds.get(threadId).toString());
            machine.recipeLogic = logic;
            lanes[threadId] = logic;
        }
        return lanes;
    }

    private static Map<ResourceLocation, MBDRecipe> createCandidates() {
        Map<ResourceLocation, MBDRecipe> candidates = new HashMap<>();
        for (int candidate = 0; candidate < CANDIDATE_COUNT; candidate++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mbd2", "stress/search_" + candidate);
            candidates.put(id, new MBDRecipe(
                    null,
                    id,
                    Map.of(),
                    Map.of(),
                    List.of(),
                    new CompoundTag(),
                    20,
                    false,
                    false,
                    candidate));
        }
        return Map.copyOf(candidates);
    }

    private static void warmUp(SearchProbeLogic[] lanes) {
        long checksum = 0;
        for (int search = 0; search < WARMUP_SEARCHES; search++) {
            SearchProbeLogic lane = lanes[search % lanes.length];
            checksum += lane.runSearch().get(0).getId().hashCode();
        }
        assertNotEquals(0, checksum);
    }

    private static void resetCounters(SearchProbeLogic[] lanes) {
        for (SearchProbeLogic lane : lanes) {
            lane.exactLookupCount = 0;
            lane.exhaustiveSearchCount = 0;
            lane.simulatedMatchCount = 0;
        }
    }

    private static long sumExactLookups(SearchProbeLogic[] lanes) {
        long count = 0;
        for (SearchProbeLogic lane : lanes) count += lane.exactLookupCount;
        return count;
    }

    private static long sumExhaustiveSearches(SearchProbeLogic[] lanes) {
        long count = 0;
        for (SearchProbeLogic lane : lanes) count += lane.exhaustiveSearchCount;
        return count;
    }

    private static long sumSimulatedMatches(SearchProbeLogic[] lanes) {
        long count = 0;
        for (SearchProbeLogic lane : lanes) count += lane.simulatedMatchCount;
        return count;
    }

    private static final class SearchProbeLogic extends ThreadedRecipeLogic {
        private final Map<ResourceLocation, MBDRecipe> candidates;
        private long exactLookupCount;
        private long exhaustiveSearchCount;
        private long simulatedMatchCount;

        private SearchProbeLogic(IMachine machine,
                                 int threadId,
                                 Map<ResourceLocation, MBDRecipe> candidates) {
            super(machine, threadId);
            this.candidates = candidates;
        }

        @Override
        protected List<MBDRecipe> searchRecipesById(Set<ResourceLocation> candidateIds) {
            exactLookupCount++;
            List<MBDRecipe> matches = new ArrayList<>(candidateIds.size());
            for (ResourceLocation candidateId : candidateIds) {
                MBDRecipe recipe = candidates.get(candidateId);
                if (recipe != null) {
                    simulatedMatchCount++;
                    matches.add(recipe);
                }
            }
            return matches;
        }

        @Override
        protected List<MBDRecipe> searchRecipes(Predicate<? super MBDRecipe> candidateFilter) {
            exhaustiveSearchCount++;
            List<MBDRecipe> matches = new ArrayList<>(candidates.size());
            for (MBDRecipe recipe : candidates.values()) {
                if (candidateFilter.test(recipe)) {
                    simulatedMatchCount++;
                    matches.add(recipe);
                }
            }
            return matches;
        }

        private List<MBDRecipe> runSearch() {
            return searchRecipe();
        }

        private void completeSearch(boolean recipeStarted) {
            onRecipeSearchHandled(recipeStarted);
        }
    }

    private static final class ProbeMachine implements IMachine {
        private RecipeLogic recipeLogic;

        @Override
        public BlockEntity getHolder() {
            return null;
        }

        @Override
        public long getOffset() {
            return 0;
        }

        @Override
        public Optional<Direction> getFrontFacing() {
            return Optional.empty();
        }

        @Override
        public boolean isFacingValid(Direction facing) {
            return false;
        }

        @Override
        public void setFrontFacing(Direction facing) {
        }

        @Override
        public MBDRecipeType getRecipeType() {
            return null;
        }

        @Override
        public List<MBDRecipeType> getRecipeTypes() {
            return List.of();
        }

        @Override
        public RecipeLogic getRecipeLogic() {
            return recipeLogic;
        }

        @Override
        public Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> getRecipeCapabilitiesProxy() {
            return ImmutableTable.of();
        }
    }
}
