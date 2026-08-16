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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadedRecipeLogicSearchTest {

    private static final MBDRecipe A = recipe("a", 0);
    private static final MBDRecipe B = recipe("b", 1);
    private static final MBDRecipe C = recipe("c", 2);

    @Test
    void stableAssignmentUsesOneExactLookupWithoutAnExhaustiveSearch() {
        SearchProbeLogic logic = newLogic(List.of(A, B, C));
        logic.setAssignedRecipeId(id(B));

        List<MBDRecipe> matches = logic.runSearch();

        assertEquals(List.of(B), matches);
        assertEquals(1, logic.exactLookupCount);
        assertEquals(0, logic.exhaustiveSearchCount);
        assertEquals(1, logic.simulatedMatchCount);
        assertFalse(logic.lastSearchWasExhaustive());
    }

    @Test
    void missingAssignmentFallsBackToOneExhaustiveDiscoverySearch() {
        SearchProbeLogic logic = newLogic(List.of(B, C));
        logic.setAssignedRecipeId("mbd2:missing");

        List<MBDRecipe> matches = logic.runSearch();

        assertEquals(List.of(B, C), matches);
        assertEquals(1, logic.exactLookupCount);
        assertEquals(1, logic.exhaustiveSearchCount);
        assertEquals(2, logic.simulatedMatchCount);
        assertTrue(logic.lastSearchWasExhaustive());
    }

    @Test
    void rejectedRestrictedResultForcesExplorationOnTheNextSearch() {
        SearchProbeLogic logic = newLogic(List.of(A, B, C));
        logic.setAssignedRecipeId(id(A));

        assertEquals(List.of(A), logic.runSearch());
        logic.completeSearch(false);
        List<MBDRecipe> recoveryMatches = logic.runSearch();
        assertEquals(List.of(A, B, C), recoveryMatches);
        assertEquals(List.of(A, B, C), logic.applyLatestFilters(recoveryMatches),
                "recovery must bypass the stale assignment so later candidates can start");

        assertEquals(1, logic.exactLookupCount);
        assertEquals(1, logic.exhaustiveSearchCount);
        assertTrue(logic.lastSearchWasExhaustive());
    }

    @Test
    void postSearchFilteringUsesTheLatestAssignmentAndLaneFilters() {
        SearchProbeLogic logic = newLogic(List.of(A, B, C));
        logic.setAssignedRecipeId(id(B));
        logic.getBlacklist().add(id(B));

        assertEquals(List.of(), logic.applyLatestFilters(List.of(A, B, C)));

        logic.getBlacklist().clear();
        logic.setExternalRecipeBlocklist(Set.of(id(A)));
        assertEquals(List.of(B), logic.applyLatestFilters(List.of(A, B, C)));
    }

    @Test
    void suppressedUnassignedLaneDoesNotInvokeAnyRecipeSearch() {
        SearchProbeLogic logic = newLogic(List.of(A, B, C));
        logic.setAssignedRecipeId("", false);

        assertEquals(List.of(), logic.runSearch());
        assertEquals(0, logic.exactLookupCount);
        assertEquals(0, logic.exhaustiveSearchCount);
        assertEquals(0, logic.simulatedMatchCount);
        assertFalse(logic.lastSearchWasExhaustive());

        logic.setAssignedRecipeId("", true);
        assertEquals(List.of(A, B, C), logic.runSearch());
        assertEquals(1, logic.exhaustiveSearchCount);
    }

    private static SearchProbeLogic newLogic(List<MBDRecipe> recipes) {
        ProbeMachine machine = new ProbeMachine();
        SearchProbeLogic logic = new SearchProbeLogic(machine, recipes);
        machine.recipeLogic = logic;
        return logic;
    }

    private static MBDRecipe recipe(String path, int priority) {
        return new MBDRecipe(
                null,
                ResourceLocation.fromNamespaceAndPath("mbd2", path),
                Map.of(),
                Map.of(),
                List.of(),
                new CompoundTag(),
                20,
                false,
                false,
                priority);
    }

    private static String id(MBDRecipe recipe) {
        return recipe.getId().toString();
    }

    private static final class SearchProbeLogic extends ThreadedRecipeLogic {
        private final List<MBDRecipe> availableRecipes;
        private int exactLookupCount;
        private int exhaustiveSearchCount;
        private int simulatedMatchCount;

        private SearchProbeLogic(IMachine machine, List<MBDRecipe> availableRecipes) {
            super(machine, 1);
            this.availableRecipes = availableRecipes;
        }

        @Override
        protected List<MBDRecipe> searchRecipesById(Set<ResourceLocation> candidateIds) {
            exactLookupCount++;
            List<MBDRecipe> matches = new ArrayList<>();
            for (MBDRecipe recipe : availableRecipes) {
                if (candidateIds.contains(recipe.getId())) {
                    simulatedMatchCount++;
                    matches.add(recipe);
                }
            }
            return List.copyOf(matches);
        }

        @Override
        protected List<MBDRecipe> searchRecipes(Predicate<? super MBDRecipe> candidateFilter) {
            exhaustiveSearchCount++;
            List<MBDRecipe> matches = new ArrayList<>();
            for (MBDRecipe recipe : availableRecipes) {
                if (candidateFilter.test(recipe)) {
                    simulatedMatchCount++;
                    matches.add(recipe);
                }
            }
            return List.copyOf(matches);
        }

        private List<MBDRecipe> runSearch() {
            return searchRecipe();
        }

        private boolean lastSearchWasExhaustive() {
            return wasLastRecipeSearchExhaustive();
        }

        private List<MBDRecipe> applyLatestFilters(List<MBDRecipe> recipes) {
            return filterSearchedRecipes(recipes);
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
