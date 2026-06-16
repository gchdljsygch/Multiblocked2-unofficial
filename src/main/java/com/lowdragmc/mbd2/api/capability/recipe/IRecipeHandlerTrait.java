package com.lowdragmc.mbd2.api.capability.recipe;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;

/**
 * Recipe handler backed by a machine trait.
 *
 * <p>The business goal is to expose trait-owned inventories, tanks, energy
 * stores, or other resources to recipe logic and notify the machine when those
 * resources change. Trait callbacks normally run on the logical server thread
 * that owns the machine.</p>
 */
public interface IRecipeHandlerTrait<K> extends IRecipeHandler<K> {
    /**
     * Returns the IO directions this trait supports during recipe search,
     * per-tick recipe handling, and recipe completion.
     *
     * @return handler direction, often {@link IO#IN}, {@link IO#OUT}, or
     * {@link IO#BOTH}
     */
    IO getHandlerIO();

    /**
     * Checks whether this trait can handle a recipe-side IO request.
     *
     * @param recipeIO requested recipe side; normally {@link IO#IN} or
     *                 {@link IO#OUT}
     * @return {@code true} when {@link #getHandlerIO()} supports the requested
     * side
     */
    default boolean compatibleWith(IO recipeIO) {
        return getHandlerIO().support(recipeIO);
    }

    /**
     * Registers a listener for changes to the trait's internal content.
     *
     * <p>Side effects: stores the listener in the trait implementation. The
     * returned subscription must be used to unregister it when no longer needed.</p>
     *
     * @param listener callback invoked when the trait's recipe-visible content
     *                 changes
     * @return subscription handle for removing the listener
     */
    ISubscription addChangedListener(Runnable listener);
}
