package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.RecipeGroup;
import lombok.Getter;
import lombok.Setter;

/**
 * A trait definition that have recipe handling capability.
 */
@Getter @Setter
public abstract class RecipeCapabilityTraitDefinition extends TraitDefinition {

    @Configurable(name = "config.definition.trait.recipe_handler", tips = "config.definition.trait.recipe_handler.tooltip")
    private IO recipeHandlerIO = IO.IN;

    @Configurable(name = "config.definition.trait.distinct", tips = {"config.definition.trait.distinct.tooltip.0", "config.definition.trait.distinct.tooltip.1"})
    private boolean isDistinct;

    @Configurable(name = "config.definition.trait.slot_names", tips = "config.definition.trait.slot_names.tooltip")
    private String[] slotNames = new String[0];

    @Configurable(name = "config.definition.trait.recipe_group", tips = "config.definition.trait.recipe_group.tooltip")
    private String recipeGroup = RecipeGroup.DEFAULT;

    @ConfigSetter(field = "recipeGroup")
    public void setRecipeGroup(String recipeGroup) {
        this.recipeGroup = RecipeGroup.normalizeOrDefault(recipeGroup);
    }

    public String getRecipeGroup() {
        return RecipeGroup.normalizeOrDefault(recipeGroup);
    }
}
