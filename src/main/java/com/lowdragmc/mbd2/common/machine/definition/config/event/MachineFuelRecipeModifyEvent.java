package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;

/**
 * Fired when a machine is about to use or modify a fuel recipe.
 * <p>
 * Graph handlers receive the candidate as {@code recipe.in} and can return a
 * replacement through {@code recipe.out}. Canceling the event or returning
 * {@code null} prevents the fuel recipe from being used.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineFuelRecipeModifyEvent", group = "MachineEvent")
public class MachineFuelRecipeModifyEvent extends MachineEvent {
    /**
     * Mutable fuel recipe candidate. A {@code null} value rejects the recipe.
     */
    @GraphParameterGet(identity = "recipe.in")
    @GraphParameterSet(identity = "recipe.out")
    @Setter
    public MBDRecipe recipe;

    /**
     * Creates a fuel recipe modification event.
     * <p>
     * Handlers can replace {@link #recipe}, return a replacement through {@code recipe.out}, cancel the event, or leave
     * the recipe unchanged. A final {@code null} recipe means the candidate fuel recipe is unavailable.
     *
     * @param machine machine evaluating the fuel recipe
     * @param recipe  fuel recipe candidate before custom modification
     */
    public MachineFuelRecipeModifyEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe.in")).ifPresent(p -> p.setValue(recipe));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        this.recipe = Optional.ofNullable(exposedParameters.get("recipe.out"))
                .map(ExposedParameter::getValue)
                .filter(MBDRecipe.class::isInstance)
                .map(MBDRecipe.class::cast)
                .orElse(null);
    }

}
