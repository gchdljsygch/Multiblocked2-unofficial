package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

/**
 * Fired at the configured fixed-tick interval for an entity-backed machine.
 */
@LDLRegister(name = "EntityMachineFixedTickEvent", group = "EntityMachineEvent")
public class EntityMachineFixedTickEvent extends EntityMachineEvent {
    /**
     * Tick interval, clamped by the caller to at least {@code 1}.
     */
    @GraphParameterGet
    public final int interval;
    /**
     * Entity machine timer value at the time of the event.
     */
    @GraphParameterGet
    public final long timer;

    /**
     * Creates a fixed-tick event for an entity machine.
     * <p>
     * This event reports scheduling information only; it does not mutate the timer. Handlers can use
     * {@link #every(int)} for coarser intervals without maintaining their own counters.
     *
     * @param machine  entity machine runtime being ticked
     * @param entity   backing Minecraft entity
     * @param interval configured interval that caused this event; expected to be at least {@code 1}
     * @param timer    current entity-machine timer value
     */
    public EntityMachineFixedTickEvent(MBDEntityMachine machine, Entity entity, int interval, long timer) {
        super(machine, entity);
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
