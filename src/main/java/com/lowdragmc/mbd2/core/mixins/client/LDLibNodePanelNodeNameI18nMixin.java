package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

/**
 * Converts LDLib node-menu annotation names into MBD2 graph translation keys.
 *
 * <p>The node panel later runs strings through LDLib's localization path, so this redirect
 * returns a key rather than the translated text. If no normalized key exists, the original
 * annotation name remains visible.</p>
 */
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.graphprocessor.widget.NodePanelWidget$NodeGroupWidget", remap = false)
public class LDLibNodePanelNodeNameI18nMixin {
    /**
     * Redirects menu-name lookup for node entries loaded from {@link LDLRegister}.
     *
     * @param annotation LDLib registration annotation on the node class
     * @return an MBD2 translation key when present, otherwise the annotation's raw name
     */
    @Redirect(
            method = "reloadNodes",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/editor/annotation/LDLRegister;name()Ljava/lang/String;", ordinal = 1),
            remap = false
    )
    private String mbd2$translateNodeMenuName(LDLRegister annotation) {
        String raw = annotation == null ? "" : annotation.name();
        if (raw.isEmpty()) return raw;
        String key = "mbd2.gp." + normalizeKey(raw);
        return I18n.exists(key) ? key : raw;
    }

    /**
     * Normalizes LDLib annotation names to the language-key suffix used by graph entries.
     *
     * @param key raw annotation name
     * @return sanitized, lower-case key suffix
     */
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
