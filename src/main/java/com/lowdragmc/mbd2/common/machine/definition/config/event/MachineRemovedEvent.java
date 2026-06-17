package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

/**
 * Fired when a machine is removed from the world lifecycle.
 * <p>
 * Trait removal callbacks have already run before this event is posted by the
 * default machine implementation.
 */
@Getter
@LDLRegister(name = "MachineRemovedEvent", group = "MachineEvent")
public class MachineRemovedEvent extends MachineEvent {

    /**
     * Creates an event for machine removal.
     * <p>
     * Posted after default trait removal callbacks have run. Handlers should treat the machine as leaving the world and
     * avoid scheduling new persistent work against its holder.
     *
     * @param machine machine being removed
     */
    public MachineRemovedEvent(MBDMachine machine) {
        super(machine);
    }
}
