package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

/**
 * Fired after a block-backed machine is loaded into a valid chunk.
 * <p>
 * The event is scheduled from the server after the machine and its traits have
 * received their normal load callbacks.
 */
@Getter
@LDLRegister(name = "MachineOnLoadEvent", group = "MachineEvent")
public class MachineOnLoadEvent extends MachineEvent {

    /**
     * Creates an event for a machine load callback.
     * <p>
     * Posted after the machine and traits have completed their default load handling. Handlers should run on the logical
     * server thread that owns the machine.
     *
     * @param machine machine that has loaded into a valid chunk
     */
    public MachineOnLoadEvent(MBDMachine machine) {
        super(machine);
    }

}
