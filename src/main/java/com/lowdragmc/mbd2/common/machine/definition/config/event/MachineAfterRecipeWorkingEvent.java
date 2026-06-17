package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

/**
 * Fired after a machine recipe finishes its working phase.
 * <p>
 * Depending on recipe settings, this event can be used both for normal
 * after-work hooks and for deciding whether delayed input consumption should
 * proceed.
 */
@Getter
@LDLRegister(name = "MachineAfterRecipeWorkingEvent", group = "MachineEvent")
public class MachineAfterRecipeWorkingEvent extends MachineEvent {
    /**
     * Recipe that just completed the working phase.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;

    /**
     * Creates an event for a recipe that has just completed work.
     * <p>
     * The event is notification-only; handlers should not mutate the supplied recipe unless they own it. It is posted on
     * the machine's recipe logic thread during recipe progression.
     *
     * @param machine machine whose recipe logic completed work
     * @param recipe  recipe that completed the working phase
     */
    public MachineAfterRecipeWorkingEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
    }
}
