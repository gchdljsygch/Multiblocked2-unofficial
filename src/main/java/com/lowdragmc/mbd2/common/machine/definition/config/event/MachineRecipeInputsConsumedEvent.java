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
 * Fired after recipe inputs have been consumed.
 * <p>
 * {@link #consumedInputs} records the exact consumed objects grouped by recipe
 * capability. It is intended for logging, rewards, side effects, or scripting
 * that needs to know what was actually taken.
 */
@Getter
@LDLRegister(name = "MachineRecipeInputsConsumedEvent", group = "MachineEvent")
public class MachineRecipeInputsConsumedEvent extends MachineEvent {
    /**
     * Recipe whose inputs were consumed.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;
    /**
     * Consumption record grouped by recipe capability.
     */
    @GraphParameterGet
    public final RecipeConsumption consumedInputs;
    /**
     * Whether the consumption happened after the working phase instead of
     * before it.
     */
    @GraphParameterGet
    public final boolean afterWorking;

    /**
     * Creates an event for completed recipe input consumption.
     * <p>
     * The consumption record is caller-owned and should be treated as read-only by handlers. Use {@code afterWorking} to
     * distinguish delayed consumption from the normal pre-work consumption path.
     *
     * @param machine        machine whose recipe inputs were consumed
     * @param recipe         recipe that consumed inputs
     * @param consumedInputs exact consumed inputs grouped by recipe capability
     * @param afterWorking   {@code true} when consumption happened after the working phase
     */
    public MachineRecipeInputsConsumedEvent(MBDMachine machine, MBDRecipe recipe, RecipeConsumption consumedInputs, boolean afterWorking) {
        super(machine);
        this.recipe = recipe;
        this.consumedInputs = consumedInputs;
        this.afterWorking = afterWorking;
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
        Optional.ofNullable(exposedParameters.get("afterWorking")).ifPresent(p -> p.setValue(afterWorking));
    }
}
