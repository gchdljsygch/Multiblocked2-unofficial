package com.non_coffee.mbd2thread.mixin.client;

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

@Mixin(value = NodePortWidget.class, remap = false)
public class LDLibNodePortWidgetI18nMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2thread$translateDisplayName(CallbackInfoReturnable<String> cir) {
        String key = cir.getReturnValue();
        if (key == null || key.isEmpty()) return;
        String altKey = "mbd2thread.gp." + normalizeKey(key);
        if (I18n.exists(altKey)) {
            cir.setReturnValue(I18n.get(altKey));
        } else if (I18n.exists(key)) {
            cir.setReturnValue(I18n.get(key));
        }
    }

    @Inject(method = "initPortInformation", at = @At("TAIL"), remap = false)
    private void mbd2thread$recalcWidthAfterTranslation(CallbackInfo ci) {
        NodePortWidget self = (NodePortWidget) (Object) this;
        String name = self.getDisplayName();
        if (name == null) name = "";
        int width = 18 + Minecraft.getInstance().font.width(name);
        self.setSize(width, 15);

        String typeName = self.getPortTypeName();
        String translatedType = translateText(typeName);
        String typeLine = I18n.exists("mbd2thread.gp.type") ? I18n.get("mbd2thread.gp.type", translatedType) : ("Type: " + translatedType);
        var tooltips = new ArrayList<String>();
        tooltips.add(typeLine);
        if (self.port.portData.tooltip != null && !self.port.portData.tooltip.isEmpty()) {
            for (String line : self.port.portData.tooltip) {
                tooltips.add(translateTooltipLine(line));
            }
        }
        self.setHoverTooltips(tooltips.toArray(new String[0]));
    }

    private static String translateText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        String altKey = "mbd2thread.gp." + normalizeKey(trimmed);
        if (I18n.exists(altKey)) return I18n.get(altKey);
        if (I18n.exists(trimmed)) return I18n.get(trimmed);
        return trimmed;
    }

    private static String translateTooltipLine(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        String tipsKey = "mbd2thread.gp.tooltip." + normalizeKey(trimmed);
        if (I18n.exists(tipsKey)) return I18n.get(tipsKey);
        return translateText(trimmed);
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
