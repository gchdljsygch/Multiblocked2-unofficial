package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
@LDLRegister(name = "EntityMachineTickEvent", group = "EntityMachineEvent")
public class EntityMachineTickEvent extends EntityMachineEvent {
    public EntityMachineTickEvent(MBDEntityMachine machine, Entity entity) {
        super(machine, entity);
    }
}
