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
 * Fired when machine recipe logic is waiting on a recipe.
 * <p>
 * The recipe may be stalled by missing inputs/outputs or other recipe logic
 * conditions depending on the active {@code RecipeLogic}.
 */
@Getter
@LDLRegister(name = "MachineOnRecipeWaitingEvent", group = "MachineEvent")
public class MachineOnRecipeWaitingEvent extends MachineEvent {
    /**
     * Recipe currently being waited on.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;

    /**
     * Creates an event for a recipe waiting state.
     * <p>
     * The recipe is selected but cannot currently progress because recipe logic is waiting on inputs, outputs, or other
     * runtime conditions.
     *
     * @param machine machine whose recipe logic is waiting
     * @param recipe  recipe currently waiting
     */
    public MachineOnRecipeWaitingEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
    }
}
