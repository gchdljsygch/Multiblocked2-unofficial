package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.mbd2.common.machine.MBDMachine;

final class RecipeThreadContext {
    private static final ThreadLocal<MBDMachine> MACHINE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> THREAD_ID = new ThreadLocal<>();

    private RecipeThreadContext() {
    }

    static void set(MBDMachine machine, int threadId) {
        MACHINE.set(machine);
        THREAD_ID.set(threadId);
    }

    static Integer getThreadId() {
        return THREAD_ID.get();
    }

    static boolean isCurrentMachine(MBDMachine machine) {
        return machine != null && machine == MACHINE.get();
    }

    static void clearIfMachine(MBDMachine machine) {
        if (isCurrentMachine(machine)) {
            MACHINE.remove();
            THREAD_ID.remove();
        }
    }

    static void runWith(MBDMachine machine, int threadId, Runnable runnable) {
        set(machine, threadId);
        try {
            runnable.run();
        } finally {
            clearIfMachine(machine);
        }
    }
}

