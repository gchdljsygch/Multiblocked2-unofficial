package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired at the start of a server-side machine tick.
 * <p>
 * Canceling this event skips fixed-tick events, recipe logic, and trait server
 * ticks for that tick.
 */
@Cancelable
@LDLRegister(name = "MachineTickEvent", group = "MachineEvent")
public class MachineTickEvent extends MachineEvent {

    /**
     * Creates a server tick event for a machine.
     * <p>
     * Canceling this event skips the caller's fixed-tick dispatch, recipe logic tick, and trait server ticks for this
     * machine tick.
     *
     * @param machine server-side machine instance being ticked
     */
    public MachineTickEvent(MBDMachine machine) {
        super(machine);
    }
}
