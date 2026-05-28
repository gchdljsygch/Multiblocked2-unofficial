package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.non_coffee.mbd2thread.energy.fe.compat.RecipeCapabilitiesProxyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MBDMachine.class, remap = false)
public class MBDMachineInitCapabilitiesProxyMixin {
    @Inject(method = "initCapabilitiesProxy", at = @At("RETURN"))
    private void mbd2thread$initCapabilitiesProxy(CallbackInfo ci) {
        RecipeCapabilitiesProxyCompat.apply(((MBDMachine) (Object) this).getRecipeCapabilitiesProxy());
    }
}

