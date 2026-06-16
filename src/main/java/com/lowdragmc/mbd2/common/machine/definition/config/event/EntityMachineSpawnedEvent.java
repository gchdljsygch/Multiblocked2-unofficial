package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import net.minecraft.world.entity.Entity;

@LDLRegister(name = "EntityMachineSpawnedEvent", group = "EntityMachineEvent")
public class EntityMachineSpawnedEvent extends EntityMachineEvent {
    public EntityMachineSpawnedEvent(MBDEntityMachine machine, Entity entity) {
        super(machine, entity);
    }
}
