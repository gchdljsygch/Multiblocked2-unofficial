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
 * Fired during each working tick of a machine recipe.
 * <p>
 * Canceling this event makes the caller treat the working callback as handled
 * and skips the default {@link com.lowdragmc.mbd2.api.machine.IMachine}
 * working behavior for that tick.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineOnRecipeWorkingEvent", group = "MachineEvent")
public class MachineOnRecipeWorkingEvent extends MachineEvent {
    /**
     * Recipe currently running.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;
    /**
     * Current recipe progress in ticks.
     */
    @GraphParameterGet
    public final int progress;

    /**
     * Creates an event for one recipe working tick.
     * <p>
     * Canceling this event skips the default working behavior for this tick only. Progress is measured in recipe ticks
     * and is supplied by the active {@code RecipeLogic}.
     *
     * @param machine  machine whose recipe is working
     * @param recipe   recipe currently running
     * @param progress current recipe progress in ticks, normally {@code 0..duration}
     */
    public MachineOnRecipeWorkingEvent(MBDMachine machine, MBDRecipe recipe, int progress) {
        super(machine);
        this.recipe = recipe;
        this.progress = progress;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
        Optional.ofNullable(exposedParameters.get("progress")).ifPresent(p -> p.setValue(progress));
    }
}
