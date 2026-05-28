package com.lowdragmc.mbd2.api.recipe;

import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RecipeConsumptionTracker {
    private static final ThreadLocal<Recorder> CURRENT = new ThreadLocal<>();

    private RecipeConsumptionTracker() {
    }

    public static boolean isRecording() {
        return CURRENT.get() != null;
    }

    public static void record(RecipeCapability<?> capability, Object consumed, @Nullable String slotName) {
        var recorder = CURRENT.get();
        if (recorder != null && consumed != null) {
            recorder.record(capability, consumed, slotName);
        }
    }

    public static Recorder start() {
        var previous = CURRENT.get();
        var recorder = new Recorder(previous);
        CURRENT.set(recorder);
        return recorder;
    }

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

        public RecipeConsumption snapshot() {
            return entries.isEmpty() ? RecipeConsumption.EMPTY : new RecipeConsumption(entries);
        }

        @Override
        public void close() {
            if (!closed) {
                CURRENT.set(previous);
                closed = true;
            }
        }
    }
}
