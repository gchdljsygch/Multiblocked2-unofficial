package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import lombok.Getter;

import java.util.Set;

/**
 * Base adapter that exposes a {@link RecipeCapabilityTrait} as an {@link IRecipeHandlerTrait}.
 *
 * <p>Concrete subclasses implement only the content-specific handling algorithm while this adapter delegates shared
 * metadata, IO mode, slot grouping, distinctness, and change listener registration to the owning trait.</p>
 *
 * @param <CONTENT> recipe capability content type handled by the subclass
 */
public abstract class RecipeHandlerTrait<CONTENT> implements IRecipeHandlerTrait<CONTENT> {
    public final RecipeCapabilityTrait trait;
    @Getter
    public final RecipeCapability<CONTENT> recipeCapability;

    /**
     * Creates a handler adapter bound to one capability trait.
     *
     * @param trait            owning trait that supplies common handler metadata and listener storage
     * @param recipeCapability capability type this handler consumes or produces
     */
    public RecipeHandlerTrait(RecipeCapabilityTrait trait, RecipeCapability<CONTENT> recipeCapability) {
        this.trait = trait;
        this.recipeCapability = recipeCapability;
    }

    /**
     * Registers a listener with the owning trait.
     *
     * @param listener callback invoked when handler-visible state changes
     * @return subscription used to remove the listener
     */
    @Override
    public ISubscription addChangedListener(Runnable listener) {
        return trait.addChangedListener(listener);
    }

    /**
     * Returns the recipe IO mode configured on the owning trait.
     *
     * @return input, output, both, or none according to the trait definition/runtime state
     */
    @Override
    public IO getHandlerIO() {
        return trait.getHandlerIO();
    }

    /**
     * Returns whether this handler should be considered distinct during recipe matching.
     *
     * @return owning trait's distinct flag
     */
    @Override
    public boolean isDistinct() {
        return trait.isDistinct();
    }

    /**
     * Returns the logical slot names exposed by the owning trait.
     *
     * @return immutable or runtime-owned set of slot names, depending on the owning trait implementation
     */
    @Override
    public Set<String> getSlotNames() {
        return trait.getSlotNames();
    }

    /**
     * Returns the recipe group assigned to the owning trait.
     *
     * @return recipe group id, or an empty/default value depending on trait configuration
     */
    @Override
    public String getRecipeGroup() {
        return trait.getRecipeGroup();
    }
}
