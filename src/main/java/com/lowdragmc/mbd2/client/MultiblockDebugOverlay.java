package com.lowdragmc.mbd2.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores the temporary client-side set of multiblock positions highlighted by
 * the debugger overlay.
 *
 * <p>The business goal is to keep mismatch markers visible for a limited number
 * of client ticks after the player uses the debugger gadget. The position set
 * reference is volatile and the counter is atomic so render and tick reads see
 * coherent snapshots, but callers should still treat this as client-thread UI
 * state rather than a general concurrent collection.</p>
 */
public final class MultiblockDebugOverlay {
    private static volatile Set<BlockPos> POSITIONS = Collections.emptySet();
    private static final AtomicInteger LEFT_TICKS = new AtomicInteger(0);

    private MultiblockDebugOverlay() {
    }

    /**
     * Replaces the current overlay with a snapshot of positions to highlight.
     *
     * <p>Preconditions: intended for logical client/render state. Side effects:
     * copies the provided set, replaces the global overlay positions, and resets
     * the countdown. A {@code null} or empty set, or a non-positive duration,
     * clears any active overlay.</p>
     *
     * @param positions     world positions to highlight; copied before storage
     * @param durationTicks number of client ticks to keep the overlay visible;
     *                      values less than or equal to {@code 0} clear it
     */
    public static void show(Set<BlockPos> positions, int durationTicks) {
        if (positions == null || positions.isEmpty() || durationTicks <= 0) {
            clear();
            return;
        }
        POSITIONS = Collections.unmodifiableSet(new HashSet<>(positions));
        LEFT_TICKS.set(durationTicks);
    }

    /**
     * Clears the active overlay immediately.
     *
     * <p>Side effects: replaces the stored position set with an empty immutable
     * set and resets the countdown to zero.</p>
     */
    public static void clear() {
        POSITIONS = Collections.emptySet();
        LEFT_TICKS.set(0);
    }

    /**
     * Advances the overlay lifetime by one client tick.
     *
     * <p>Preconditions: called from the client tick handler. Side effects:
     * decrements the countdown and clears the overlay when it expires.</p>
     */
    public static void tick() {
        int left = LEFT_TICKS.get();
        if (left <= 0) return;
        if (LEFT_TICKS.decrementAndGet() <= 0) {
            clear();
        }
    }

    /**
     * Returns the current highlighted positions for rendering.
     *
     * <p>Side effects: none. The returned set is immutable as stored by
     * {@link #show(Set, int)}.</p>
     *
     * @return active highlight positions, or {@code null} when no overlay should
     * be rendered
     */
    @Nullable
    public static Set<BlockPos> getPositions() {
        Set<BlockPos> set = POSITIONS;
        return set.isEmpty() ? null : set;
    }
}

