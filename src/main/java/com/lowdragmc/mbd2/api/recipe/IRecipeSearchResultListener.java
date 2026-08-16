package com.lowdragmc.mbd2.api.recipe;

import java.util.List;

/**
 * Capability for machine implementations that consume completed recipe-search results.
 *
 * <p>The callback runs on the logical server thread immediately before {@link RecipeLogic} validates and starts one
 * of the returned candidates. Implementations may update scheduling metadata used by that validation, but must not
 * mutate the supplied list or perform another recipe search.</p>
 */
public interface IRecipeSearchResultListener {

    /**
     * Receives the candidates produced by one recipe-logic search.
     *
     * @param source     recipe logic that initiated the search
     * @param candidates current matching candidates in priority order
     * @param exhaustive {@code true} when the search considered every recipe type candidate; {@code false} when an
     *                   implementation-specific prefilter narrowed the search
     */
    void onRecipeSearchResults(RecipeLogic source, List<MBDRecipe> candidates, boolean exhaustive);
}
