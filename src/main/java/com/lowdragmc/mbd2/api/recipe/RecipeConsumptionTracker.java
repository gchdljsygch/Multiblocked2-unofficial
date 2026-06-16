package com.lowdragmc.mbd2.api.recipe;

import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local recorder for capability content consumed during recipe handling.
 *
 * <p>The business goal is to let item, fluid, energy, and integration traits
 * report the concrete resources they consumed without threading a collector
 * through every capability API. Recording is scoped to the current thread and
 * supports nesting by restoring the previous recorder when a recorder is
 * closed. Callers should use try-with-resources around {@link #start()} to
 * avoid leaking the active recorder into later recipe work on the same thread.</p>
 */
public final class RecipeConsumptionTracker {
    private static final ThreadLocal<Recorder> CURRENT = new ThreadLocal<>();

    private RecipeConsumptionTracker() {
    }

    /**
     * Indicates whether the current thread is inside a recording scope.
     *
     * @return {@code true} when {@link #record(RecipeCapability, Object, String)}
     * would append to a recorder on this thread
     */
    public static boolean isRecording() {
        return CURRENT.get() != null;
    }

    /**
     * Records one consumed content object in the current thread's active scope.
     *
     * <p>Preconditions: {@code capability} should describe the type of
     * {@code consumed}. A {@code null} consumed object is ignored. Side effects:
     * appends to the active recorder when one exists; otherwise this is a no-op.</p>
     *
     * @param capability recipe capability that performed the consumption
     * @param consumed   concrete consumed content snapshot
     * @param slotName   optional trait slot name that consumed the content
     */
    public static void record(RecipeCapability<?> capability, Object consumed, @Nullable String slotName) {
        var recorder = CURRENT.get();
        if (recorder != null && consumed != null) {
            recorder.record(capability, consumed, slotName);
        }
    }

    /**
     * Starts a recording scope on the current thread.
     *
     * <p>Side effects: installs a new current recorder and retains any previous
     * recorder so nested scopes can restore it on close.</p>
     *
     * @return recorder that must be closed to restore the previous thread-local
     * state
     */
    public static Recorder start() {
        var previous = CURRENT.get();
        var recorder = new Recorder(previous);
        CURRENT.set(recorder);
        return recorder;
    }

    /**
     * Active recording scope returned by {@link RecipeConsumptionTracker#start()}.
     *
     * <p>Instances are not thread-safe and should only be used on the thread
     * that created them.</p>
     */
    public static final class Recorder implements AutoCloseable {
        private final Recorder previous;
        private final List<RecipeConsumption.Entry> entries = new ArrayList<>();
        private boolean closed;

        private Recorder(@Nullable Recorder previous) {
            this.previous = previous;
        }

        private void record(RecipeCapability<?> capability, Object consumed, @Nullable String slotName) {
            entries.add(new RecipeConsumption.Entry(capability, consumed, slotName));
        }

        /**
         * Captures the entries recorded so far.
         *
         * <p>Side effects: none; later records do not mutate the returned
         * {@link RecipeConsumption} because it copies its entries.</p>
         *
         * @return immutable consumption snapshot, or {@link RecipeConsumption#EMPTY}
         * when nothing has been recorded
         */
        public RecipeConsumption snapshot() {
            return entries.isEmpty() ? RecipeConsumption.EMPTY : new RecipeConsumption(entries);
        }

        /**
         * Ends this recording scope and restores the previous recorder.
         *
         * <p>Side effects: updates the thread-local recorder once. Repeated calls
         * are ignored.</p>
         */
        @Override
        public void close() {
            if (!closed) {
                CURRENT.set(previous);
                closed = true;
            }
        }
    }
}
