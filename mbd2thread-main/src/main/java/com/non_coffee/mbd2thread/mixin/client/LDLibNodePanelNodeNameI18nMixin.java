package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

@Mixin(targets = "com.lowdragmc.lowdraglib.gui.graphprocessor.widget.NodePanelWidget$NodeGroupWidget", remap = false)
public class LDLibNodePanelNodeNameI18nMixin {
    @Redirect(
            method = "reloadNodes",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/editor/annotation/LDLRegister;name()Ljava/lang/String;", ordinal = 1),
            remap = false
    )
    private String mbd2thread$translateNodeMenuName(LDLRegister annotation) {
        String raw = annotation == null ? "" : annotation.name();
        if (raw.isEmpty()) return raw;
        String key = "mbd2thread.gp." + normalizeKey(raw);
        return I18n.exists(key) ? key : raw;
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
