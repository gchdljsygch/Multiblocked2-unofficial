package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.machine.widget.TraitList;

import javax.annotation.Nullable;

/**
 * Editor panel for machine trait definitions.
 *
 * <p>The panel reuses the machine preview scene and adds a tool-panel list of trait definitions. Selecting a trait
 * opens its configurator; selected traits may also render additional preview overlays after the world scene renders.</p>
 */
public class MachineTraitPanel extends MachineScenePanel {
    @Nullable
    private TraitList traitList;

    /**
     * Creates a trait panel for the machine editor.
     *
     * @param editor owning machine editor
     */
    public MachineTraitPanel(MachineEditor editor) {
        super(editor);
    }

    /**
     * Populates and shows the editor tool panel with the trait list.
     */
    public void onPanelSelected() {
        editor.getConfigPanel().clearAllConfigurators();
        editor.getToolPanel().clearAllWidgets();
        editor.getToolPanel().setTitle("editor.machine.machine_traits");
        editor.getToolPanel().addNewToolBox("editor.machine.machine_traits.list", Icons.WIDGET_CUSTOM, size -> traitList = new TraitList(editor, size));
        if (editor.getToolPanel().inAnimate()) {
            editor.getToolPanel().getAnimation().appendOnFinish(() -> editor.getToolPanel().show());
        } else {
            editor.getToolPanel().show();
        }
    }

    /**
     * Hides trait tooling and clears trait configurators.
     */
    public void onPanelDeselected() {
        editor.getToolPanel().setTitle("ldlib.gui.editor.group.tool_box");
        editor.getToolPanel().hide();
        editor.getToolPanel().clearAllWidgets();
        editor.getConfigPanel().clearAllConfigurators();
    }

    /**
     * Renders base preview overlays and the selected trait's custom overlay, if any.
     *
     * @param sceneWidget scene that just completed world rendering
     */
    @Override
    public void renderAfterWorld(SceneWidget sceneWidget) {
        super.renderAfterWorld(sceneWidget);
        if (traitList != null && traitList.getSelected() != null) {
            var definition = traitList.getSelected();
            definition.renderAfterWorldInTraitPanel(this);
        }
    }
}
