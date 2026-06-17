package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Fired when a fuel recipe finishes burning.
 * <p>
 * The recipe may be {@code null} when the fuel logic completed without a
 * concrete last fuel recipe.
 */
@Getter
@LDLRegister(name = "MachineFuelBurningFinishEvent", group = "MachineEvent")
public class MachineFuelBurningFinishEvent extends MachineEvent {
    /**
     * Fuel recipe that finished, if known.
     */
    @GraphParameterGet(identity = "recipe")
    @Nullable
    public final MBDRecipe recipe;

    /**
     * Creates an event for a completed fuel burn.
     * <p>
     * The recipe may be {@code null} when the fuel logic has no concrete last fuel recipe to report.
     *
     * @param machine machine whose fuel logic finished burning
     * @param recipe  fuel recipe that finished, or {@code null} when unknown
     */
    public MachineFuelBurningFinishEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
    }

}
