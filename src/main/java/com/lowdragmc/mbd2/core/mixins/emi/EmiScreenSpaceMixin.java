package com.lowdragmc.mbd2.core.mixins.emi;

import dev.emi.emi.screen.StackBatcher;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps EMI's sidebar renderer alive when a custom LDLib screen is rebuilt while the client is
 * leaving a world.
 *
 * <p>EMI 1.0.5 obtains its stack batcher from a shared pool. A stale pool entry can leave a
 * {@code ScreenSpace} with a null batcher, which otherwise crashes the render thread on the next
 * sidebar frame.</p>
 */
@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class EmiScreenSpaceMixin {

    @Shadow
    @Final
    @Mutable
    public StackBatcher batcher;

    /**
     * Replaces a missing pooled batcher before EMI can use the screen space.
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void mbd2$ensureBatcher(CallbackInfo ci) {
        if (batcher == null) {
            batcher = new StackBatcher();
        }
    }

    /**
     * Handles a batcher becoming unavailable after construction without taking down the client.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$skipUninitializedRender(CallbackInfo ci) {
        if (batcher == null) {
            ci.cancel();
        }
    }
}
