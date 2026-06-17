package com.lowdragmc.mbd2.common.gui.editor.recipe;

import com.lowdragmc.lowdraglib.gui.animation.Transform;
import com.lowdragmc.lowdraglib.gui.editor.ui.MainPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.recipe.widget.RecipeTypeUIFloatView;
import lombok.Getter;

/**
 * Main-panel wrapper for editing recipe XEI UI templates.
 *
 * <p>The panel restores the standard widget toolbox so users can place arbitrary UI
 * widgets, and it adds a floating {@link RecipeTypeUIFloatView} that can generate common
 * recipe-template widgets such as progress bars, duration labels, condition labels, and
 * capability slots. The {@code isFuel} mode selects whether the normal recipe UI or fuel
 * recipe UI is edited.</p>
 */
public class RecipeXEIUIPanel extends MainPanel {
    @Getter
    private final RecipeTypeUIFloatView floatView;

    /**
     * Creates a recipe UI template editor.
     *
     * @param editor owning machine editor
     * @param root   root widget group containing the editable UI canvas
     * @param isFuel whether the fuel-recipe template is edited instead of the common one
     */
    public RecipeXEIUIPanel(MachineEditor editor, WidgetGroup root, boolean isFuel) {
        super(editor, root);
        floatView = new RecipeTypeUIFloatView(isFuel);
    }

    /**
     * Returns the owning editor with the concrete project type expected by this module.
     */
    public MachineEditor getEditor() {
        return (MachineEditor) editor;
    }

    /**
     * Shows standard widget tools and the recipe-template helper float view.
     */
    public void onPanelSelected() {
        editor.getConfigPanel().clearAllConfigurators();
        editor.getToolPanel().clearAllWidgets();
        for (WidgetToolBox.Default tab : WidgetToolBox.Default.TABS) {
            editor.getToolPanel().addNewToolBox("ldlib.gui.editor.group." + tab.groupName, tab.icon, tab::createToolBox);
        }
        if (editor.getToolPanel().inAnimate()) {
            editor.getToolPanel().getAnimation().appendOnFinish(() -> editor.getToolPanel().show());
        } else {
            editor.getToolPanel().show();
        }
        editor.getFloatView().addWidgetAnima(floatView, new Transform().duration(200).scale(0.2f));
        floatView.reloadList();
    }

    /**
     * Hides the tool panel, clears configurators, and removes the helper float view.
     */
    public void onPanelDeselected() {
        editor.getToolPanel().hide();
        editor.getToolPanel().clearAllWidgets();
        editor.getConfigPanel().clearAllConfigurators();
        editor.getFloatView().removeWidget(floatView);
    }
}
