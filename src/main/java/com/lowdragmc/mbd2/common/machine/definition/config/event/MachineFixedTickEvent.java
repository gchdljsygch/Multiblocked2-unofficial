package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

@Getter
@LDLRegister(name = "MachineFixedTickEvent", group = "MachineEvent")
public class MachineFixedTickEvent extends MachineEvent {
    @GraphParameterGet
    public final int interval;
    @GraphParameterGet
    public final long timer;

    public MachineFixedTickEvent(MBDMachine machine, int interval, long timer) {
        super(machine);
        this.interval = interval;
        this.timer = timer;
    }

    public boolean every(int interval) {
        return timer % Math.max(1, interval) == 0;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("interval")).ifPresent(p -> p.setValue(interval));
        Optional.ofNullable(exposedParameters.get("timer")).ifPresent(p -> p.setValue(timer));
    }
}
