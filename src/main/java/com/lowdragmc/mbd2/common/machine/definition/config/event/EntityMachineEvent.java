package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDEntityMachineEventDispatcher;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

public class EntityMachineEvent extends MachineEvent {
    @GraphParameterGet
    public final Entity entity;

    public EntityMachineEvent(MBDEntityMachine machine, Entity entity) {
        super(machine);
        this.entity = entity;
    }

    @Override
    public MBDEntityMachine getMachine() {
        return (MBDEntityMachine) super.getMachine();
    }

    @Override
    public EntityMachineEvent postCustomEvent() {
        getMachine().getDefinition().entityAISettings().postGraphEvent(this);
        postKubeJSEvent();
        return this;
    }

    @Override
    public EntityMachineEvent postKubeJSEvent() {
        if (MBD2.isKubeJSLoaded()) {
            try {
                if (MBDEntityMachineEventDispatcher.postEvent(this).interruptFalse() && isCancelable()) {
                    setCanceled(true);
                }
            } catch (Exception e) {
                MBD2.LOGGER.error("Failed to post KubeJS entity machine event {}", this, e);
            }
        }
        return this;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("entity")).ifPresent(p -> p.setValue(entity));
    }
}
