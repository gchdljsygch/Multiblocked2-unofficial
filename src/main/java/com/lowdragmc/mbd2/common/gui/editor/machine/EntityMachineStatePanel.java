package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.animation.Transform;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.machine.widget.EntityMachineStatePreview;
import com.lowdragmc.mbd2.common.machine.definition.config.MachineState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State-configuration panel for entity-machine definitions.
 *
 * <p>The panel uses entity preview widgets and wraps selected {@link MachineState} configurators so entity states expose
 * the entity renderer settings instead of block front-renderer settings.</p>
 */
public class EntityMachineStatePanel extends MachineConfigPanel {
    private static final String RENDERER_CONFIG = "config.machine_state.renderer";
    private static final String ENTITY_RENDERER_CONFIG = "config.entity_machine.state.renderer";
    private static final String FRONT_RENDERER_CONFIG = "config.machine_state.front_renderer";

    /**
     * Creates an entity-machine state panel.
     *
     * @param editor owning machine editor
     */
    public EntityMachineStatePanel(MachineEditor editor) {
        super(editor);
    }

    /**
     * Rebuilds the entity-machine preview scene without forcing a specific state.
     */
    @Override
    public void resetScene() {
        level.clear();
        previewMachine = EntityMachinePreviewScene.create(editor, scene, level, null, this::renderAfterWorld);
        scene.setRenderedCore(Collections.singleton(BlockPos.ZERO), null);
        reloadAdditionalTraits();
    }

    /**
     * Builds the entity-machine state preview tree.
     */
    @Override
    public void loadMachineState() {
        if (editor.getCurrentProject() instanceof EntityMachineProject project) {
            loadMachineStateRecursive(project.getDefinition().stateMachine().getRootState(), new ArrayList<>(), 0);
        }
    }

    private void loadMachineStateRecursive(MachineState state, List<Integer> depthCount, int depth) {
        if (depthCount.size() <= depth) depthCount.add(0);
        var count = depthCount.get(depth);
        depthCount.set(depth, count + 1);
        var preview = new EntityMachineStatePreview(this, state);
        preview.setSelfPosition(new Position(50 + count * 200, 50 + depth * 120));
        preview.collapse();
        floatView.addWidget(preview);
        for (var child : state.children()) {
            loadMachineStateRecursive(child, depthCount, depth + 1);
        }
    }

    /**
     * Adds an entity-machine state preview widget for a newly created state.
     *
     * @param newState state to show in the overlay
     */
    @Override
    public void onStateAdded(MachineState newState) {
        var preview = new EntityMachineStatePreview(this, newState);
        preview.setSelfPosition(new Position((getSize().width - preview.getSize().width) / 2, (getSize().height - preview.getSize().height) / 2));
        floatView.addWidgetAnima(preview, new Transform().duration(200).scale(0.2f));
    }

    /**
     * Opens an entity-state-specific configurator and switches the preview machine to the selected state.
     *
     * @param state selected machine state
     */
    @Override
    public void onStateSelected(MachineState state) {
        editor.getConfigPanel().openConfigurator(MachineEditor.SECOND, new EntityMachineStateConfigurable(state));
        if (previewMachine != null) {
            previewMachine.setMachineState(state.name());
        }
    }

    /**
     * Configurable wrapper that adapts block-machine state configurators for entity-machine state editing.
     *
     * @param state state being configured
     */
    private record EntityMachineStateConfigurable(MachineState state) implements IConfigurable {
        /**
         * Builds the wrapped state configurator and replaces block renderer controls with entity renderer controls.
         *
         * @param father target configurator group
         */
        @Override
        public void buildConfigurator(ConfiguratorGroup father) {
            state.buildConfigurator(father);
            replaceRendererConfigurator(father);
            father.getConfigurators().stream()
                    .filter(configurator -> FRONT_RENDERER_CONFIG.equals(configurator.getName()))
                    .findFirst()
                    .ifPresent(father::removeConfigurator);
        }

        private void replaceRendererConfigurator(ConfiguratorGroup father) {
            var configurators = father.getConfigurators();
            for (int i = 0; i < configurators.size(); i++) {
                var configurator = configurators.get(i);
                if (RENDERER_CONFIG.equals(configurator.getName())) {
                    var replacement = new ConfiguratorGroup(ENTITY_RENDERER_CONFIG, true);
                    replacement.setTips(
                            "config.entity_machine.state.renderer.tooltip.0",
                            "config.entity_machine.state.renderer.tooltip.1");
                    state.renderer().buildConfigurator(replacement);
                    father.removeConfigurator(configurator);
                    father.addConfigurator(i, replacement);
                    return;
                }
            }
        }
    }
}
