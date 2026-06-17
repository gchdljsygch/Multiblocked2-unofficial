package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.graphprocessor.widget.NodePortWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Localizes LDLib graph node port labels, types, and tooltip lines.
 *
 * <p>Port widgets size themselves from the original text during initialization. After replacing
 * the visible strings this mixin also recalculates the widget width and rebuilds hover tooltips,
 * otherwise translated labels can be clipped or mixed with untranslated type names.</p>
 */
@Mixin(value = NodePortWidget.class, remap = false)
public class LDLibNodePortWidgetI18nMixin {
    /**
     * Replaces a port display name after LDLib resolves the original value.
     *
     * @param cir callback containing the raw port display name and receiving the translated text
     */
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2$translateDisplayName(CallbackInfoReturnable<String> cir) {
        String key = cir.getReturnValue();
        if (key == null || key.isEmpty()) return;
        String altKey = "mbd2.gp." + normalizeKey(key);
        if (I18n.exists(altKey)) {
            cir.setReturnValue(I18n.get(altKey));
        } else if (I18n.exists(key)) {
            cir.setReturnValue(I18n.get(key));
        }
    }

    /**
     * Recomputes the initialized port widget after translation has changed visible text.
     *
     * @param ci mixin callback info
     */
    @Inject(method = "initPortInformation", at = @At("TAIL"), remap = false)
    private void mbd2$recalcWidthAfterTranslation(CallbackInfo ci) {
        NodePortWidget self = (NodePortWidget) (Object) this;
        String name = self.getDisplayName();
        if (name == null) name = "";
        int width = 18 + Minecraft.getInstance().font.width(name);
        self.setSize(width, 15);

        String typeName = self.getPortTypeName();
        String translatedType = translateText(typeName);
        String typeLine = I18n.exists("mbd2.gp.type") ? I18n.get("mbd2.gp.type", translatedType) : ("Type: " + translatedType);
        var tooltips = new ArrayList<String>();
        tooltips.add(typeLine);
        if (self.port.portData.tooltip != null && !self.port.portData.tooltip.isEmpty()) {
            for (String line : self.port.portData.tooltip) {
                tooltips.add(translateTooltipLine(line));
            }
        }
        self.setHoverTooltips(tooltips.toArray(new String[0]));
    }

    /**
     * Resolves plain LDLib UI text through MBD2 graph keys before falling back to direct keys.
     *
     * @param raw raw display text or translation key
     * @return translated text when available, otherwise trimmed raw text
     */
    private static String translateText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        String altKey = "mbd2.gp." + normalizeKey(trimmed);
        if (I18n.exists(altKey)) return I18n.get(altKey);
        if (I18n.exists(trimmed)) return I18n.get(trimmed);
        return trimmed;
    }

    /**
     * Resolves a tooltip line, preferring the dedicated graph tooltip namespace.
     *
     * @param raw raw tooltip text
     * @return translated tooltip text or the normal display translation fallback
     */
    private static String translateTooltipLine(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        String tipsKey = "mbd2.gp.tooltip." + normalizeKey(trimmed);
        if (I18n.exists(tipsKey)) return I18n.get(tipsKey);
        return translateText(trimmed);
    }

    /**
     * Converts arbitrary port text into the normalized key suffix used by MBD2 language files.
     *
     * @param key raw display text
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
