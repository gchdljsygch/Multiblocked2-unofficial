package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import net.minecraft.core.BlockPos;

import java.util.Collections;

/**
 * Basic configuration panel for entity-machine definitions.
 *
 * <p>The panel replaces the block preview scene with an entity-machine preview created by
 * {@link EntityMachinePreviewScene} and opens the entity definition configurator when selected.</p>
 */
public class EntityMachineConfigPanel extends MachineScenePanel {
    /**
     * Creates an entity-machine configuration panel.
     *
     * @param editor owning machine editor
     */
    public EntityMachineConfigPanel(MachineEditor editor) {
        super(editor);
    }

    /**
     * Rebuilds the preview scene using the entity-machine preview entity and the root state.
     */
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

    /**
     * Opens the current entity-machine definition in the basic configurator tab.
     */
    public void onPanelSelected() {
        if (editor.getCurrentProject() instanceof EntityMachineProject project) {
            editor.getConfigPanel().openConfigurator(MachineEditor.BASIC, project.getDefinition());
        }
    }
}
