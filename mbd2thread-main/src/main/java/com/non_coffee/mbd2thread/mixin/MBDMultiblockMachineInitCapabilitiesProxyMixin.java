package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.non_coffee.mbd2thread.energy.fe.compat.RecipeCapabilitiesProxyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MBDMultiblockMachine.class, remap = false)
public class MBDMultiblockMachineInitCapabilitiesProxyMixin {
    @Inject(method = "initCapabilitiesProxy", at = @At("RETURN"))
    private void mbd2thread$initCapabilitiesProxy(CallbackInfo ci) {
        RecipeCapabilitiesProxyCompat.apply(((MBDMultiblockMachine) (Object) this).getRecipeCapabilitiesProxy());
    }
}

