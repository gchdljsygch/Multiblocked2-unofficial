package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.Optional;

/**
 * Fired when a player right-clicks a block-backed machine.
 * <p>
 * Graph handlers can set {@link #interactionResult} through the boolean graph
 * parameter {@code interactionResult}: {@code true} maps to
 * {@link InteractionResult#SUCCESS}, {@code false} maps to
 * {@link InteractionResult#PASS}. The caller uses the final interaction result
 * directly.
 */
@Getter
@LDLRegister(name = "MachineRightClickEvent", group = "MachineEvent")
public class MachineRightClickEvent extends MachineEvent {
    /**
     * Player performing the click.
     */
    @GraphParameterGet
    public final Player player;
    /**
     * Stack held in {@link #hand} when the event was created.
     */
    @GraphParameterGet
    public final ItemStack heldItem;
    /**
     * Hand used for the click.
     */
    @GraphParameterGet
    public final InteractionHand hand;
    /**
     * Hit result supplied by the block interaction.
     */
    @GraphParameterGet
    public final BlockHitResult hit;
    /**
     * Mutable result returned to the block interaction caller.
     */
    @Setter
    @GraphParameterSet(displayName = "interaction result", type = Boolean.class)
    public InteractionResult interactionResult;

    /**
     * Creates an event for a player right-clicking a block-backed machine.
     * <p>
     * Side effect: snapshots the item currently held in {@code hand}. Graph handlers may replace
     * {@link #interactionResult}; {@code SUCCESS} consumes the interaction, while {@code PASS} lets later handling
     * continue. {@code hit} may be {@code null} when reused by entity-machine interaction bridging.
     *
     * @param machine machine being right-clicked
     * @param player  interacting player
     * @param hand    hand used for the click
     * @param hit     block hit result, or {@code null} when no block hit is available
     */
    public MachineRightClickEvent(MBDMachine machine, Player player, InteractionHand hand, BlockHitResult hit) {
        super(machine);
        this.player = player;
        this.heldItem = player.getItemInHand(hand);
        this.hand = hand;
        this.hit = hit;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("hand")).ifPresent(p -> p.setValue(hand));
        Optional.ofNullable(exposedParameters.get("hit")).ifPresent(p -> p.setValue(hit));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("interactionResult")).ifPresent(p -> {
            if (p.getValue() instanceof Boolean result) {
                interactionResult = result ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
        });
    }
}
