package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;

/**
 * Base event for modifying a machine recipe candidate.
 * <p>
 * Graph handlers receive the current recipe as {@code recipe.in} and can return
 * a replacement through {@code recipe.out}. Returning no valid recipe sets
 * {@link #recipe} to {@code null}, which callers treat as unavailable.
 */
@Getter
public class MachineRecipeModifyEvent extends MachineEvent {
    /**
     * Mutable recipe candidate. A {@code null} value rejects the recipe.
     */
    @GraphParameterGet(identity = "recipe.in")
    @GraphParameterSet(identity = "recipe.out")
    @Setter
    public MBDRecipe recipe;

    /**
     * Creates a recipe modification event for a candidate recipe.
     * <p>
     * Handlers can replace {@link #recipe} directly or return a replacement through {@code recipe.out}. A final
     * {@code null} recipe rejects the candidate.
     *
     * @param machine machine evaluating the recipe
     * @param recipe  recipe candidate before this modification stage
     */
    public MachineRecipeModifyEvent(MBDMachine machine, MBDRecipe recipe) {
        super(machine);
        this.recipe = recipe;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("recipe.in")).ifPresent(p -> p.setValue(recipe));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        this.recipe = Optional.ofNullable(exposedParameters.get("recipe.out"))
                .map(ExposedParameter::getValue)
                .filter(MBDRecipe.class::isInstance)
                .map(MBDRecipe.class::cast)
                .orElse(null);
    }

    /**
     * Fired before built-in machine recipe modifiers run.
     * <p>
     * Canceling this event stops the default modifier chain and returns the
     * current recipe value to the caller.
     */
    @LDLRegister(name = "MachineRecipeModifyEvent.Before", group = "MachineEvent")
    @Cancelable
    public static class Before extends MachineRecipeModifyEvent {
        /**
         * Creates a pre-modifier recipe event.
         * <p>
         * Canceling this event skips the built-in modifier chain and returns the current recipe value to the caller.
         *
         * @param machine machine evaluating the recipe
         * @param recipe  recipe candidate before built-in modifiers run
         */
        public Before(MBDMachine machine, MBDRecipe recipe) {
            super(machine, recipe);
        }
    }

    /**
     * Fired after built-in machine recipe modifiers have run.
     */
    @LDLRegister(name = "MachineRecipeModifyEvent.After", group = "MachineEvent")
    public static class After extends MachineRecipeModifyEvent {
        /**
         * Creates a post-modifier recipe event.
         *
         * @param machine machine evaluating the recipe
         * @param recipe  recipe after built-in modifiers have run; may be {@code null} when rejected earlier
         */
        public After(MBDMachine machine, MBDRecipe recipe) {
            super(machine, recipe);
        }
    }

}
