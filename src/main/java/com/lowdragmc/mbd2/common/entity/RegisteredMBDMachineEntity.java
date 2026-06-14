package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class RegisteredMBDMachineEntity extends MBDMachineEntity {
    public RegisteredMBDMachineEntity(EntityType<?> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level, definition);
    }
}
