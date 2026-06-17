package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;

/**
 * Concrete living entity type used for definition-registered machine entities.
 *
 * <p>The class provides the minimal vanilla equipment contract required by {@link LivingEntity} while delegating all
 * machine behavior to {@link MBDLivingMachineEntity}. Registered living machine definitions can still expose inventory
 * or equipment-like behavior through MBD traits; vanilla armor/hand slots are intentionally empty here.</p>
 */
public class RegisteredMBDLivingMachineEntity extends MBDLivingMachineEntity {

    /**
     * Creates a registered living machine entity.
     *
     * @param entityType vanilla living entity type produced by the registry entry
     * @param level      level that owns the entity
     * @param definition definition used to create the attached meta-machine
     */
    public RegisteredMBDLivingMachineEntity(EntityType<? extends LivingEntity> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level, definition);
    }

    /**
     * Returns the default arm for vanilla rendering/animation fallbacks.
     *
     * @return right arm
     */
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /**
     * Reports no vanilla armor slots.
     *
     * @return immutable empty iterable
     */
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    /**
     * Returns no vanilla equipment from any slot.
     *
     * @param slot queried equipment slot
     * @return empty stack
     */
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    /**
     * Ignores vanilla equipment assignment.
     *
     * <p>Definitions that need item storage should model it through machine traits rather than vanilla equipment
     * slots.</p>
     *
     * @param slot  target equipment slot
     * @param stack stack being assigned
     */
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }
}
