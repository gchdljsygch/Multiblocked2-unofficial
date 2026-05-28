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

@Getter
@LDLRegister(name = "MachineOnConsumeInputsAfterWorkingEvent", group = "MachineEvent")
public class MachineOnConsumeInputsAfterWorkingEvent extends MachineEvent {
    @GraphParameterGet
    public final MBDRecipe recipe;
    @GraphParameterGet
    public final RecipeConsumption consumedInputs;

    public MachineOnConsumeInputsAfterWorkingEvent(MBDMachine machine, MBDRecipe recipe) {
        this(machine, recipe, RecipeConsumption.EMPTY);
    }

    public MachineOnConsumeInputsAfterWorkingEvent(MBDMachine machine, MBDRecipe recipe, RecipeConsumption consumedInputs) {
        super(machine);
        this.recipe = recipe;
        this.consumedInputs = consumedInputs;
    }

    public List<RecipeConsumption.Entry> getConsumedEntries() {
        return consumedInputs.getEntries();
    }

    public List<Object> getConsumedInputs(RecipeCapability<?> capability) {
        return consumedInputs.get(capability);
    }

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
