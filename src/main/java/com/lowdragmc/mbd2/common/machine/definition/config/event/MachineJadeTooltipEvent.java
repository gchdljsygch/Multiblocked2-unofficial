package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Cancelable
@LDLRegister(name = "MachineJadeTooltipEvent", group = "MachineEvent")
public class MachineJadeTooltipEvent extends MachineEvent {
    @Nullable
    @GraphParameterGet
    public final Player player;
    @GraphParameterGet(displayName = "provider uid")
    public final ResourceLocation providerUid;
    @GraphParameterSet(identity = "tooltip", displayName = "tooltip", type = String.class)
    public String customText = "";
    private final List<Component> tooltips = new ArrayList<>();

    public MachineJadeTooltipEvent(MBDMachine machine, @Nullable Player player, ResourceLocation providerUid) {
        super(machine);
        this.player = player;
        this.providerUid = providerUid;
    }

    public void add(Component component) {
        if (component != null) {
            tooltips.add(component);
        }
    }

    public void addText(String text) {
        if (text != null && !text.isBlank()) {
            tooltips.add(Component.literal(text));
        }
    }

    public void addTranslatable(String key, Object... args) {
        if (key != null && !key.isBlank()) {
            tooltips.add(Component.translatable(key, args));
        }
    }

    public boolean isShiftKeyDown() {
        return player != null && player.isShiftKeyDown();
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("providerUid")).ifPresent(p -> p.setValue(providerUid));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("tooltip"))
                .map(ExposedParameter::getValue)
                .map(Object::toString)
                .ifPresent(this::addText);
    }
}
