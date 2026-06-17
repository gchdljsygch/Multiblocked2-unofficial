package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.kjs.LDLibKubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents LDLib client-only KubeJS bindings from loading on dedicated servers.
 *
 * <p>LDLib's binding registration can touch client classes. MBD cancels it on
 * {@link Dist#DEDICATED_SERVER} so server startup remains safe in packs that include KubeJS and
 * LDLib without a client environment.</p>
 */
@Mixin(value = LDLibKubeJSPlugin.class, remap = false)
public abstract class LDLibKubeJSPluginMixin {
    /**
     * Cancels LDLib binding registration on the dedicated server distribution.
     *
     * @param event KubeJS binding registration event
     * @param ci    callback cancelled only on dedicated servers
     */
    @Inject(method = "registerBindings", at = @At("HEAD"), cancellable = true)
    private void mbd2$skipClientBindingsOnServer(BindingsEvent event, CallbackInfo ci) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ci.cancel();
        }
    }
}
