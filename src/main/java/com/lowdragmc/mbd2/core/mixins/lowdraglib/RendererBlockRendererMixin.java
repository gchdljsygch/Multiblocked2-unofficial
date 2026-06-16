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

@Mixin(targets = "com.lowdragmc.lowdraglib.client.renderer.block.RendererBlockRenderer", remap = false)
public abstract class RendererBlockRendererMixin {
    @Unique
    private static boolean mbd2$loggedResourcePreviewFailure;

    @Shadow
    public abstract Optional<RendererBlockEntity> getMachine(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos);

    @Shadow
    public abstract Optional<RendererBlockEntity> getMachine(@Nullable BlockEntity blockEntity);

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

    @Unique
    private static void mbd2$restoreResourcePreviewState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

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
