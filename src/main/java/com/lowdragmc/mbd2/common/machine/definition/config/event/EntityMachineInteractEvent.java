package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Fired when a player interacts with an entity-backed machine.
 * <p>
 * Graph handlers can set {@link #interactionResult} through the boolean graph
 * parameter {@code interactionResult}: {@code true} maps to
 * {@link InteractionResult#SUCCESS}, {@code false} maps to
 * {@link InteractionResult#PASS}.
 */
@Getter
@LDLRegister(name = "EntityMachineInteractEvent", group = "EntityMachineEvent")
public class EntityMachineInteractEvent extends EntityMachineEvent {
    /**
     * Player performing the interaction.
     */
    @GraphParameterGet
    public final Player player;
    /**
     * Stack held in {@link #hand} when the event was created.
     */
    @GraphParameterGet
    public final ItemStack heldItem;
    /**
     * Hand used for the interaction.
     */
    @GraphParameterGet
    public final InteractionHand hand;
    /**
     * Mutable interaction result returned to the entity interaction caller.
     */
    @Setter
    @GraphParameterSet(displayName = "interaction result", type = Boolean.class)
    public InteractionResult interactionResult = InteractionResult.PASS;

    /**
     * Creates an interaction event for an entity-backed machine.
     * <p>
     * Side effect: snapshots the item currently held in {@code hand}. Graph handlers may replace the final
     * {@link #interactionResult}; {@code SUCCESS} consumes the interaction, while {@code PASS} lets later handling
     * continue.
     *
     * @param machine entity machine runtime being interacted with
     * @param entity  backing Minecraft entity
     * @param player  interacting player
     * @param hand    hand used for the interaction
     */
    public EntityMachineInteractEvent(MBDEntityMachine machine, Entity entity, Player player, InteractionHand hand) {
        super(machine, entity);
        this.player = player;
        this.heldItem = player.getItemInHand(hand);
        this.hand = hand;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("heldItem")).ifPresent(p -> p.setValue(heldItem));
        Optional.ofNullable(exposedParameters.get("hand")).ifPresent(p -> p.setValue(hand));
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
