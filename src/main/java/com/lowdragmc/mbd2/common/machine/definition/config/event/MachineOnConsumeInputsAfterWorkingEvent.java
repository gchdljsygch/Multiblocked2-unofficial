package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumption;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fired when a machine is consuming inputs after the working phase.
 * <p>
 * The first constructor is used before a concrete consumption record exists;
 * the second carries the actual consumed inputs after they are known.
 */
@Getter
@LDLRegister(name = "MachineOnConsumeInputsAfterWorkingEvent", group = "MachineEvent")
public class MachineOnConsumeInputsAfterWorkingEvent extends MachineEvent {
    /**
     * Recipe whose delayed inputs are being consumed.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;
    /**
     * Consumption record, or {@link RecipeConsumption#EMPTY} before consumption
     * details are available.
     */
    @GraphParameterGet
    public final RecipeConsumption consumedInputs;

    /**
     * Creates a pre-record event for delayed input consumption.
     * <p>
     * Use this overload before the concrete consumed input list has been calculated. The event exposes
     * {@link RecipeConsumption#EMPTY} for {@link #consumedInputs}.
     *
     * @param machine machine consuming delayed inputs
     * @param recipe  recipe whose delayed inputs are being consumed
     */
    public MachineOnConsumeInputsAfterWorkingEvent(MBDMachine machine, MBDRecipe recipe) {
        this(machine, recipe, RecipeConsumption.EMPTY);
    }

    /**
     * Creates an event carrying the actual delayed input consumption record.
     * <p>
     * The consumption record is caller-owned and should be treated as read-only by handlers unless they explicitly own
     * the recipe consumption pipeline.
     *
     * @param machine        machine consuming delayed inputs
     * @param recipe         recipe whose delayed inputs are being consumed
     * @param consumedInputs recorded inputs consumed after the working phase
     */
    public MachineOnConsumeInputsAfterWorkingEvent(MBDMachine machine, MBDRecipe recipe, RecipeConsumption consumedInputs) {
        super(machine);
        this.recipe = recipe;
        this.consumedInputs = consumedInputs;
    }

    /**
     * Returns all consumed entries in recorded order.
     *
     * @return immutable or caller-owned entry list from the consumption record
     */
    public List<RecipeConsumption.Entry> getConsumedEntries() {
        return consumedInputs.getEntries();
    }

    /**
     * Returns consumed inputs for a capability.
     *
     * @param capability recipe capability to query
     * @return consumed objects for that capability, or an empty list
     */
    public List<Object> getConsumedInputs(RecipeCapability<?> capability) {
        return consumedInputs.get(capability);
    }

    /**
     * Returns consumed inputs for a capability by registered name.
     *
     * @param capabilityName capability registry name
     * @return consumed objects for that capability, or an empty list
     */
    public List<Object> getConsumedInputs(String capabilityName) {
        return consumedInputs.get(capabilityName);
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe")).ifPresent(p -> p.setValue(recipe));
        Optional.ofNullable(exposedParameters.get("consumedInputs")).ifPresent(p -> p.setValue(consumedInputs));
    }

}
