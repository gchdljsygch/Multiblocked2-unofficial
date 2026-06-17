package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;

/**
 * Fired after a multiblock pattern matches and before the catalyst is consumed.
 * <p>
 * Canceling this event rejects the catalyst use and prevents structure
 * formation from continuing.
 */
@Cancelable
@LDLRegister(name = "MachineUseCatalystEvent", group = "MachineEvent.Multiblock")
public class MachineUseCatalystEvent extends MachineEvent {
    /**
     * Catalyst stack held by the player.
     */
    @GraphParameterGet
    public final ItemStack catalyst;
    /**
     * Player using the catalyst.
     */
    @GraphParameterGet
    public final Player player;
    /**
     * Hand containing the catalyst stack.
     */
    @GraphParameterGet
    public final InteractionHand hand;

    /**
     * Creates an event for a multiblock catalyst use.
     * <p>
     * Canceling the event rejects the catalyst and prevents the structure formation path from continuing. The catalyst
     * stack is the caller-owned stack from the player's hand; handlers should not mutate it unless they intentionally
     * participate in consumption behavior.
     *
     * @param machine  controller machine attempting to form
     * @param catalyst catalyst stack being used
     * @param player   player using the catalyst
     * @param hand     hand containing the catalyst stack
     */
    public MachineUseCatalystEvent(MBDMachine machine, ItemStack catalyst, Player player, InteractionHand hand) {
        super(machine);
        this.catalyst = catalyst;
        this.player = player;
        this.hand = hand;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("catalyst")).ifPresent(p -> p.setValue(catalyst));
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("hand")).ifPresent(p -> p.setValue(hand));
    }
}
