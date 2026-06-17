package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineEvent;

/**
 * Event graph configuration for entity-backed machines.
 * <p>
 * This specialization registers the {@code EntityMachineEvent} group so entity
 * machine definitions expose spawn, tick, interact, remove, and fixed-tick
 * graph hooks separately from block-machine events.
 */
public class ConfigEntityAISettings extends ConfigMachineEvents {

    /**
     * Creates entity-machine graph event settings with the entity event group enabled.
     * <p>
     * Side effect: registers {@code EntityMachineEvent} as an allowed graph event group. Instances are mutable editor
     * configuration objects and should be edited on the editor/UI thread.
     */
    public ConfigEntityAISettings() {
        registerEventGroup("EntityMachineEvent");
    }
}
