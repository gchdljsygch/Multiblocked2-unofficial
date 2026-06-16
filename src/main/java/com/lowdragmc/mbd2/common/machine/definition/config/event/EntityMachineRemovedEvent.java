package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

@Getter
@LDLRegister(name = "EntityMachineRemovedEvent", group = "EntityMachineEvent")
public class EntityMachineRemovedEvent extends EntityMachineEvent {
    @GraphParameterGet(type = String.class)
    public final Entity.RemovalReason reason;

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
