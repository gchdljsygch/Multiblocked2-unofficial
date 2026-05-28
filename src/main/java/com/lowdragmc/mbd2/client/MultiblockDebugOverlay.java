package com.lowdragmc.mbd2.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiblockDebugOverlay {
    private static volatile Set<BlockPos> POSITIONS = Collections.emptySet();
    private static final AtomicInteger LEFT_TICKS = new AtomicInteger(0);

    private MultiblockDebugOverlay() {
    }

    public static void show(Set<BlockPos> positions, int durationTicks) {
        if (positions == null || positions.isEmpty() || durationTicks <= 0) {
            clear();
            return;
        }
        POSITIONS = Collections.unmodifiableSet(new HashSet<>(positions));
        LEFT_TICKS.set(durationTicks);
    }

    public static void clear() {
        POSITIONS = Collections.emptySet();
        LEFT_TICKS.set(0);
    }

    public static void tick() {
        int left = LEFT_TICKS.get();
        if (left <= 0) return;
        if (LEFT_TICKS.decrementAndGet() <= 0) {
            clear();
        }
    }

    @Nullable
    public static Set<BlockPos> getPositions() {
        Set<BlockPos> set = POSITIONS;
        return set.isEmpty() ? null : set;
    }
}

