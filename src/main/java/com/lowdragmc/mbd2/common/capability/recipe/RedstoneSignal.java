package com.lowdragmc.mbd2.common.capability.recipe;

import net.minecraft.util.Mth;

public record RedstoneSignal(int strength, int maxStrength, int duration) {

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

    public RedstoneSignal(int strength, int duration) {
        this(strength, strength + 1, duration);
    }

    public static RedstoneSignal input(int strength) {
        return new RedstoneSignal(strength, strength + 1, 0);
    }

    public static RedstoneSignal input(int minStrength, int maxStrength) {
        return new RedstoneSignal(minStrength, maxStrength, 0);
    }

    public static RedstoneSignal output(int strength, int duration) {
        return new RedstoneSignal(strength, strength + 1, duration);
    }

    public RedstoneSignal withStrength(int strength) {
        if (isOutput() || isExactInput()) {
            return new RedstoneSignal(strength, strength + 1, duration);
        }
        int width = maxStrength - this.strength;
        return input(strength, strength + width);
    }

    public RedstoneSignal withMaxStrength(int maxStrength) {
        return new RedstoneSignal(strength, maxStrength, duration);
    }

    public RedstoneSignal withDuration(int duration) {
        return new RedstoneSignal(strength, maxStrength, duration);
    }

    public boolean isOutput() {
        return duration > 0;
    }

    public boolean isExactInput() {
        return !isOutput() && maxStrength == strength + 1;
    }

    public boolean matchesInput(int signal) {
        return signal >= strength && signal < maxStrength;
    }

    public String inputDisplay() {
        return isExactInput() ? "=%d".formatted(strength) : "[%d,%d)".formatted(strength, maxStrength);
    }
}
