package com.lowdragmc.mbd2.api.recipe.content;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Numeric transform applied to size-like recipe content during parallelization
 * or recipe modification.
 *
 * <p>The business goal is to scale recipe amounts using a simple
 * {@code value * multiplier + addition} formula while keeping the transform
 * editable in the machine editor. Instances are mutable for configurator use and
 * should not be shared across threads while edited.</p>
 */
@Getter
@Setter
public class ContentModifier implements IConfigurable {
    public static final ContentModifier IDENTITY = ContentModifier.identity();

    @Configurable(name = "content_modifier.multiplier", tips = "content_modifier.multiplier.tips")
    @NumberRange(range = {0, Double.MAX_VALUE}, wheel = 1f)
    private double multiplier;
    @Configurable(name = "content_modifier.addition", tips = "content_modifier.addition.tips")
    @NumberRange(range = {0, Double.MAX_VALUE}, wheel = 1f)
    private double addition;

    /**
     * Returns whether this modifier leaves values unchanged.
     *
     * @return {@code true} when multiplier is {@code 1} and addition is
     * {@code 0}
     */
    public boolean isIdentity() {
        return multiplier == 1 && addition == 0;
    }

    /**
     * Creates a modifier from both formula terms.
     *
     * @param multiplier multiplicative term; editor range is non-negative
     * @param addition   additive term; editor range is non-negative
     * @return new modifier
     */
    public static ContentModifier of(double multiplier, double addition) {
        return new ContentModifier(multiplier, addition);
    }

    /**
     * Creates a modifier that only multiplies values.
     *
     * @param multiplier multiplicative term
     * @return new modifier with zero addition
     */
    public static ContentModifier multiplier(double multiplier) {
        return new ContentModifier(multiplier, 0);
    }

    /**
     * Creates a modifier that only adds to values.
     *
     * @param addition additive term
     * @return new modifier with multiplier {@code 1}
     */
    public static ContentModifier addition(double addition) {
        return new ContentModifier(1, addition);
    }

    /**
     * Creates a modifier that leaves values unchanged.
     *
     * @return new identity modifier
     */
    public static ContentModifier identity() {
        return new ContentModifier(1, 0);
    }

    /**
     * Creates a numeric modifier.
     *
     * @param multiplier multiplicative term in {@code value * multiplier + addition}
     * @param addition   additive term in {@code value * multiplier + addition}
     */
    public ContentModifier(double multiplier, double addition) {
        this.multiplier = multiplier;
        this.addition = addition;
    }

    /**
     * Applies this modifier to a number.
     *
     * <p>Big decimal values preserve decimal precision through
     * {@link BigDecimal}; big integers truncate modifier terms to {@code long}
     * before applying them. Other number types are returned as {@link Double}
     * results.</p>
     *
     * @param number source number
     * @return transformed number
     */
    public Number apply(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal.multiply(BigDecimal.valueOf(multiplier)).add(BigDecimal.valueOf(addition));
        }
        if (number instanceof BigInteger bigInteger) {
            return bigInteger.multiply(BigInteger.valueOf((long) multiplier)).add(BigInteger.valueOf((long) addition));
        }
        return number.doubleValue() * multiplier + addition;
    }

    /**
     * Combines this modifier with another modifier.
     *
     * <p>The current semantics multiply multipliers and add additions, matching
     * the existing recipe parallel behavior.</p>
     *
     * @param modifier modifier to merge with this one
     * @return combined modifier
     */
    public ContentModifier merge(ContentModifier modifier) {
        return new ContentModifier(multiplier * modifier.multiplier, addition + modifier.addition);
    }

}
