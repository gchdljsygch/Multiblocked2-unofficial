package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@LDLRegister(name = "MachineUIEvent", group = "MachineEvent")
public class MachineUIEvent extends MachineEvent {
    public WidgetGroup root;

    public MachineUIEvent(MBDMachine machine, WidgetGroup root) {
        super(machine);
        this.root = root;
    }

}
