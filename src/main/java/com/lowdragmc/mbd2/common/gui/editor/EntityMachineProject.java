package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.gui.editor.machine.EntityAIEventsPanel;
import com.lowdragmc.mbd2.common.gui.editor.machine.EntityMachineConfigPanel;
import com.lowdragmc.mbd2.common.gui.editor.machine.EntityMachineStatePanel;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.MachineState;
import com.lowdragmc.mbd2.common.machine.definition.config.StateMachine;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.File;

/**
 * Editor project for entity-backed machine definitions.
 *
 * <p>Entity machine projects reuse the base machine resource and UI serialization but create
 * {@link EntityMachineDefinition} instances and load entity-specific editor tabs for AI events and state previews.</p>
 */
@Getter
@LDLRegister(name = "em", group = "editor.machine")
@NoArgsConstructor
public class EntityMachineProject extends MachineProject {

    /**
     * Creates an entity-machine project with explicit resources, definition, and UI.
     *
     * @param resources  project resource map
     * @param definition entity machine definition to edit
     * @param ui         configurable UI root
     */
    public EntityMachineProject(Resources resources, EntityMachineDefinition definition, WidgetGroup ui) {
        super(resources, definition, ui);
    }

    /**
     * Returns this project's definition as an entity-machine definition.
     *
     * @return entity machine definition
     */
    @Override
    public EntityMachineDefinition getDefinition() {
        return (EntityMachineDefinition) super.getDefinition();
    }

    /**
     * Creates the default entity-machine definition.
     *
     * @return definition using {@code mbd2:new_entity_machine} and an empty renderer
     */
    @Override
    protected EntityMachineDefinition createDefinition() {
        var builder = EntityMachineDefinition.builder();
        builder.id(MBD2.id("new_entity_machine"))
                .rootState(StateMachine.createSingleDefault(MachineState::builder, IRenderer.EMPTY));
        return builder.build();
    }

    /**
     * Creates a new empty entity-machine project.
     *
     * @return initialized entity-machine project
     */
    @Override
    public EntityMachineProject newEmptyProject() {
        return new EntityMachineProject(new Resources(createResources()), createDefinition(), createDefaultUI());
    }

    /**
     * Returns the workspace directory for entity-machine projects.
     *
     * @param editor owning editor
     * @return {@code entity_machine} subdirectory under the editor workspace
     */
    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "entity_machine");
    }

    /**
     * Adds entity-machine tabs when the project is loaded into a machine editor.
     *
     * @param editor editor receiving the project's tabs and resources
     */
    @Override
    public void onLoad(Editor editor) {
        if (editor instanceof MachineEditor machineEditor) {
            editor.getResourcePanel().loadResource(getResources(), false);
            var tabContainer = machineEditor.getTabPages();
            var entityConfigPanel = new EntityMachineConfigPanel(machineEditor);
            var machineStatePanel = new EntityMachineStatePanel(machineEditor);
            var machineTraitPanel = createMachineTraitPanel(machineEditor);
            var entityAIEventsPanel = new EntityAIEventsPanel(machineEditor);
            var machineEventsPanel = createMachineEventsPanel(machineEditor);
            var machineUIPanel = createMachineUIPanel(machineEditor);
            tabContainer.addTab("editor.machine.basic_settings", entityConfigPanel, entityConfigPanel::onPanelSelected);
            tabContainer.addTab("editor.machine.machine_states", machineStatePanel, machineStatePanel::onPanelSelected);
            tabContainer.addTab("editor.machine.machine_traits", machineTraitPanel, machineTraitPanel::onPanelSelected, machineTraitPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.entity_ai", entityAIEventsPanel, entityAIEventsPanel::onPanelSelected, entityAIEventsPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.machine_events", machineEventsPanel, machineEventsPanel::onPanelSelected, machineEventsPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.machine_ui", machineUIPanel, machineUIPanel::onPanelSelected, machineUIPanel::onPanelDeselected);
        }
    }
}
