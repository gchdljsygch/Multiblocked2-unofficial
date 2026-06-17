package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;


/**
 * Fired before a machine state name changes.
 * <p>
 * Canceling this event prevents the state field from changing and skips the
 * resulting block update, lighting update, sound update, and render refresh.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineStateChangedEvent", group = "MachineEvent")
public class MachineStateChangedEvent extends MachineEvent {
    /**
     * Current state name.
     */
    @GraphParameterGet(displayName = "old state")
    public final String oldState;
    /**
     * Requested state name.
     */
    @GraphParameterGet(displayName = "new state")
    public final String newState;

    /**
     * Creates a cancellable event for a requested machine state change.
     * <p>
     * The old and new values are state names, not {@code MachineState} objects. Canceling the event keeps the machine in
     * {@code oldState} and prevents the caller's visual/sound update path for the requested change.
     *
     * @param machine  machine whose state is changing
     * @param oldState current state name
     * @param newState requested state name
     */
    public MachineStateChangedEvent(MBDMachine machine, String oldState, String newState) {
        super(machine);
        this.oldState = oldState;
        this.newState = newState;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("oldState")).ifPresent(p -> p.setValue(oldState));
        Optional.ofNullable(exposedParameters.get("newState")).ifPresent(p -> p.setValue(newState));
    }
}
