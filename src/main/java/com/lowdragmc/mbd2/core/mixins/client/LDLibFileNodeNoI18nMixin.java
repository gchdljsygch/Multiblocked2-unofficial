package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.util.FileNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks LDLib file-node names as literal text before they pass through localization helpers.
 *
 * <p>The prefix inserted here is consumed by {@link LDLibTipsI18nFallbackMixin}. It prevents
 * workspace file names from being interpreted as translation keys, which would otherwise hide or
 * replace real file names in the editor.</p>
 */
@Mixin(value = FileNode.class, remap = false)
public class LDLibFileNodeNoI18nMixin {
    /**
     * Prefixes the file-node string with an internal sentinel that means "do not localize".
     *
     * @param cir callback containing the original file name and receiving the sentinel-prefixed
     *            value
     */
    @Inject(method = "toString", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2$skipLocalizationForFileName(CallbackInfoReturnable<String> cir) {
        String raw = cir.getReturnValue();
        if (raw == null || raw.isEmpty()) return;
        cir.setReturnValue("\u0000" + raw);
    }
}

