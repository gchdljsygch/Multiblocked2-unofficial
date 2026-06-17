package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Fired after a machine block is placed by a living entity using an item stack.
 * <p>
 * This is not fired for raw world block placement APIs that bypass
 * {@code setPlacedBy}.
 */
@Getter
@LDLRegister(name = "MachinePlacedEvent", group = "MachineEvent")
public class MachinePlacedEvent extends MachineEvent {
    /**
     * Entity that placed the machine. The historical parameter name is
     * {@code player}, but non-player living entities may be supplied by
     * Minecraft.
     */
    @GraphParameterGet
    public final LivingEntity player;
    /**
     * Stack used to place the machine.
     */
    @GraphParameterGet
    public final ItemStack itemStack;

    /**
     * Creates an event for a machine placed from an item stack.
     * <p>
     * The stack is the placement stack supplied by Minecraft and may be mutable caller-owned state. This event is
     * notification-only and is posted on the logical side that handled placement.
     *
     * @param machine   machine runtime created for the placed block
     * @param player    living entity that placed the block; may be a non-player entity
     * @param itemStack stack used for placement
     */
    public MachinePlacedEvent(MBDMachine machine, LivingEntity player, ItemStack itemStack) {
        super(machine);
        this.player = player;
        this.itemStack = itemStack;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("itemStack")).ifPresent(p -> p.setValue(itemStack));
    }
}
