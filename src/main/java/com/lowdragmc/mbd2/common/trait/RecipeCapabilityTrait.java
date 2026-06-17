package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.RecipeGroup;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base trait for resources that participate in recipe IO.
 *
 * <p>The business goal is to bridge editor-defined
 * {@link RecipeCapabilityTraitDefinition} settings with runtime recipe handlers:
 * IO direction, distinct matching, slot routing, recipe groups, change
 * listeners, and sync/render invalidation. Instances are owned by one
 * {@link MBDMachine}; listener registration and notification are not
 * thread-safe and should run on the machine's logical thread.</p>
 */
public abstract class RecipeCapabilityTrait implements ITrait, IEnhancedManaged {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Getter
    private final MBDMachine machine;
    @Getter
    private final RecipeCapabilityTraitDefinition definition;
    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * Creates a recipe-capability trait bound to a machine.
     *
     * @param machine    machine that owns this trait
     * @param definition editor/runtime definition controlling recipe IO behavior
     */
    public RecipeCapabilityTrait(MBDMachine machine, RecipeCapabilityTraitDefinition definition) {
        this.machine = machine;
        this.definition = definition;
    }

    /**
     * Schedules a client render refresh for the owning machine.
     *
     * <p>Side effects: delegates to {@link MBDMachine#scheduleRenderUpdate()}.</p>
     */
    @Override
    public void scheduleRenderUpdate() {
        machine.scheduleRenderUpdate();
    }

    /**
     * Marks the owning machine as changed.
     *
     * <p>Side effects: delegates to {@link MBDMachine#onChanged()}, which may
     * mark sync data dirty and persist machine state.</p>
     */
    @Override
    public void onChanged() {
        machine.onChanged();
    }

    /**
     * Notifies all registered listeners that recipe-visible content changed.
     *
     * <p>Side effects: invokes listeners synchronously in registration order.
     * Listeners are expected to be lightweight and should not mutate the listener
     * list during iteration unless the implementation can tolerate it.</p>
     */
    public void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    // ***** for recipe trait ***** //

    /**
     * Registers a callback for recipe-visible content changes.
     *
     * <p>Business goal: let recipe logic re-check handlers when inventories,
     * tanks, energy stores, or other resources mutate. Side effects: stores the
     * listener until the returned subscription is invoked.</p>
     *
     * @param listener callback to run from {@link #notifyListeners()}
     * @return subscription that removes the listener
     */
    public ISubscription addChangedListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Returns the recipe IO direction configured for this trait.
     *
     * @return configured handler IO, such as input, output, both, or none
     */
    public IO getHandlerIO() {
        return getDefinition().getRecipeHandlerIO();
    }

    /**
     * Returns whether recipe matching should treat this handler distinctly.
     *
     * @return {@code true} when equal capability contents should not be merged
     * across this handler
     */
    public boolean isDistinct() {
        return getDefinition().isDistinct();
    }

    /**
     * Returns slot names accepted by this handler.
     *
     * @return configured slot names as a set; empty means unrestricted
     */
    public Set<String> getSlotNames() {
        return Arrays.stream(getDefinition().getSlotNames()).collect(Collectors.toSet());
    }

    /**
     * Returns the normalized recipe group for this handler.
     *
     * @return configured group, or {@link RecipeGroup#DEFAULT} when blank
     */
    public String getRecipeGroup() {
        return RecipeGroup.normalizeOrDefault(getDefinition().getRecipeGroup());
    }

}
