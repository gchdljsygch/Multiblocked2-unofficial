package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired at the start of an entity machine tick.
 * <p>
 * Canceling this event skips the entity machine's tick work for that tick.
 */
@Cancelable
@LDLRegister(name = "EntityMachineTickEvent", group = "EntityMachineEvent")
public class EntityMachineTickEvent extends EntityMachineEvent {
    /**
     * Creates an event for one entity-machine tick.
     * <p>
     * Canceling the event skips the machine tick work for this tick only.
     *
     * @param machine entity machine runtime being ticked
     * @param entity  backing Minecraft entity
     */
    public EntityMachineTickEvent(MBDEntityMachine machine, Entity entity) {
        super(machine, entity);
    }
}
