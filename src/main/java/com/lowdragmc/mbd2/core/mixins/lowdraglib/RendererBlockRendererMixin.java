package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.renderer.block.RendererBlockEntity;
import com.lowdragmc.mbd2.MBD2;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Hardens LDLib renderer-resource previews against renderer exceptions and leaked render state.
 *
 * <p>Renderer resources are user-editable and can temporarily point at invalid assets. These
 * injections replace LDLib's direct preview calls with guarded versions that return conservative
 * fallbacks, log the first failure, and restore OpenGL state after TESR preview rendering.</p>
 */
@Mixin(targets = "com.lowdragmc.lowdraglib.client.renderer.block.RendererBlockRenderer", remap = false)
public abstract class RendererBlockRendererMixin {
    @Unique
    private static boolean mbd2$loggedResourcePreviewFailure;

    @Shadow
    public abstract Optional<RendererBlockEntity> getMachine(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos);

    @Shadow
    public abstract Optional<RendererBlockEntity> getMachine(@Nullable BlockEntity blockEntity);

    /**
     * Safely checks whether a preview renderer wants TESR rendering.
     *
     * @param blockEntity preview block entity
     * @param cir         callback receiving {@code false} when lookup or renderer code fails
     */
    @Inject(method = "hasTESR", at = @At("HEAD"), cancellable = true)
    private void mbd2$hasTesrSafely(BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        try {
            cir.setReturnValue(getMachine(blockEntity)
                    .map(machine -> machine.getRenderer().hasTESR(blockEntity))
                    .orElse(false));
        } catch (RuntimeException exception) {
            mbd2$logResourcePreviewFailure("tesr check", exception);
            cir.setReturnValue(false);
        }
    }

    /**
     * Safely checks whether a preview renderer should be treated as global.
     *
     * @param blockEntity preview block entity
     * @param cir         callback receiving {@code false} on failure
     */
    @Inject(method = "isGlobalRenderer", at = @At("HEAD"), cancellable = true)
    private void mbd2$isGlobalRendererSafely(BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        try {
            cir.setReturnValue(getMachine(blockEntity)
                    .map(machine -> machine.getRenderer().isGlobalRenderer(blockEntity))
                    .orElse(false));
        } catch (RuntimeException exception) {
            mbd2$logResourcePreviewFailure("global renderer check", exception);
            cir.setReturnValue(false);
        }
    }

    /**
     * Safely runs renderer visibility logic for resource previews.
     *
     * @param blockEntity preview block entity
     * @param cameraPos   current camera position
     * @param cir         callback receiving a conservative visibility result
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void mbd2$shouldRenderSafely(BlockEntity blockEntity, Vec3 cameraPos, CallbackInfoReturnable<Boolean> cir) {
        try {
            cir.setReturnValue(getMachine(blockEntity)
                    .map(machine -> machine.getRenderer().shouldRender(blockEntity, cameraPos))
                    .orElse(Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, 64.0D)));
        } catch (RuntimeException exception) {
            mbd2$logResourcePreviewFailure("visibility check", exception);
            cir.setReturnValue(true);
        }
    }

    /**
     * Safely obtains block-model quads for renderer resource previews.
     *
     * @param level preview level
     * @param pos   preview position
     * @param state preview block state
     * @param side  queried render side
     * @param rand  random source for model lookup
     * @param cir   callback receiving quads or an empty list on failure
     */
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void mbd2$renderResourceModelSafely(@Nullable BlockAndTintGetter level,
                                                @Nullable BlockPos pos,
                                                @Nullable BlockState state,
                                                @Nullable Direction side,
                                                RandomSource rand,
                                                CallbackInfoReturnable<List<BakedQuad>> cir) {
        try {
            cir.setReturnValue(getMachine(level, pos)
                    .map(machine -> machine.getRenderer().renderModel(level, pos, state, side, rand))
                    .orElseGet(Collections::emptyList));
        } catch (RuntimeException exception) {
            mbd2$logResourcePreviewFailure("model", exception);
            cir.setReturnValue(Collections.emptyList());
        }
    }

    /**
     * Safely invokes TESR preview rendering and restores GUI render state afterward.
     *
     * @param blockEntity     preview block entity
     * @param partialTicks    frame partial ticks
     * @param stack           pose stack
     * @param buffer          buffer source
     * @param combinedLight   packed light value
     * @param combinedOverlay packed overlay value
     * @param ci              callback always cancelled because this method replaces LDLib's body
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mbd2$renderResourceTesrSafely(BlockEntity blockEntity,
                                               float partialTicks,
                                               PoseStack stack,
                                               MultiBufferSource buffer,
                                               int combinedLight,
                                               int combinedOverlay,
                                               CallbackInfo ci) {
        try {
            getMachine(blockEntity).ifPresent(machine ->
                    machine.getRenderer().render(blockEntity, partialTicks, stack, buffer, combinedLight, combinedOverlay));
        } catch (RuntimeException exception) {
            mbd2$logResourcePreviewFailure("tesr", exception);
        } finally {
            mbd2$restoreResourcePreviewState();
            ci.cancel();
        }
    }

    /**
     * Restores common render-system state expected by LDLib GUI widgets after preview rendering.
     */
    @Unique
    private static void mbd2$restoreResourcePreviewState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    /**
     * Logs the first preview failure at warn level and later failures at debug level.
     *
     * @param phase     preview phase that failed
     * @param exception thrown renderer exception
     */
    @Unique
    private static void mbd2$logResourcePreviewFailure(String phase, RuntimeException exception) {
        if (!mbd2$loggedResourcePreviewFailure) {
            mbd2$loggedResourcePreviewFailure = true;
            MBD2.LOGGER.warn("LDLib renderer resource preview failed during {}; skipped this preview renderer.", phase, exception);
            return;
        }
        MBD2.LOGGER.debug("LDLib renderer resource preview failed during {}", phase, exception);
    }
}
