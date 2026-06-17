package com.lowdragmc.mbd2.common.capability.recipe;

import net.minecraft.util.Mth;

/**
 * Recipe content value for redstone signal requirements and outputs.
 *
 * <p>For input requirements, {@link #duration()} is {@code 0} and the valid signal range is the half-open interval
 * {@code [strength, maxStrength)}. For outputs, {@link #duration()} is positive and {@link #strength()} is the emitted
 * vanilla redstone power for that many ticks. Values are normalized on construction: strength is clamped to
 * {@code 0..15}, max strength to {@code 0..16}, and duration to at least {@code 0}.</p>
 *
 * @param strength    minimum input strength or output strength, normalized to {@code 0..15}
 * @param maxStrength exclusive upper input bound, normalized to be greater than {@code strength}; ignored by outputs
 * @param duration    output pulse duration in ticks; {@code 0} means this value is an input predicate
 */
public record RedstoneSignal(int strength, int maxStrength, int duration) {

    /**
     * Normalizes strength/range/duration so every instance has a usable input interval or output pulse.
     */
    public RedstoneSignal {
        strength = Mth.clamp(strength, 0, 15);
        duration = Math.max(0, duration);
        if (duration > 0) {
            maxStrength = Math.min(16, strength + 1);
        } else {
            maxStrength = Mth.clamp(maxStrength, 0, 16);
            if (maxStrength <= strength) {
                maxStrength = Math.min(16, strength + 1);
            }
        }
    }

    /**
     * Creates an exact input when duration is zero, or an output pulse when duration is positive.
     *
     * @param strength exact input/output strength before normalization
     * @param duration output pulse duration in ticks; {@code 0} for exact input
     */
    public RedstoneSignal(int strength, int duration) {
        this(strength, strength + 1, duration);
    }

    /**
     * Creates an exact input requirement.
     *
     * @param strength required vanilla redstone strength, clamped to {@code 0..15}
     * @return input predicate that matches exactly that strength
     */
    public static RedstoneSignal input(int strength) {
        return new RedstoneSignal(strength, strength + 1, 0);
    }

    /**
     * Creates a ranged input requirement.
     *
     * @param minStrength inclusive lower bound
     * @param maxStrength exclusive upper bound
     * @return input predicate matching {@code [minStrength, maxStrength)} after normalization
     */
    public static RedstoneSignal input(int minStrength, int maxStrength) {
        return new RedstoneSignal(minStrength, maxStrength, 0);
    }

    /**
     * Creates an output pulse.
     *
     * @param strength emitted vanilla redstone strength, clamped to {@code 0..15}
     * @param duration pulse duration in ticks, clamped to at least {@code 0}
     * @return output signal content
     */
    public static RedstoneSignal output(int strength, int duration) {
        return new RedstoneSignal(strength, strength + 1, duration);
    }

    /**
     * Returns a copy with a different strength while preserving input range width when applicable.
     *
     * @param strength new minimum/exact/output strength
     * @return normalized signal copy
     */
    public RedstoneSignal withStrength(int strength) {
        if (isOutput() || isExactInput()) {
            return new RedstoneSignal(strength, strength + 1, duration);
        }
        int width = maxStrength - this.strength;
        return input(strength, strength + width);
    }

    /**
     * Returns a copy with a different exclusive input upper bound.
     *
     * @param maxStrength new exclusive upper input bound
     * @return normalized signal copy
     */
    public RedstoneSignal withMaxStrength(int maxStrength) {
        return new RedstoneSignal(strength, maxStrength, duration);
    }

    /**
     * Returns a copy with a different output duration.
     *
     * @param duration new duration in ticks; values {@code <= 0} turn the value into an input predicate
     * @return normalized signal copy
     */
    public RedstoneSignal withDuration(int duration) {
        return new RedstoneSignal(strength, maxStrength, duration);
    }

    /**
     * Reports whether this value represents an output pulse.
     *
     * @return {@code true} when duration is positive
     */
    public boolean isOutput() {
        return duration > 0;
    }

    /**
     * Reports whether this input predicate matches exactly one strength.
     *
     * @return {@code true} when this is an input and {@code maxStrength == strength + 1}
     */
    public boolean isExactInput() {
        return !isOutput() && maxStrength == strength + 1;
    }

    /**
     * Tests an observed redstone input against this input range.
     *
     * @param signal observed vanilla redstone strength, normally {@code 0..15}
     * @return {@code true} when {@code signal} is in {@code [strength, maxStrength)}
     */
    public boolean matchesInput(int signal) {
        return signal >= strength && signal < maxStrength;
    }

    /**
     * Formats the input predicate for UI and error messages.
     *
     * @return exact display such as {@code =15}, or ranged display such as {@code [3,8)}
     */
    public String inputDisplay() {
        return isExactInput() ? "=%d".formatted(strength) : "[%d,%d)".formatted(strength, maxStrength);
    }
}
