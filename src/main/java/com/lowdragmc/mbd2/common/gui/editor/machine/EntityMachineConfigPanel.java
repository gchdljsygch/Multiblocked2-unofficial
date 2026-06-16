package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import net.minecraft.core.BlockPos;

import java.util.Collections;

public class EntityMachineConfigPanel extends MachineScenePanel {
    public EntityMachineConfigPanel(MachineEditor editor) {
        super(editor);
    }

    @Override
    public void resetScene() {
        level.clear();
        String rootStateName = null;
        if (editor.getCurrentProject() instanceof EntityMachineProject project) {
            rootStateName = project.getDefinition().stateMachine().getRootState().name();
        }
        previewMachine = EntityMachinePreviewScene.create(editor, scene, level, rootStateName, this::renderAfterWorld);
        scene.setRenderedCore(Collections.singleton(BlockPos.ZERO), null);
        reloadAdditionalTraits();
    }

    public void onPanelSelected() {
        if (editor.getCurrentProject() instanceof EntityMachineProject project) {
            editor.getConfigPanel().openConfigurator(MachineEditor.BASIC, project.getDefinition());
        }
    }
}
