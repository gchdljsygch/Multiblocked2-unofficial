package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;

/**
 * Fired before a machine begins working on a recipe.
 * <p>
 * Canceling this event makes the caller treat the before-work callback as
 * handled and prevents the default before-work behavior from continuing.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineBeforeRecipeWorkingEvent", group = "MachineEvent")
public class MachineBeforeRecipeWorkingEvent extends MachineEvent {
    /**
     * Recipe that is about to start working.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;

    /**
     * Creates an event for a recipe that is about to start work.
     * <p>
     * Canceling the event stops the caller's before-work path for this recipe attempt. The event is posted on the
     * machine's recipe logic thread.
     *
     * @param machine machine whose recipe logic is starting work
     * @param recipe  recipe selected for the next working phase
     */
    public MachineBeforeRecipeWorkingEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
    }
}
