package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.machine.MBDMachine;

/**
 * Fired after a multiblock controller successfully forms or refreshes its
 * structure.
 * <p>
 * Part membership, proxy blocks, capability routing, and controller state have
 * already been updated when this event is posted.
 */
@LDLRegister(name = "MachineStructureFormedEvent", group = "MachineEvent.Multiblock")
public class MachineStructureFormedEvent extends MachineEvent {

    /**
     * Creates an event for a successfully formed or refreshed multiblock structure.
     * <p>
     * Posted after part membership, proxy blocks, capability routing, and controller state have been updated.
     *
     * @param machine controller machine whose structure formed or refreshed
     */
    public MachineStructureFormedEvent(MBDMachine machine) {
        super(machine);
    }
}
