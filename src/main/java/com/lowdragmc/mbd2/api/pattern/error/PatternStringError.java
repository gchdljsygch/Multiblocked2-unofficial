package com.lowdragmc.mbd2.api.pattern.error;

import net.minecraft.network.chat.Component;

/**
 * Pattern error represented by a fixed translation key.
 *
 * <p>Use this for failures whose message does not depend on the failed
 * predicate candidates. The translation key is expected to be resolved on the
 * side rendering the diagnostic.</p>
 */
public class PatternStringError extends PatternError {
    public final String translateKey;

    /**
     * Creates a string-backed pattern error.
     *
     * @param translateKey localization key for the diagnostic message
     */
    public PatternStringError(String translateKey) {
        this.translateKey = translateKey;
    }

    /**
     * Returns the localized diagnostic message.
     *
     * @return translatable component using {@link #translateKey}
     */
    @Override
    public Component getErrorInfo() {
        return Component.translatable(translateKey);
    }
}
