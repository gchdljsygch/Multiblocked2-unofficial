package com.lowdragmc.mbd2.api.recipe.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import lombok.Getter;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Client-side event fired after a fuel recipe UI root is created.
 *
 * <p>The business goal is to let integrations customize the separate fuel UI
 * for recipe types that require fuel. Listeners may mutate {@link #root} or
 * cancel the event according to the caller's cancellation handling. This event
 * should be handled on the client UI thread.</p>
 */
@Getter
@Cancelable
@LDLRegister(name = "FuelRecipeUIEvent", group = "RecipeTypeEvent")
public class FuelRecipeUIEvent extends RecipeTypeEvent {
    public MBDRecipe recipe;
    public WidgetGroup root;

    /**
     * Creates a fuel recipe UI event.
     *
     * @param recipeType owning recipe type
     * @param recipe     fuel recipe being displayed
     * @param root       mutable root widget created for the fuel recipe
     */
    public FuelRecipeUIEvent(MBDRecipeType recipeType, MBDRecipe recipe, WidgetGroup root) {
        super(recipeType);
        this.recipe = recipe;
        this.root = root;
    }
}
