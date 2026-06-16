package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigMachineEvents;

public class EntityAIEventsPanel extends MachineEventsPanel {
    public EntityAIEventsPanel(MachineEditor editor) {
        super(editor);
    }

    @Override
    public void onPanelSelected() {
        if (getEditor().getCurrentProject() instanceof EntityMachineProject project) {
            getEditor().getConfigPanel().openConfigurator(MachineEditor.BASIC, project.getDefinition().entityAISettings());
        }
    }

    @Override
    public boolean handlesEventConfig(ConfigMachineEvents config) {
        return getEditor().getCurrentProject() instanceof EntityMachineProject project &&
                project.getDefinition().entityAISettings() == config;
    }
}
