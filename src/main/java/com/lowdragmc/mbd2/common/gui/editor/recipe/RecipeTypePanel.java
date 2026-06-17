package com.lowdragmc.mbd2.common.gui.editor.recipe;

import com.lowdragmc.lowdraglib.gui.animation.Transform;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.MenuPanel;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.recipe.widget.RecipeTypeUIFloatView;
import com.lowdragmc.mbd2.common.gui.editor.recipe.widget.RecipeXEIPreviewFloatView;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Editor panel for one machine recipe type and its built-in recipes.
 *
 * <p>The panel installs separate common and fuel recipe lists in the editor toolbox,
 * exposes the recipe type itself in the basic configurator, and shows a floating XEI
 * preview for whichever recipe row is selected. Recipe rows write directly into the
 * backing {@link MBDRecipeType} built-in recipe map.</p>
 *
 * <p>This class is client editor UI and is expected to run on the render/UI thread.</p>
 */
public class RecipeTypePanel extends WidgetGroup {
    @Getter
    protected final MachineEditor editor;
    protected MBDRecipeType recipeType;
    protected WidgetGroup contentGroup;
    @Getter
    private final RecipeXEIPreviewFloatView floatView = new RecipeXEIPreviewFloatView();

    /**
     * Creates a recipe-type editor panel.
     *
     * @param recipeType recipe type whose built-in recipes and settings are edited
     * @param editor     owning machine editor
     */
    public RecipeTypePanel(MBDRecipeType recipeType, MachineEditor editor) {
        super(0, MenuPanel.HEIGHT, editor.getSize().getWidth() - ConfigPanel.WIDTH, editor.getSize().height - MenuPanel.HEIGHT - 16);
        this.recipeType = recipeType;
        this.editor = editor;
        this.contentGroup = new WidgetGroup(20, 20, getSizeWidth() - 40, getSizeHeight() - 20);
        if (getSizeWidth() - editor.getToolPanel().getSizeWidth() - 40 > 400) {
            contentGroup.setSizeWidth(getSizeWidth() - editor.getToolPanel().getSizeWidth() - 40);
            contentGroup.setSelfPositionX(editor.getToolPanel().getSizeWidth() + 20);
        }
        addWidget(contentGroup);
    }

    /**
     * Installs recipe-list toolboxes, opens recipe-type settings, and shows the preview view.
     */
    public void onPanelSelected() {
        editor.getConfigPanel().clearAllConfigurators();
        editor.getToolPanel().clearAllWidgets();
        editor.getToolPanel().setTitle("editor.machine.recipe_type.recipes");
        contentGroup.clearAllWidgets();
        var common = new AtomicReference<RecipeList>();
        var fuel = new AtomicReference<RecipeList>();
        editor.getToolPanel().addNewToolBox("editor.machine.recipe_type.recipes.common", Icons.WIDGET_CUSTOM, size -> {
            // if recipes in common are selected, then deselect the fuel recipes
            common.set(new RecipeList(this, size, false));
            common.get().setOnSelected(() -> {
                if (fuel.get() != null) {
                    fuel.get().setSelected(null);
                }
            });
            return common.get();
        });
        editor.getToolPanel().addNewToolBox("editor.machine.recipe_type.recipes.fuel", Icons.WIDGET_CUSTOM, size -> {
            // if recipes in fuel are selected, then deselect the common recipes
            fuel.set(new RecipeList(this, size, true));
            fuel.get().setOnSelected(() -> {
                if (common.get() != null) {
                    common.get().setSelected(null);
                }
            });
            return fuel.get();
        });
        if (editor.getToolPanel().inAnimate()) {
            editor.getToolPanel().getAnimation().appendOnFinish(() -> editor.getToolPanel().show());
        } else {
            editor.getToolPanel().show();
        }
        editor.getFloatView().addWidgetAnima(floatView, new Transform().duration(200).scale(0.2f));
        floatView.clearRecipe();
        editor.getConfigPanel().openConfigurator(MachineEditor.BASIC, recipeType);
    }

    /**
     * Removes recipe-specific toolboxes, configurators, and preview widgets.
     */
    public void onPanelDeselected() {
        editor.getToolPanel().setTitle("ldlib.gui.editor.group.tool_box");
        editor.getToolPanel().hide();
        editor.getToolPanel().clearAllWidgets();
        editor.getConfigPanel().clearAllConfigurators();
        editor.getFloatView().removeWidget(floatView);
    }
}
