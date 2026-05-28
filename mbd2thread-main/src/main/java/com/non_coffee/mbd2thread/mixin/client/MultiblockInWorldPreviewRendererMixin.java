package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.mbd2.client.renderer.MultiblockInWorldPreviewRenderer;
import com.non_coffee.mbd2thread.client.MultiblockDebugOverlay;
import com.non_coffee.mbd2thread.client.render.OverlayRenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = MultiblockInWorldPreviewRenderer.class, remap = false)
public class MultiblockInWorldPreviewRendererMixin {
    @Inject(method = "onClientTick", at = @At("TAIL"), remap = false)
    private static void mbd2thread$tickOverlay(CallbackInfo ci) {
        MultiblockDebugOverlay.tick();
    }

    @Inject(method = "renderInWorldPreview", at = @At("HEAD"), remap = false)
    private static void mbd2thread$renderOverlay(PoseStack poseStack, Camera camera, float partialTicks, CallbackInfo ci) {
        Set<BlockPos> positions = MultiblockDebugOverlay.getPositions();
        if (positions == null) return;

        poseStack.pushPose();
        Vec3 projectedView = camera.getPosition();
        poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        for (BlockPos pos : positions) {
            OverlayRenderUtil.renderSolidBlockOverlay(poseStack, pos, 1.0f, 0.0f, 0.0f, 0.35f, 1.01f);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        poseStack.popPose();
    }
}
