package com.lowdragmc.mbd2.common.gui.editor.multiblock;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.MenuPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.sceneeditor.SceneEditorWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.widget.ShapeInfoList;
import lombok.Getter;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

/**
 * Preview panel for concrete multiblock shape variants.
 *
 * <p>The panel renders selected {@link com.lowdragmc.mbd2.api.pattern.MultiblockShapeInfo}
 * data into a {@link TrackedDummyWorld} and delegates the list of available variants to
 * {@link ShapeInfoList}. Shape previews are read-only from this panel; deletion and list
 * rebuilds are handled by the side-list widget.</p>
 *
 * <p>This class is client-editor UI and should be used only from the render/UI thread.</p>
 */
public class MultiblockShapeInfoPanel extends WidgetGroup {
    @Getter
    protected final MachineEditor editor;
    @Getter
    protected final MultiblockMachineProject project;
    @Getter
    protected final TrackedDummyWorld level;
    @Getter
    protected final SceneEditorWidget scene;
    protected final WidgetGroup buttonGroup;

    /**
     * Creates the shape-info preview panel for a multiblock project.
     *
     * @param editor  owning editor used for tool and config panels
     * @param project project that supplies explicit or auto-built shape previews
     */
    public MultiblockShapeInfoPanel(MachineEditor editor, MultiblockMachineProject project) {
        super(0, MenuPanel.HEIGHT + 16, Editor.INSTANCE.getSize().getWidth() - ConfigPanel.WIDTH, Editor.INSTANCE.getSize().height - MenuPanel.HEIGHT - 16);
        this.editor = editor;
        this.project = project;
        addWidget(scene = new SceneEditorWidget(0, 0, this.getSize().width, this.getSize().height, null));
        addWidget(buttonGroup = new WidgetGroup(0, 0, this.getSize().width, this.getSize().height));
        scene.disableTransformGizmo();
        scene.setRenderFacing(false);
        scene.setRenderSelect(false);
        scene.createScene(level = new TrackedDummyWorld());
        scene.useCacheBuffer();
        prepareButtonGroup();
        buttonGroup.setSize(new Size(Math.max(0, buttonGroup.widgets.size() * 25 - 5), 20));
        buttonGroup.setSelfPosition(new Position(this.getSize().width - buttonGroup.getSize().width - 25, 25));
    }

    /**
     * Clears the rendered preview and closes the shape-specific configurator.
     */
    public void clearShapeInfo() {
        scene.setRenderedCore(Collections.emptyList());
        editor.getConfigPanel().clearAllConfigurators(MachineEditor.BASIC);
    }

    /**
     * Displays a selected shape preview.
     *
     * @param configurable optional shape object to expose in the config panel; {@code null}
     *                     means the preview is generated from the pattern and is not directly
     *                     editable
     * @param blocks       block positions already populated in the panel dummy world and intended
     *                     to be rendered by the scene
     */
    public void loadShapeInfo(@Nullable IConfigurable configurable, Collection<BlockPos> blocks) {
        scene.setRenderedCore(blocks);
        if (configurable != null) {
            editor.getConfigPanel().openConfigurator(MachineEditor.BASIC, configurable);
        } else {
            editor.getConfigPanel().clearAllConfigurators(MachineEditor.BASIC);
        }
    }

    /**
     * Installs the shape-info toolbox and resets the currently displayed preview.
     */
    public void onPanelSelected() {
        editor.getConfigPanel().clearAllConfigurators();
        editor.getToolPanel().clearAllWidgets();
        editor.getToolPanel().setTitle("editor.machine.multiblock.multiblock_shape_info");
        editor.getToolPanel().addNewToolBox("editor.machine.multiblock.multiblock_shape_info", Icons.WIDGET_CUSTOM, size -> new ShapeInfoList(this, size));
        if (editor.getToolPanel().inAnimate()) {
            editor.getToolPanel().getAnimation().appendOnFinish(() -> editor.getToolPanel().show());
        } else {
            editor.getToolPanel().show();
        }
        clearShapeInfo();
    }

    /**
     * Hides shape-info tooling and clears editor configurators.
     */
    public void onPanelDeselected() {
        editor.getToolPanel().setTitle("ldlib.gui.editor.group.tool_box");
        editor.getToolPanel().hide();
        editor.getToolPanel().clearAllWidgets();
        editor.getConfigPanel().clearAllConfigurators();
    }

    /**
     * Adds extra toolbar controls for subclasses.
     *
     * <p>The base shape-info panel uses only the side toolbox.</p>
     */
    protected void prepareButtonGroup() {
    }
}
