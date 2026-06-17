package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Concrete non-living entity type used for definition-registered machine entities.
 *
 * <p>The class exists as the stable runtime implementation referenced by dynamically registered entity definitions.
 * Behavior is inherited from {@link MBDMachineEntity}; the supplied definition determines machine traits, renderer,
 * persistence, interaction, and UI behavior.</p>
 */
public class RegisteredMBDMachineEntity extends MBDMachineEntity {

    /**
     * Creates a registered non-living machine entity.
     *
     * @param entityType vanilla entity type produced by the registry entry
     * @param level      level that owns the entity
     * @param definition definition used to create the attached meta-machine
     */
    public RegisteredMBDMachineEntity(EntityType<?> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level, definition);
    }
}
