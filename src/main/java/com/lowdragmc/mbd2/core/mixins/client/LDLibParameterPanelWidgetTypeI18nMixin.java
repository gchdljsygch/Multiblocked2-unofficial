package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.graphprocessor.widget.ParameterPanelWidget;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Locale;

@Mixin(value = ParameterPanelWidget.class, remap = false)
public class LDLibParameterPanelWidgetTypeI18nMixin {
    @ModifyArg(
            method = "loadWidgets",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/texture/TextTexture;<init>(Ljava/lang/String;)V"),
            index = 0,
            remap = false
    )
    private String mbd2$translateTypeTextTexture(String text) {
        if (text == null || text.isEmpty()) return text;
        String altKey = "mbd2.gp." + normalizeKey(text);
        if (I18n.exists(altKey)) return I18n.get(altKey);
        if (I18n.exists(text)) return I18n.get(text);
        return text;
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
