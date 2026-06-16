package com.lowdragmc.mbd2.common.gui.editor.machine.widget;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.common.gui.editor.machine.EntityMachinePreviewScene;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineConfigPanel;
import com.lowdragmc.mbd2.common.machine.definition.config.MachineState;

public class EntityMachineStatePreview extends MachineStatePreview {
    public EntityMachineStatePreview(MachineConfigPanel panel, MachineState state) {
        super(panel, state);
    }

    @Override
    protected void setupPreviewScene(SceneWidget scene, TrackedDummyWorld level) {
        previewMachine = EntityMachinePreviewScene.create(getPanel().getEditor(), scene, level, getState().name(), null);
    }
}
