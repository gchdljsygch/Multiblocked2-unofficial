package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

/**
 * Fired after a machine recipe finishes.
 * <p>
 * At this point the recipe lifecycle has reached its finish callback; input
 * consumption timing depends on the machine's recipe settings.
 */
@Getter
@LDLRegister(name = "MachineOnRecipeFinishEvent", group = "MachineEvent")
public class MachineOnRecipeFinishEvent extends MachineEvent {
    /**
     * Recipe that just finished.
     */
    @GraphParameterGet
    public final MBDRecipe recipe;

    /**
     * Creates an event for a recipe finish callback.
     * <p>
     * This is notification-only. Input consumption may already have happened or may be handled by delayed-consumption
     * events depending on the machine recipe settings.
     *
     * @param machine machine whose recipe finished
     * @param recipe  recipe that reached its finish callback
     */
    public MachineOnRecipeFinishEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

}
