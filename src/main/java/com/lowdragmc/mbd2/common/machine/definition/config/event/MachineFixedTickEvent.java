package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

/**
 * Fired at the configured fixed-tick interval during a machine server tick.
 * <p>
 * {@link #interval} is the configured interval that caused this event, while
 * {@link #timer} is the machine offset timer used for modulo checks.
 */
@Getter
@LDLRegister(name = "MachineFixedTickEvent", group = "MachineEvent")
public class MachineFixedTickEvent extends MachineEvent {
    /**
     * Tick interval, clamped by the caller to at least {@code 1}.
     */
    @GraphParameterGet
    public final int interval;
    /**
     * Machine offset timer value at the time of the event.
     */
    @GraphParameterGet
    public final long timer;

    /**
     * Creates a fixed-tick event for a block-backed machine.
     * <p>
     * This event reports scheduling information only; it does not mutate the timer. Handlers can use
     * {@link #every(int)} for coarser intervals without maintaining their own counters.
     *
     * @param machine  machine runtime being ticked
     * @param interval configured interval that caused this event; expected to be at least {@code 1}
     * @param timer    current machine offset timer value
     */
    public MachineFixedTickEvent(MBDMachine machine, int interval, long timer) {
        super(machine);
        this.interval = interval;
        this.timer = timer;
    }

    /**
     * Tests whether this event's timer is divisible by another interval.
     *
     * @param interval requested interval; values below {@code 1} are treated as
     *                 {@code 1}
     * @return {@code true} when {@link #timer} is on that interval
     */
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
