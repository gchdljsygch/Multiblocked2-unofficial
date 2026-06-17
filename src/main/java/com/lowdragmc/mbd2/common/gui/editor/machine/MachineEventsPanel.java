package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.MenuPanel;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseGraph;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.MachineProject;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigMachineEvents;
import com.lowdragmc.mbd2.common.graphprocessor.MachineEventGraphView;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Editor panel for machine event graphs.
 *
 * <p>The panel switches between the event configuration list and an embedded {@link MachineEventGraphView}. It tracks
 * the graph currently being edited and hides the resource panel while a graph view is open.</p>
 */
@Getter
public class MachineEventsPanel extends WidgetGroup {
    @Getter
    private final MachineEditor editor;
    @Nullable
    private BaseGraph currentGraph;

    /**
     * Creates an event panel sized to the editor content area.
     *
     * @param editor owning machine editor
     */
    public MachineEventsPanel(MachineEditor editor) {
        super(0, MenuPanel.HEIGHT + 16, editor.getSize().getWidth() - ConfigPanel.WIDTH, editor.getSize().height - MenuPanel.HEIGHT - 16);
        this.editor = editor;
    }

    /**
     * Opens an embedded graph editor for an event graph.
     *
     * @param graph graph model to edit
     */
    public void openEventGraphEditor(BaseGraph graph) {
        clearAllWidgets();
        currentGraph = graph;
        addWidget(new MachineEventGraphView(graph, 0, 0, getSizeWidth(), getSizeHeight()));
        editor.getResourcePanel().hide();
    }

    /**
     * Closes the embedded graph editor and clears its widgets.
     */
    public void closeEventGraphEditor() {
        clearAllWidgets();
        currentGraph = null;
    }

    /**
     * Checks whether this panel owns a given machine-event configuration object.
     *
     * @param config event config to test
     * @return {@code true} when the config belongs to the current machine definition
     */
    public boolean handlesEventConfig(ConfigMachineEvents config) {
        return editor.getCurrentProject() instanceof MachineProject project &&
                project.getDefinition().machineEvents() == config;
    }

    /**
     * Opens the current machine definition's event configuration.
     */
    public void onPanelSelected() {
        if (editor.getCurrentProject() instanceof MachineProject project) {
            editor.getConfigPanel().openConfigurator(MachineEditor.BASIC, project.getDefinition().machineEvents());
        }
    }

    /**
     * Clears configurators when leaving the event panel.
     */
    public void onPanelDeselected() {
        editor.getConfigPanel().clearAllConfigurators();
    }
}
