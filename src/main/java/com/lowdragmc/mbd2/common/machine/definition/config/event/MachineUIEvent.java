package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Optional;

/**
 * Fired while a machine creates its modular UI.
 * <p>
 * Graph/KubeJS handlers can inspect the opening player and replace
 * {@link #root}; if the root is set to {@code null}, the caller will skip UI
 * creation.
 */
@Getter
@Setter
@LDLRegister(name = "MachineUIEvent", group = "MachineEvent")
public class MachineUIEvent extends MachineEvent {
    /**
     * Mutable root widget that will be wrapped in a {@code ModularUI}.
     */
    public WidgetGroup root;
    /**
     * Player for whom the UI is being created.
     */
    @GraphParameterGet
    public Player player;

    /**
     * Creates an event for modular UI construction.
     * <p>
     * Handlers may replace {@link #root} before the caller wraps it in a {@code ModularUI}. Setting {@code root} to
     * {@code null} cancels UI creation by convention in the caller.
     *
     * @param machine machine whose UI is being built
     * @param root    initial root widget group
     * @param player  player opening the UI
     */
    public MachineUIEvent(MBDMachine machine, WidgetGroup root, Player player) {
        super(machine);
        this.root = root;
        this.player = player;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
    }
}
