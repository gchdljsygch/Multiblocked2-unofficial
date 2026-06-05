package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.kjs.LDLibKubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LDLibKubeJSPlugin.class, remap = false)
public abstract class LDLibKubeJSPluginMixin {
    @Inject(method = "registerBindings", at = @At("HEAD"), cancellable = true)
    private void mbd2$skipClientBindingsOnServer(BindingsEvent event, CallbackInfo ci) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ci.cancel();
        }
    }
}
