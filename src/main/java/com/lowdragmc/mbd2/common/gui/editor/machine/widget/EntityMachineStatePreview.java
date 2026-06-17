package com.lowdragmc.mbd2.common.gui.editor.machine.widget;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.common.gui.editor.machine.EntityMachinePreviewScene;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineConfigPanel;
import com.lowdragmc.mbd2.common.machine.definition.config.MachineState;

/**
 * Machine-state preview widget that renders entity-machine states.
 *
 * <p>It reuses the generic state preview frame and menu behavior, but replaces the block preview scene with an entity
 * preview created by {@link EntityMachinePreviewScene}.</p>
 */
public class EntityMachineStatePreview extends MachineStatePreview {
    /**
     * Creates an entity-machine state preview.
     *
     * @param panel owning state panel
     * @param state state represented by this preview
     */
    public EntityMachineStatePreview(MachineConfigPanel panel, MachineState state) {
        super(panel, state);
    }

    /**
     * Installs the entity-machine preview scene for this state.
     */
    @Override
    protected void setupPreviewScene(SceneWidget scene, TrackedDummyWorld level) {
        previewMachine = EntityMachinePreviewScene.create(getPanel().getEditor(), scene, level, getState().name(), null);
    }
}
