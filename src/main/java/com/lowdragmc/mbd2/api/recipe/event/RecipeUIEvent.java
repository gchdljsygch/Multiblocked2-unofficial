package com.lowdragmc.mbd2.api.recipe.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import lombok.Getter;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Client-side event fired after a normal recipe UI root is created.
 *
 * <p>The business goal is to let integrations and scripts add, remove, or
 * replace widgets for a displayed recipe. Listeners may mutate {@link #root}
 * directly or cancel the event to suppress the generated UI, depending on the
 * caller that posted the event. This event should be handled on the client UI
 * thread that owns the widget tree.</p>
 */
@Getter
@Cancelable
@LDLRegister(name = "RecipeUIEvent", group = "RecipeTypeEvent")
public class RecipeUIEvent extends RecipeTypeEvent {
    public MBDRecipe recipe;
    public WidgetGroup root;

    /**
     * Creates a recipe UI event.
     *
     * @param recipeType owning recipe type
     * @param recipe     recipe being displayed
     * @param root       mutable root widget created for the recipe
     */
    public RecipeUIEvent(MBDRecipeType recipeType, MBDRecipe recipe, WidgetGroup root) {
        super(recipeType);
        this.recipe = recipe;
        this.root = root;
    }
}
