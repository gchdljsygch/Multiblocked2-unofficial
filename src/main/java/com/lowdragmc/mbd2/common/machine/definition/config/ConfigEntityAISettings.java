package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineEvent;

public class ConfigEntityAISettings extends ConfigMachineEvents {

    public ConfigEntityAISettings() {
        registerEventGroup("EntityMachineEvent");
    }
}
