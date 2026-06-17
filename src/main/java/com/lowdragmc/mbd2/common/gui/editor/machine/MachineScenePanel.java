package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.MenuPanel;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.*;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.blockentity.MachineBlockEntity;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.MachineProject;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Base editor panel that displays a machine preview inside an LDLib scene.
 *
 * <p>The panel owns a {@link TrackedDummyWorld}, a {@link SceneWidget}, and an overlay button group. It creates a
 * preview machine from the current {@link MachineProject} and can draw shape/rendering-box frame overlays after the
 * scene world is rendered. All state is editor UI state and should be accessed on the client UI/render thread.</p>
 */
@Getter
public class MachineScenePanel extends WidgetGroup {
    /**
     * Editor that owns this panel.
     */
    protected final MachineEditor editor;
    /**
     * Dummy world used by the preview scene.
     */
    protected final TrackedDummyWorld level;
    /**
     * Scene widget that renders the preview machine.
     */
    protected final SceneWidget scene;
    /**
     * Floating button strip for panel toggles.
     */
    protected final WidgetGroup buttonGroup;
    /**
     * Machine instance currently displayed in the preview, or {@code null} when no project is active.
     */
    @Nullable
    protected MBDMachine previewMachine;
    /**
     * Whether to draw the machine state's collision/shape edges.
     */
    @Setter
    protected boolean drawShapeFrameLines = false;
    /**
     * Whether to draw the machine state's rendering-box edges.
     */
    @Setter
    protected boolean drawRenderingBoxFrameLines = false;

    /**
     * Creates a machine scene panel sized to the editor content area.
     *
     * @param editor owning machine editor
     */
    public MachineScenePanel(MachineEditor editor) {
        super(0, MenuPanel.HEIGHT, editor.getSize().getWidth() - ConfigPanel.WIDTH, editor.getSize().height - MenuPanel.HEIGHT - 16);
        this.editor = editor;
        addWidget(scene = new SceneWidget(0, 0, this.getSize().width, this.getSize().height, null));
        addWidget(buttonGroup = new WidgetGroup(0, 0, this.getSize().width, this.getSize().height));
        scene.setRenderFacing(false);
        scene.setRenderSelect(false);
        scene.createScene(level = new TrackedDummyWorld());
        scene.getRenderer().setOnLookingAt(null); // better performance
        scene.setRenderedCore(Collections.singleton(BlockPos.ZERO), null);
        scene.setAfterWorldRender(this::renderAfterWorld);
        scene.getRenderer().setEndBatchLast(false);
        resetScene();
        prepareButtonGroup();
    }

    /**
     * Resets the dummy world and recreates the block-machine preview.
     *
     * <p>The method clears the level, places the fake machine block at the origin, creates a machine from the current
     * project definition, and reloads preview traits. Call this when the definition changes enough that the holder or
     * machine instance must be rebuilt.</p>
     */
    public void resetScene() {
        this.level.clear();
        this.level.addBlock(BlockPos.ZERO, BlockInfo.fromBlock(MBDRegistries.FAKE_MACHINE().block()));
        Optional.ofNullable(this.level.getBlockEntity(BlockPos.ZERO)).ifPresent(blockEntity -> {
            if (blockEntity instanceof MachineBlockEntity holder && editor.getCurrentProject() instanceof MachineProject project) {
                holder.setMachine(this.previewMachine = project.getDefinition().createMachine(holder));
            }
        });
        reloadAdditionalTraits();
    }

    /**
     * Reloads additional traits on the preview machine and invokes preview-load callbacks.
     */
    public void reloadAdditionalTraits() {
        if (previewMachine != null) {
            previewMachine.loadAdditionalTraits();
            previewMachine.getAdditionalTraits().forEach(ITrait::onLoadingTraitInPreview);
        }
    }

    /**
     * Rebuilds the panel's overlay button group.
     *
     * <p>Subclasses may override or append controls; the base implementation adds toggles for shape and rendering-box
     * frame overlays.</p>
     */
    public void prepareButtonGroup() {
        buttonGroup.clearAllWidgets();
        addSwitch(Icons.icon(MBD2.MOD_ID, "cube_outline"), null, "editor.machine_scene.draw_shape_frame_lines", this::isDrawShapeFrameLines, this::setDrawShapeFrameLines);
        addSwitch(Icons.icon(MBD2.MOD_ID, "cube_outline").copy().setColor(0xffeedd00), null, "editor.machine_scene.draw_rendering_box_frame_lines", this::isDrawRenderingBoxFrameLines, this::setDrawRenderingBoxFrameLines);
        refreshButtonGroupPosition();
    }

    /**
     * Adds a boolean switch to the overlay button group.
     *
     * @param baseTexture    icon shown in both states when {@code pressedTexture} is absent
     * @param pressedTexture optional icon for the pressed state
     * @param tooltips       optional translation key for hover text
     * @param getter         state supplier used by the switch
     * @param setter         callback invoked when the switch changes
     */
    public void addSwitch(IGuiTexture baseTexture, @Nullable IGuiTexture pressedTexture, @Nullable String tooltips, BooleanSupplier getter, BooleanConsumer setter) {
        var switchWidget = new SwitchWidget(buttonGroup.widgets.size() * 25, 0, 20, 20,
                (cd, pressed) -> setter.accept(pressed.booleanValue()))
                .setSupplier(getter::getAsBoolean).setTexture(
                        new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture().setRadius(5), baseTexture),
                        new GuiTextureGroup(ColorPattern.T_GREEN.rectTexture().setRadius(5), pressedTexture == null ? baseTexture : pressedTexture))
                .setHoverTooltips(tooltips);
        buttonGroup.addWidget(switchWidget);
    }

    /**
     * Adds a push button to the overlay button group.
     *
     * @param baseTexture button icon
     * @param tooltips    optional translation key for hover text
     * @param action      action invoked on click
     */
    public void addButton(IGuiTexture baseTexture, @Nullable String tooltips, Runnable action) {
        var buttonWidget = new ButtonWidget(buttonGroup.widgets.size() * 25, 0, 20, 20,
                new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture().setRadius(5), baseTexture),
                cd -> action.run()).setHoverTooltips(tooltips);
        buttonGroup.addWidget(buttonWidget);
    }

    /**
     * Repositions the overlay button group in the top-right corner of the panel.
     */
    public void refreshButtonGroupPosition() {
        buttonGroup.setSize(new Size(Math.max(0, buttonGroup.widgets.size() * 25 - 5), 20));
        buttonGroup.setSelfPosition(new Position(this.getSize().width - buttonGroup.getSize().width - 25, 25));
    }

    /**
     * Renders preview overlays after the scene world has rendered.
     *
     * <p>The method draws collision-shape and rendering-box wireframes when their toggles are enabled. It mutates
     * OpenGL/RenderSystem line, blend, cull, and depth state for the duration of the overlay pass.</p>
     *
     * @param sceneWidget scene that just completed world rendering
     */
    public void renderAfterWorld(SceneWidget sceneWidget) {
        if (previewMachine == null) return;
        var drawFrameLines = drawShapeFrameLines || drawRenderingBoxFrameLines;
        var poseStack = new PoseStack();
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        var matrix4f = poseStack.last().pose();
        var normal = poseStack.last().normal();
        if (drawFrameLines) {

            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            poseStack.pushPose();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            RenderSystem.lineWidth(5);
        }

        if (drawShapeFrameLines) {
            previewMachine.getMachineState().getShape(null).forAllEdges((x0, y0, z0, x1, y1, z1) -> {
                float f = (float) (x1 - x0);
                float f1 = (float) (y1 - y0);
                float f2 = (float) (z1 - z0);
                float f3 = Mth.sqrt(f * f + f1 * f1 + f2 * f2);
                f /= f3;
                f1 /= f3;
                f2 /= f3;
                buffer.vertex(matrix4f, (float) (x0), (float) (y0), (float) (z0)).color(-1).normal(normal, f, f1, f2).endVertex();
                buffer.vertex(matrix4f, (float) (x1), (float) (y1), (float) (z1)).color(-1).normal(normal, f, f1, f2).endVertex();
            });
        }

        if (drawRenderingBoxFrameLines) {
            var aabb = previewMachine.getMachineState().getRenderingBox(null);
            if (aabb != null) {
                var color = 0xffeedd00;
                RenderBufferUtils.drawCubeFrame(poseStack, buffer,
                        (float) aabb.minX, (float) aabb.minY, (float) aabb.minZ,
                        (float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ,
                        ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color), ColorUtils.alpha(color));
            }
        }

        if (drawFrameLines) {
            tessellator.end();

            poseStack.popPose();
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }
}
