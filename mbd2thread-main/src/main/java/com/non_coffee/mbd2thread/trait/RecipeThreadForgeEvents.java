package com.non_coffee.mbd2thread.trait;

import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineRecipeStatusChangedEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineRecipeModifyEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineTickEvent;
import com.mojang.logging.LogUtils;
import com.non_coffee.mbd2thread.Mbd2Thread;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Mbd2Thread.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeThreadForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean tickHandlerFailureLogged;

    private RecipeThreadForgeEvents() {
    }

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
                LOGGER.warn("[mbd2thread] onMachineTick handler suppressed an upstream RuntimeException; further reports are silenced. Original error: {}", e.toString(), e);
            }
        }
    }

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
