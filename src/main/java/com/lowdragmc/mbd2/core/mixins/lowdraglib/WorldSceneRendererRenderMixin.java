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

/**
 * Replaces LDLib scene rendering with a guarded render path that restores GUI state.
 *
 * <p>Editor scene previews render arbitrary generated worlds and renderer resources. If a preview
 * renderer throws, LDLib can leave camera, batch, depth, or render-type state dirty. This mixin
 * skips rendering during Minecraft's loading overlay, catches preview failures, clears batches,
 * resets the camera, and restores GUI render state for following widgets.</p>
 */
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

    /**
     * Renders a world-scene widget with exception handling and explicit render-state restoration.
     *
     * @param poseStack GUI pose stack
     * @param x         scene X coordinate
     * @param y         scene Y coordinate
     * @param width     scene width
     * @param height    scene height
     * @param mouseX    current mouse X
     * @param mouseY    current mouse Y
     * @param ci        callback always cancelled because this method replaces LDLib's render body
     */
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

    /**
     * Flushes Minecraft's main buffer source after scene rendering.
     */
    @Unique
    private static void mbd2$endSceneBatch() {
        try {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Restores render-type and GL state expected by normal GUI widgets.
     */
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

    /**
     * Logs the first scene render failure at warn level and later failures at debug level.
     *
     * @param phase     render phase that failed
     * @param exception thrown renderer exception
     */
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
