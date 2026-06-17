package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.machine.MBDMachine;

/**
 * Fired once per client tick for a client-side machine instance.
 * <p>
 * Use this event only for client visuals or local UI state; authoritative
 * machine state must be changed on the server.
 */
@LDLRegister(name = "MachineClientTickEvent", group = "MachineEvent")
public class MachineClientTickEvent extends MachineEvent {

    /**
     * Creates a client-tick event for a machine.
     * <p>
     * This event is client-side only and should be used for visual or local UI work. Server-authoritative state must not
     * be mutated from this hook.
     *
     * @param machine client-side machine instance being ticked
     */
    public MachineClientTickEvent(MBDMachine machine) {
        super(machine);
    }
}
