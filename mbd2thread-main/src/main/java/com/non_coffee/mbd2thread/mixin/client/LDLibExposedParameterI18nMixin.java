package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(value = ExposedParameter.class, remap = false)
public class LDLibExposedParameterI18nMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2thread$translateDisplayName(CallbackInfoReturnable<String> cir) {
        String raw = cir.getReturnValue();
        if (raw == null || raw.isEmpty()) return;
        String key = "mbd2thread.gp." + normalizeKey(raw);
        if (I18n.exists(key)) {
            cir.setReturnValue(I18n.get(key));
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
