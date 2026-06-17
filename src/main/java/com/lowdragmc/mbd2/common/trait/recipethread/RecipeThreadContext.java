package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.mbd2.common.machine.MBDMachine;

/**
 * Thread-local marker for the recipe logic currently ticking a machine.
 *
 * <p>The name refers to logical recipe lanes, not Java worker threads. The mod still executes recipe logic on the
 * normal server tick thread; this context lets event hooks distinguish the base machine logic ({@code 0}) from
 * extra {@link ThreadedRecipeLogic} lanes while callbacks are nested inside a tick.</p>
 */
final class RecipeThreadContext {
    private static final ThreadLocal<MBDMachine> MACHINE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> THREAD_ID = new ThreadLocal<>();

    private RecipeThreadContext() {
    }

    /**
     * Marks the current call stack as executing one recipe lane for a machine.
     *
     * @param machine  machine whose recipe logic is being processed
     * @param threadId logical lane id; {@code 0} is the machine's original {@code RecipeLogic}
     */
    static void set(MBDMachine machine, int threadId) {
        MACHINE.set(machine);
        THREAD_ID.set(threadId);
    }

    /**
     * Returns the current logical recipe lane id.
     *
     * @return current lane id, or {@code null} when no recipe-thread context is active
     */
    static Integer getThreadId() {
        return THREAD_ID.get();
    }

    /**
     * Tests whether the active context belongs to the supplied machine instance.
     *
     * @param machine machine to compare by identity
     * @return {@code true} only when {@code machine} is non-null and is the current context machine
     */
    static boolean isCurrentMachine(MBDMachine machine) {
        return machine != null && machine == MACHINE.get();
    }

    /**
     * Clears the context if it still belongs to the supplied machine.
     *
     * <p>The identity check prevents nested or unrelated recipe ticks from being cleared accidentally.</p>
     *
     * @param machine machine whose context should be removed
     */
    static void clearIfMachine(MBDMachine machine) {
        if (isCurrentMachine(machine)) {
            MACHINE.remove();
            THREAD_ID.remove();
        }
    }

    /**
     * Runs an action with a recipe-thread context and clears it afterward.
     *
     * @param machine  machine whose recipe logic is being processed
     * @param threadId logical lane id; {@code 0} is the base machine logic
     * @param runnable action to execute; exceptions are propagated after cleanup
     */
    static void runWith(MBDMachine machine, int threadId, Runnable runnable) {
        set(machine, threadId);
        try {
            runnable.run();
        } finally {
            clearIfMachine(machine);
        }
    }
}

