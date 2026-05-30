package com.lowdragmc.mbd2.common.capability.recipe;

import net.minecraft.util.Mth;

public record RedstoneSignal(int strength, int duration) {

    public RedstoneSignal {
        strength = Mth.clamp(strength, 0, 15);
        duration = Math.max(0, duration);
    }

    public static RedstoneSignal input(int strength) {
        return new RedstoneSignal(strength, 0);
    }

    public static RedstoneSignal output(int strength, int duration) {
        return new RedstoneSignal(strength, duration);
    }

    public RedstoneSignal withStrength(int strength) {
        return new RedstoneSignal(strength, duration);
    }

    public RedstoneSignal withDuration(int duration) {
        return new RedstoneSignal(strength, duration);
    }
}
