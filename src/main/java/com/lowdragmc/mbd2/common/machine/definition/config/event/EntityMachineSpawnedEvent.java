package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import net.minecraft.world.entity.Entity;

/**
 * Fired when an entity-backed machine is spawned into the level.
 */
@LDLRegister(name = "EntityMachineSpawnedEvent", group = "EntityMachineEvent")
public class EntityMachineSpawnedEvent extends EntityMachineEvent {
    /**
     * Creates an event for a newly spawned entity machine.
     *
     * @param machine entity machine runtime that was attached to the entity
     * @param entity  backing Minecraft entity now present in the level
     */
    public EntityMachineSpawnedEvent(MBDEntityMachine machine, Entity entity) {
        super(machine, entity);
    }
}
