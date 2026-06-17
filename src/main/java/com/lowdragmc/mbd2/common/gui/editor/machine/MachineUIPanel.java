package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.gui.animation.Transform;
import com.lowdragmc.lowdraglib.gui.editor.ui.MainPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.MachineProject;
import com.lowdragmc.mbd2.common.gui.editor.machine.widget.TraitUIFloatView;
import lombok.Getter;

/**
 * MainPanel used to edit the machine UI widget tree.
 *
 * <p>The panel exposes the current project's configurable UI as the editing canvas and opens a floating helper view
 * that can insert standard machine UI widgets and trait-provided UI templates.</p>
 */
public class MachineUIPanel extends MainPanel {

    /**
     * Floating helper with machine and trait UI insertion buttons.
     */
    @Getter
    private final TraitUIFloatView floatView = new TraitUIFloatView();

    /**
     * Creates a UI editor panel for the current machine project.
     *
     * @param editor owning machine editor
     */
    public MachineUIPanel(MachineEditor editor) {
        super(editor, editor.getCurrentProject() instanceof MachineProject machineProject ? machineProject.getUi() : new WidgetGroup());
    }

    /**
     * Returns the owning editor as a {@link MachineEditor}.
     */
    public MachineEditor getEditor() {
        return (MachineEditor) editor;
    }

    /**
     * Shows standard widget toolboxes and opens the trait UI helper float view.
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
        floatView.reloadTrait();
    }

    /**
     * Hides UI editor tooling and removes the trait UI helper float view.
     */
    public void onPanelDeselected() {
        editor.getToolPanel().hide();
        editor.getToolPanel().clearAllWidgets();
        editor.getConfigPanel().clearAllConfigurators();
        editor.getFloatView().removeWidget(floatView);
    }
}
