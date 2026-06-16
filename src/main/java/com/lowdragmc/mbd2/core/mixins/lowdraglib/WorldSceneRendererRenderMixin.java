package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.utils.PositionedRect;
import com.lowdragmc.mbd2.MBD2;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class WorldSceneRendererRenderMixin {
    @Unique
    private static boolean mbd2$loggedSceneRenderFailure;

    @Shadow
    private Consumer<BlockHitResult> onLookingAt;

    @Shadow
    private Vector3f lastHit;

    @Shadow
    private BlockHitResult lastTraceResult;

    @Shadow
    public abstract PositionedRect getPositionedRect(int x, int y, int width, int height);

    @Shadow
    protected abstract void setupCamera(PositionedRect viewport);

    @Shadow
    protected abstract void drawWorld();

    @Shadow
    public abstract Vector3f unProject(int mouseX, int mouseY);

    @Shadow
    public abstract BlockHitResult rayTrace(Vector3f hitPos);

    @Shadow
    protected abstract void resetCamera();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mbd2$renderSafely(@Nonnull PoseStack poseStack, float x, float y, float width, float height, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) {
            return;
        }

        var pose = poseStack.last().pose();
        Vector4f pos = new Vector4f(x, y, 0, 1.0F);
        pos = pose.transform(pos);
        Vector4f size = new Vector4f(x + width, y + height, 0, 1.0F);
        size = pose.transform(size);
        x = pos.x();
        y = pos.y();
        width = size.x() - x;
        height = size.y() - y;
        PositionedRect viewport = getPositionedRect((int) x, (int) y, (int) width, (int) height);
        var topLeft = poseStack.last().pose().transformPosition(new Vector3f(0.0f, 0.0f, 0.0f));
        PositionedRect mouse = getPositionedRect((int) (mouseX + topLeft.x), (int) (mouseY + topLeft.y), 0, 0);
        mouseX = mouse.position.x;
        mouseY = mouse.position.y;

        boolean cameraSetup = false;
        try {
            setupCamera(viewport);
            cameraSetup = true;
            drawWorld();
            lastTraceResult = null;
            lastHit = unProject(mouseX, mouseY);
            if (onLookingAt != null && mouseX > viewport.position.x && mouseX < viewport.position.x + viewport.size.width
                    && mouseY > viewport.position.y && mouseY < viewport.position.y + viewport.size.height) {
                BlockHitResult result = rayTrace(lastHit);
                if (result != null) {
                    lastTraceResult = result;
                    onLookingAt.accept(result);
                }
            }
        } catch (RuntimeException exception) {
            lastTraceResult = null;
            lastHit = null;
            mbd2$logSceneRenderFailure("render", exception);
        } finally {
            mbd2$endSceneBatch();
            if (cameraSetup) {
                try {
                    resetCamera();
                } catch (RuntimeException exception) {
                    mbd2$logSceneRenderFailure("camera reset", exception);
                }
            }
            mbd2$restoreGuiRenderState();
        }
    }

    @Unique
    private static void mbd2$endSceneBatch() {
        try {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (RuntimeException ignored) {
        }
    }

    @Unique
    private static void mbd2$restoreGuiRenderState() {
        for (RenderType renderType : RenderType.chunkBufferLayers()) {
            try {
                renderType.clearRenderState();
            } catch (RuntimeException ignored) {
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
    }

    @Unique
    private static void mbd2$logSceneRenderFailure(String phase, RuntimeException exception) {
        if (!mbd2$loggedSceneRenderFailure) {
            mbd2$loggedSceneRenderFailure = true;
            MBD2.LOGGER.warn("LDLib scene renderer failed during {}; skipped this preview frame and restored GUI render state.", phase, exception);
            return;
        }
        MBD2.LOGGER.debug("LDLib scene renderer failed during {}", phase, exception);
    }
}
