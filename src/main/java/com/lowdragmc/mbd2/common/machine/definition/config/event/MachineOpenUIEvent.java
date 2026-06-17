package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;


/**
 * Fired before a machine opens its UI for a player.
 * <p>
 * Canceling this event prevents the server-side UI factory from opening the
 * container.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineOpenUIEvent", group = "MachineEvent")
public class MachineOpenUIEvent extends MachineEvent {
    /**
     * Player requesting the UI.
     */
    @GraphParameterGet
    public final Player player;

    /**
     * Creates an event for a pending machine UI open.
     * <p>
     * Canceling this event prevents the caller from opening the server-side container for {@code player}.
     *
     * @param machine machine whose UI is being requested
     * @param player  player requesting the UI
     */
    public MachineOpenUIEvent(MBDMachine machine, Player player) {
        super(machine);
        this.player = player;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
    }
}
