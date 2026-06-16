package com.lowdragmc.mbd2.api.capability.recipe;

import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeGroup;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles one recipe capability for matching and committing recipe IO.
 *
 * <p>The business goal is to let recipe logic pass capability-specific content
 * through one or more handlers until all content is satisfied. Implementations
 * must treat {@code simulate=true} as a no-commit check and should only mutate
 * inventories, tanks, energy stores, worlds, or internal counters when
 * {@code simulate=false}. Calls normally happen on the logical server thread,
 * but recipe search may simulate handlers from background threads when async
 * search is enabled; implementations that are not thread-safe should make
 * simulation read-only and externally synchronized.</p>
 */
public interface IRecipeHandler<K> {

    /**
     * Matches or commits capability content for a recipe.
     *
     * <p>Side effects depend on {@code simulate}. In commit mode, input handlers
     * may consume resources and output handlers may insert or emit resources. The
     * returned list represents content that this handler could not satisfy and
     * should be offered to later handlers.</p>
     *
     * @param io       recipe IO direction; always {@link IO#IN} or {@link IO#OUT}
     *                 during normal recipe handling
     * @param recipe   recipe being matched or handled
     * @param left     mutable or immutable content list remaining for this
     *                 capability; entries use this handler's content type
     * @param slotName optional slot name requested by recipe content; {@code null}
     *                 means any compatible slot/group is acceptable
     * @param simulate {@code true} to check availability without committing
     *                 mutations; {@code false} to perform IO
     * @return remaining unsatisfied content for other handlers, or {@code null}
     * when all content is satisfied. Returning {@code null} is the success marker
     */
    List<K> handleRecipeInner(IO io, MBDRecipe recipe, List<K> left, @Nullable String slotName, boolean simulate);

    /**
     * Returns slot names handled by this proxy.
     *
     * @return accepted slot names; empty means the handler is not restricted by
     * slot name
     */
    default Set<String> getSlotNames() {
        return Collections.emptySet();
    }

    /**
     * Returns whether this handler requires distinct content matching.
     *
     * <p>Distinct handlers should be considered separately during matching so
     * one handler does not satisfy multiple mutually exclusive content entries.</p>
     *
     * @return {@code true} when content of the same capability must be handled
     * distinctly
     */
    default boolean isDistinct() {
        return false;
    }

    /**
     * Recipe group id for isolating recipe matching between handlers.
     *
     * @return primary recipe group id; defaults to
     * {@link RecipeGroup#DEFAULT}
     */
    default String getRecipeGroup() {
        return RecipeGroup.DEFAULT;
    }

    /**
     * Returns all recipe groups accepted by this handler.
     *
     * @return non-empty set of recipe group ids; defaults to the single primary
     * group
     */
    default Set<String> getRecipeGroups() {
        return Set.of(getRecipeGroup());
    }

    /**
     * Returns the capability type handled by this proxy.
     *
     * @return capability descriptor for content type {@code K}
     */
    RecipeCapability<K> getRecipeCapability();

    /**
     * Copies handler content before matching or committing.
     *
     * <p>Side effects: delegates to the capability serializer. Handlers may
     * override this when their content needs implementation-specific deep-copy
     * semantics.</p>
     *
     * @param content source content object
     * @return copied content value
     */
    @SuppressWarnings("unchecked")
    default K copyContent(Object content) {
        return getRecipeCapability().copyInner((K) content);
    }

    /**
     * Copies generic content and delegates to
     * {@link #handleRecipeInner(IO, MBDRecipe, List, String, boolean)}.
     *
     * @param io       recipe IO direction
     * @param recipe   recipe being handled
     * @param left     generic content list
     * @param slotName optional requested slot name
     * @param simulate {@code true} for no-commit matching
     * @return remaining unsatisfied content, or {@code null} on success
     */
    default List<K> handleRecipe(IO io, MBDRecipe recipe, List<?> left, @Nullable String slotName, boolean simulate) {
        return handleRecipeInner(io, recipe, left.stream().map(this::copyContent).collect(Collectors.toList()), slotName, simulate);
    }

    /**
     * Handles content with an explicit recipe-group filter.
     *
     * <p>Default side effects and return semantics match
     * {@link #handleRecipe(IO, MBDRecipe, List, String, boolean)}. Handlers that
     * support multiple groups can override this to isolate matching by group.</p>
     *
     * @param io          recipe IO direction
     * @param recipe      recipe being handled
     * @param left        generic content list
     * @param slotName    optional requested slot name
     * @param simulate    {@code true} for no-commit matching
     * @param recipeGroup optional group id requested by the recipe
     * @return remaining unsatisfied content, or {@code null} on success
     */
    default List<K> handleRecipe(IO io, MBDRecipe recipe, List<?> left, @Nullable String slotName, boolean simulate, @Nullable String recipeGroup) {
        return handleRecipe(io, recipe, left, slotName, simulate);
    }

    /**
     * Called when recipe logic enters the working state.
     *
     * <p>Trigger examples include idle-to-working and waiting-to-working
     * transitions. Side effects are implementation-specific setup for active
     * recipe IO.</p>
     *
     * @param holder capability holder whose recipe started working
     * @param io     handler side used by the recipe
     * @param recipe active recipe
     */
    default void preWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
    }

    /**
     * Called when recipe logic leaves the working state.
     *
     * <p>Trigger examples include working-to-idle, working-to-waiting, and
     * interruption. Side effects are implementation-specific cleanup for active
     * recipe IO.</p>
     *
     * @param holder capability holder whose recipe stopped working
     * @param io     handler side used by the recipe
     * @param recipe recipe that was active
     */
    default void postWorking(IRecipeCapabilityHolder holder, IO io, MBDRecipe recipe) {
    }


}
