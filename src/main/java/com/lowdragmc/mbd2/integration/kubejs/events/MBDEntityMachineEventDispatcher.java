package com.lowdragmc.mbd2.integration.kubejs.events;

import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.Extra;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public interface MBDEntityMachineEventDispatcher {
    EventHandler SPAWNED = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server("spawned",
            () -> MBDEntityMachineEvents.EntityMachineSpawnedEventJS.class).extra(Extra.ID);
    EventHandler REMOVED = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server("removed",
            () -> MBDEntityMachineEvents.EntityMachineRemovedEventJS.class).extra(Extra.ID);
    EventHandler INTERACT = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server("interact",
            () -> MBDEntityMachineEvents.EntityMachineInteractEventJS.class).extra(Extra.ID);

    static void init() {
        // NO-OP
    }

    static void postSpawned(MBDEntityMachine machine, Entity entity) {
        var machineId = machineId(machine);
        SPAWNED.post(new MBDEntityMachineEvents.EntityMachineSpawnedEventJS(machine, entity), machineId);
    }

    static void postRemoved(MBDEntityMachine machine, Entity entity, Entity.RemovalReason reason) {
        var machineId = machineId(machine);
        REMOVED.post(new MBDEntityMachineEvents.EntityMachineRemovedEventJS(machine, entity, reason), machineId);
    }

    static void postInteract(MBDEntityMachine machine, Entity entity, Player player, InteractionHand hand) {
        var machineId = machineId(machine);
        INTERACT.post(new MBDEntityMachineEvents.EntityMachineInteractEventJS(machine, entity, player, hand), machineId);
    }

    static ResourceLocation machineId(MBDEntityMachine machine) {
        return machine.getDefinition().id();
    }
}
