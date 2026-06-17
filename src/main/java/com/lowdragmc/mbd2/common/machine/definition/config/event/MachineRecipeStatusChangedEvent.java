package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;


/**
 * Fired when a machine recipe logic status changes.
 * <p>
 * Multiblock controllers also use this event when formation refreshes the
 * visible state even if the old and new statuses are equal.
 */
@Getter
@LDLRegister(name = "MachineRecipeStatusChangedEvent", group = "MachineEvent")
public class MachineRecipeStatusChangedEvent extends MachineEvent {
    /**
     * Previous recipe logic status. Graph handlers receive the enum name as a
     * string.
     */
    @GraphParameterGet(displayName = "old status", type = String.class, tips = "graph_processor.node.mbd2.recipe_logic.status.tips")
    public final RecipeLogic.Status oldStatus;
    /**
     * New recipe logic status. Graph handlers receive the enum name as a string.
     */
    @GraphParameterGet(displayName = "new status", type = String.class, tips = "graph_processor.node.mbd2.recipe_logic.status.tips")
    public final RecipeLogic.Status newStatus;

    /**
     * Creates an event for a recipe-logic status transition.
     * <p>
     * The statuses are exposed to graph handlers as enum-name strings. Callers may also post this when refreshing visible
     * state for multiblocks, so consumers should not assume the two statuses are different.
     *
     * @param machine   machine whose recipe logic status changed or refreshed
     * @param oldStatus previous recipe logic status
     * @param newStatus new recipe logic status
     */
    public MachineRecipeStatusChangedEvent(MBDMachine machine, RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        super(machine);
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("oldStatus")).ifPresent(p -> p.setValue(oldStatus.toString()));
        Optional.ofNullable(exposedParameters.get("newStatus")).ifPresent(p -> p.setValue(newStatus.toString()));
    }
}
