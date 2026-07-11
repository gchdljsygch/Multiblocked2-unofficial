package com.lowdragmc.mbd2.core.mixins.create;

import com.simibubi.create.compat.jei.category.animations.AnimatedSpout;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Prevents Create's JEI spout animation from crashing on unresolved fluid tags.
 *
 * <p>Create assumes every filling recipe has at least one matching fluid stack,
 * but generated or proxy recipes can legally carry a tag that resolves to no
 * fluids in the current datapack set. Keep the recipe visible and render an
 * empty fluid placeholder instead of letting the animation index an empty list.</p>
 */
@Mixin(value = AnimatedSpout.class, remap = false)
public class AnimatedSpoutMixin {
    @Shadow
    private List<FluidStack> fluids;

    /**
     * Normalizes null or empty fluid candidate lists before Create later reads
     * index {@code 0} during drawing.
     *
     * @param fluids fluid candidates supplied by the JEI category
     * @param cir    callback receiving the original fluent return value
     */
    @Inject(method = "withFluids", at = @At("HEAD"), cancellable = true)
    private void mbd2$guardEmptyFluidList(List<FluidStack> fluids, CallbackInfoReturnable<AnimatedSpout> cir) {
        if (fluids == null || fluids.isEmpty()) {
            this.fluids = List.of(FluidStack.EMPTY);
            cir.setReturnValue((AnimatedSpout) (Object) this);
        }
    }
}
