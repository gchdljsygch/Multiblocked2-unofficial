package com.lowdragmc.mbd2.common.gui.editor.machine;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.client.renderer.EntityMachineRenderer;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.gui.editor.EntityMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Utility for installing and rendering entity-machine previews in editor scenes.
 *
 * <p>Entity-machine previews are drawn manually after the LDLib scene world render because the preview entity may use a
 * vanilla marker entity type that the normal scene entity dispatcher cannot render as an MBD machine. The helper keeps
 * the machine holder in the dummy world for lookups and renders the machine renderer directly.</p>
 */
public final class EntityMachinePreviewScene {
    private EntityMachinePreviewScene() {
    }

    /**
     * Creates a preview entity, installs its holder into the dummy world, and hooks the scene render callback.
     *
     * @param editor           owning editor; must currently load an {@link EntityMachineProject}
     * @param scene            scene widget to hook
     * @param level            dummy world used by the scene
     * @param stateName        optional state name to apply to the preview machine
     * @param afterWorldRender optional callback to run after the entity is drawn
     * @return preview machine, or {@code null} when the current project/entity cannot produce one
     */
    @Nullable
    public static MBDEntityMachine create(MachineEditor editor,
                                          SceneWidget scene,
                                          TrackedDummyWorld level,
                                          @Nullable String stateName,
                                          @Nullable Consumer<SceneWidget> afterWorldRender) {
        if (!(editor.getCurrentProject() instanceof EntityMachineProject project)) {
            return null;
        }
        var entity = project.getDefinition().createPreviewEntity(level);
        entity.setPos(0.5D, 0.0D, 0.5D);
        if (!(entity instanceof IMachineEntity machineEntity) ||
                !(machineEntity.getMetaMachine() instanceof MBDEntityMachine machine)) {
            return null;
        }
        // Keep the holder in the dummy world for machine lookups, but draw the entity machine directly.
        // Preview entities use vanilla marker/armor stand types, so LDLib's entity dispatcher cannot render them correctly.
        level.setBlockEntity(machine.entityHolder());
        if (stateName != null) {
            machine.setMachineState(stateName);
        }
        scene.setAfterWorldRender(widget -> {
            renderPreviewEntity(entity);
            if (afterWorldRender != null) {
                afterWorldRender.accept(widget);
            }
        });
        return machine;
    }

    /**
     * Renders a preview machine entity at its current position.
     *
     * <p>The method mutates RenderSystem depth/blend state and flushes the buffer source before restoring the scene's
     * expected translucent overlay state.</p>
     *
     * @param entity preview entity created by the active entity-machine definition
     */
    private static void renderPreviewEntity(Entity entity) {
        var minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        var poseStack = new PoseStack();
        poseStack.pushPose();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            poseStack.translate(entity.getX(), entity.getY(), entity.getZ());
            EntityMachineRenderer.renderMachine(entity, minecraft.getFrameTime(), poseStack, buffers, 0xF000F0);
        } finally {
            poseStack.popPose();
            try {
                buffers.endBatch();
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
            }
        }
    }
}
