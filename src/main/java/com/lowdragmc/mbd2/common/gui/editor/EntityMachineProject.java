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

@Getter
@LDLRegister(name = "em", group = "editor.machine")
@NoArgsConstructor
public class EntityMachineProject extends MachineProject {

    public EntityMachineProject(Resources resources, EntityMachineDefinition definition, WidgetGroup ui) {
        super(resources, definition, ui);
    }

    @Override
    public EntityMachineDefinition getDefinition() {
        return (EntityMachineDefinition) super.getDefinition();
    }

    @Override
    protected EntityMachineDefinition createDefinition() {
        var builder = EntityMachineDefinition.builder();
        builder.id(MBD2.id("new_entity_machine"))
                .rootState(StateMachine.createSingleDefault(MachineState::builder, IRenderer.EMPTY));
        return builder.build();
    }

    @Override
    public EntityMachineProject newEmptyProject() {
        return new EntityMachineProject(new Resources(createResources()), createDefinition(), createDefaultUI());
    }

    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "entity_machine");
    }

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
