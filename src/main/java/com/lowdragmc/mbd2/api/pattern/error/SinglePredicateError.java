package com.lowdragmc.mbd2.api.pattern.error;

import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Pattern error for one limited simple predicate.
 *
 * <p>The business goal is to report which min/max global or layer constraint
 * failed without showing unrelated alternatives from the same
 * {@link com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate}. The numeric
 * type matches the existing translation suffixes: {@code 0} max global,
 * {@code 1} min global, {@code 2} max layer, {@code 3} min layer.</p>
 */
public class SinglePredicateError extends PatternError {
    public final SimplePredicate predicate;
    public final int type;

    /**
     * Creates an error for a failed limited predicate.
     *
     * @param predicate predicate whose count constraint failed
     * @param type      constraint type; {@code 0..3} maps to the translation
     *                  suffix and count field
     */
    public SinglePredicateError(SimplePredicate predicate, int type) {
        this.predicate = predicate;
        this.type = type;
    }

    /**
     * Returns candidates from only the failed predicate.
     *
     * @return singleton candidate group for focused diagnostics
     */
    @Override
    public List<List<ItemStack>> getCandidates() {
        return Collections.singletonList(predicate.getCandidates());
    }

    /**
     * Builds the localized count-limit diagnostic.
     *
     * @return translatable component using the limit type and expected count
     */
    @Override
    public Component getErrorInfo() {
        int number = -1;
        if (type == 0) {
            number = predicate.maxCount;
        }
        if (type == 1) {
            number = predicate.minCount;
        }
        if (type == 2) {
            number = predicate.maxLayerCount;
        }
        if (type == 3) {
            number = predicate.minLayerCount;
        }
        return Component.translatable("mbd2.multiblock.pattern.error.limited." + type, number);
    }
}
