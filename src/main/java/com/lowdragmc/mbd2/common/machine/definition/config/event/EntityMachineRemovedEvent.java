package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

/**
 * Fired when an entity-backed machine is removed.
 */
@Getter
@LDLRegister(name = "EntityMachineRemovedEvent", group = "EntityMachineEvent")
public class EntityMachineRemovedEvent extends EntityMachineEvent {
    /**
     * Minecraft removal reason. Graph handlers receive it as a string.
     */
    @GraphParameterGet(type = String.class)
    public final Entity.RemovalReason reason;

    /**
     * Creates an event for entity-machine removal.
     * <p>
     * The removal reason is exposed to graph handlers as a string because graph parameters do not carry the enum type.
     *
     * @param machine entity machine runtime being removed
     * @param entity  backing Minecraft entity
     * @param reason  vanilla removal reason supplied by the entity lifecycle
     */
    public EntityMachineRemovedEvent(MBDEntityMachine machine, Entity entity, Entity.RemovalReason reason) {
        super(machine, entity);
        this.reason = reason;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("reason")).ifPresent(p -> p.setValue(reason.toString()));
    }
}
