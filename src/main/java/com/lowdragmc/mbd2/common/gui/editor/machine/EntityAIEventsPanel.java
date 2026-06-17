package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigMachineEvents;

/**
 * Event panel for entity-machine AI event graphs.
 *
 * <p>This panel behaves like {@link MachineEventsPanel} but binds to the entity AI settings rather than the normal
 * machine event settings.</p>
 */
public class EntityAIEventsPanel extends MachineEventsPanel {
    /**
     * Creates an entity AI event panel.
     *
     * @param editor owning machine editor
     */
    public EntityAIEventsPanel(MachineEditor editor) {
        super(editor);
    }

    /**
     * Opens the current entity machine's AI event configuration.
     */
    @Override
    public void onPanelSelected() {
        if (getEditor().getCurrentProject() instanceof EntityMachineProject project) {
            getEditor().getConfigPanel().openConfigurator(MachineEditor.BASIC, project.getDefinition().entityAISettings());
        }
    }

    /**
     * Checks whether this panel owns a given entity AI event configuration.
     *
     * @param config event config to test
     * @return {@code true} when the config belongs to the current entity-machine definition
     */
    @Override
    public boolean handlesEventConfig(ConfigMachineEvents config) {
        return getEditor().getCurrentProject() instanceof EntityMachineProject project &&
                project.getDefinition().entityAISettings() == config;
    }
}
