package com.lowdragmc.mbd2.api.capability.recipe;

import com.google.common.collect.Table;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Exposes recipe-capability handlers for a machine, controller, or part.
 *
 * <p>The business goal is to give recipe matching and recipe IO a grouped view
 * of all handlers that can consume inputs or produce outputs. Implementations
 * should update the returned table on the logical server thread or otherwise
 * provide their own synchronization before recipe logic reads it.</p>
 */
public interface IRecipeCapabilityHolder {

    /**
     * Returns whether this holder currently exposes any recipe handlers.
     *
     * @return {@code true} when {@link #getRecipeCapabilitiesProxy()} is not
     * empty
     */
    default boolean hasProxies() {
        return !getRecipeCapabilitiesProxy().isEmpty();
    }

    /**
     * Returns recipe handlers grouped by IO direction and capability.
     *
     * <p>Side effects: none expected. The table's rows are {@link IO} values and
     * columns are capability types. Recipe logic reads the handler lists in order
     * while matching or committing recipe content.</p>
     *
     * @return non-null table of handlers; empty when this holder has no recipe
     * IO
     */
    @Nonnull
    Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> getRecipeCapabilitiesProxy();

    /**
     * Returns the tier used to boost chance-based recipe outputs.
     *
     * @return tier value consumed by content chance logic; {@code -1} voids all
     * chance-gated outputs
     */
    default int getChanceTier() {
        return 0;
    }

}
