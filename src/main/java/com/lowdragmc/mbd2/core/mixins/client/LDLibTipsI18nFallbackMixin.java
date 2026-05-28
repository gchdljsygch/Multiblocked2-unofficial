package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(value = LocalizationUtils.class, remap = false)
public class LDLibTipsI18nFallbackMixin {
    @Inject(method = "format", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mbd2$hideMissingTipsKey(String localisationKey, Object[] substitutions, CallbackInfoReturnable<String> cir) {
        if (localisationKey == null) return;
        if (localisationKey.startsWith("\u0000")) {
            cir.setReturnValue(localisationKey.substring(1));
            return;
        }
        if (LocalizationUtils.RESOURCE != null && LocalizationUtils.RESOURCE.hasBuiltinResource(localisationKey)) return;
        if (I18n.exists(localisationKey)) return;

        String altKey = "mbd2.gp." + normalizeKey(localisationKey);
        if (I18n.exists(altKey)) {
            cir.setReturnValue(I18n.get(altKey, substitutions));
            return;
        }

        if (localisationKey.endsWith(".tips")) {
            cir.setReturnValue("");
        }
    }

    private static String normalizeKey(String key) {
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(k.length());
        boolean underscore = false;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_';
            if (ok) {
                sb.append(c);
                underscore = false;
            } else if (!underscore) {
                sb.append('_');
                underscore = true;
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == '_') start++;
        while (end > start && sb.charAt(end - 1) == '_') end--;
        return sb.substring(start, end);
    }
}
