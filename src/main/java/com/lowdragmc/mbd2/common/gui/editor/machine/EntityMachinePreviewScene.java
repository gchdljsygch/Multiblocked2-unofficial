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

public final class EntityMachinePreviewScene {
    private EntityMachinePreviewScene() {
    }

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
