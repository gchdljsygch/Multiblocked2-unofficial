package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.RecipeGroup;
import lombok.Getter;
import lombok.Setter;

/**
 * Editable definition for a trait that exposes recipe handlers.
 *
 * <p>The business goal is to configure how a trait participates in recipe
 * matching: input/output direction, distinct matching, slot-name routing, and
 * recipe-group isolation. Definition instances are mutable editor/configuration
 * state; runtime traits should read them from the machine thread and avoid
 * concurrent edits.</p>
 */
@Getter
@Setter
public abstract class RecipeCapabilityTraitDefinition extends TraitDefinition {

    @Configurable(name = "config.definition.trait.recipe_handler", tips = "config.definition.trait.recipe_handler.tooltip")
    private IO recipeHandlerIO = IO.IN;

    @Configurable(name = "config.definition.trait.distinct", tips = {"config.definition.trait.distinct.tooltip.0", "config.definition.trait.distinct.tooltip.1"})
    private boolean isDistinct;

    @Configurable(name = "config.definition.trait.slot_names", tips = "config.definition.trait.slot_names.tooltip")
    private String[] slotNames = new String[0];

    @Configurable(name = "config.definition.trait.recipe_group", tips = "config.definition.trait.recipe_group.tooltip")
    private String recipeGroup = RecipeGroup.DEFAULT;

    /**
     * Sets and normalizes the recipe group used by handlers created from this
     * definition.
     *
     * <p>Side effects: blank or {@code null} values are stored as
     * {@link RecipeGroup#DEFAULT}.</p>
     *
     * @param recipeGroup configured recipe group id from the editor or project
     *                    file
     */
    @ConfigSetter(field = "recipeGroup")
    public void setRecipeGroup(String recipeGroup) {
        this.recipeGroup = RecipeGroup.normalizeOrDefault(recipeGroup);
    }

    /**
     * Returns the normalized recipe group used by handlers created from this
     * definition.
     *
     * @return configured group id, or {@link RecipeGroup#DEFAULT} when blank
     */
    public String getRecipeGroup() {
        return RecipeGroup.normalizeOrDefault(recipeGroup);
    }
}
