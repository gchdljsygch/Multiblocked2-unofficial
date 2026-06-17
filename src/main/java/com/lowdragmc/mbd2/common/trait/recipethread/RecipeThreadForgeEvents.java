package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineRecipeStatusChangedEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineRecipeModifyEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineTickEvent;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge event bridge for {@link RecipeThreadTrait}.
 *
 * <p>The handlers run at lowest priority so regular machine logic has already prepared its tick/search state.
 * They install {@link RecipeThreadContext} for the current logical lane and prune recipe candidates before they are
 * consumed by {@code RecipeLogic}. Runtime exceptions are logged once and suppressed to avoid repeatedly breaking
 * all machine ticks.</p>
 */
@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeThreadForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean tickHandlerFailureLogged;

    private RecipeThreadForgeEvents() {
    }

    /**
     * Prepares recipe-thread assignment and marks the base machine logic as thread {@code 0}.
     *
     * @param event machine tick event fired for an MBD machine
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMachineTick(MachineTickEvent event) {
        MBDMachine machine = event.getMachine();
        RecipeThreadTrait trait = RecipeThreadTrait.get(machine);
        if (trait == null) return;
        try {
            trait.ensureRecipeAssignment();
            RecipeThreadContext.set(machine, 0);
        } catch (RuntimeException e) {
            if (!tickHandlerFailureLogged) {
                tickHandlerFailureLogged = true;
                LOGGER.warn("[mbd2] onMachineTick handler suppressed an upstream RuntimeException; further reports are silenced. Original error: {}", e.toString(), e);
            }
        }
    }

    /**
     * Filters recipe candidates according to the current logical lane.
     *
     * <p>When a recipe is not allowed for the lane currently recorded in {@link RecipeThreadContext}, the event's
     * recipe is set to {@code null} so the normal search path skips it.</p>
     *
     * @param event mutable pre-modification recipe event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRecipeModifyBefore(MachineRecipeModifyEvent.Before event) {
        MBDMachine machine = event.getMachine();
        if (!RecipeThreadContext.isCurrentMachine(machine)) return;
        RecipeThreadTrait trait = RecipeThreadTrait.get(machine);
        if (trait == null) return;
        Integer threadId = RecipeThreadContext.getThreadId();
        if (threadId == null) threadId = 0;
        if (event.getRecipe() == null) return;
        if (!trait.isRecipeAllowedForThread(threadId, event.getRecipe())) {
            event.setRecipe(null);
        }
    }

    /**
     * Marks assignments dirty when the current lane leaves the working state.
     *
     * @param event recipe status transition event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRecipeStatusChanged(MachineRecipeStatusChangedEvent event) {
        MBDMachine machine = event.getMachine();
        if (!RecipeThreadContext.isCurrentMachine(machine)) return;
        RecipeThreadTrait trait = RecipeThreadTrait.get(machine);
        if (trait == null) return;
        if (event.getOldStatus() == com.lowdragmc.mbd2.api.recipe.RecipeLogic.Status.WORKING) {
            trait.markRecipeAssignmentDirty();
            trait.ensureRecipeAssignment();
        }
    }
}
