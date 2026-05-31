package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@LDLRegister(name = "MachineStructureInvalidEvent", group = "MachineEvent.Multiblock")
public class MachineStructureInvalidEvent extends MachineEvent {
    @GraphParameterGet
    public final List<IMultiPart> parts;

    public MachineStructureInvalidEvent(MBDMachine machine, List<IMultiPart> parts) {
        super(machine);
        this.parts = parts;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("parts")).ifPresent(p -> p.setValue(parts));
    }
}
