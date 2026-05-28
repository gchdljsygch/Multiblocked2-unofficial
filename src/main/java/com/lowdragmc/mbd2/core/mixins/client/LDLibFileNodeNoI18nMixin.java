package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.util.FileNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FileNode.class, remap = false)
public class LDLibFileNodeNoI18nMixin {
    @Inject(method = "toString", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2$skipLocalizationForFileName(CallbackInfoReturnable<String> cir) {
        String raw = cir.getReturnValue();
        if (raw == null || raw.isEmpty()) return;
        cir.setReturnValue("\u0000" + raw);
    }
}

