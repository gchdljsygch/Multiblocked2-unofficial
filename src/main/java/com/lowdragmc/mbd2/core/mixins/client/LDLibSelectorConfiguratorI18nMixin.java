package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.editor.configurator.SelectorConfigurator;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Localizes LDLib selector candidates while preserving their backing objects.
 *
 * <p>SelectorConfigurator stores both a display mapping and a reverse name-to-value map. The
 * mixin wraps the display mapping at construction time and rebuilds {@code nameMap} with the
 * translated labels so user selections continue to resolve to the original candidate objects.</p>
 */
@Mixin(value = SelectorConfigurator.class, remap = false)
public class LDLibSelectorConfiguratorI18nMixin {
    @Shadow
    protected List<Object> candidates;
    @Shadow
    protected Function<Object, String> mapping;
    @Shadow
    protected Map<String, Object> nameMap;

    /**
     * Wraps the selector display mapper and rebuilds reverse lookup entries.
     *
     * <p>If two candidates translate to the same visible label, the raw label is appended to keep
     * the reverse map unambiguous.</p>
     *
     * @param name         configurator field name
     * @param supplier     value supplier passed to LDLib
     * @param onUpdate     update callback passed to LDLib
     * @param defaultValue default candidate value
     * @param forceUpdate  whether LDLib forces callback updates
     * @param candidates   configured candidate list
     * @param mapping      original display mapping
     * @param ci           mixin callback info
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void mbd2$wrapMappingForI18n(String name, java.util.function.Supplier<?> supplier, java.util.function.Consumer<?> onUpdate, Object defaultValue, boolean forceUpdate, List<?> candidates, Function<?, String> mapping, CallbackInfo ci) {
        Function<Object, String> original = this.mapping;
        if (original == null) return;
        this.mapping = o -> translateText(original.apply(o));
        Map<String, Object> rebuilt = new HashMap<>();
        for (Object c : this.candidates) {
            String raw = original.apply(c);
            String display = translateText(raw);
            if (rebuilt.containsKey(display)) {
                display = display + " (" + raw + ")";
            }
            rebuilt.put(display, c);
        }
        this.nameMap = rebuilt;
    }

    /**
     * Resolves selector display text through MBD2 graph keys and direct translation keys.
     *
     * @param raw original display text
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
     * Converts selector labels to graph translation-key suffixes.
     *
     * @param key raw selector label
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
