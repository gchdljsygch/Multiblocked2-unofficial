package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;

public class RegisteredMBDLivingMachineEntity extends MBDLivingMachineEntity {
    public RegisteredMBDLivingMachineEntity(EntityType<? extends LivingEntity> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level, definition);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }
}
