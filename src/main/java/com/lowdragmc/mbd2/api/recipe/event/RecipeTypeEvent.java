package com.lowdragmc.mbd2.api.recipe.event;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.ILDLRegister;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDClientEvents;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDServerEvents;
import lombok.Getter;
import net.minecraftforge.eventbus.api.Event;

/**
 * Base event for recipe-type extension hooks.
 *
 * <p>The business goal is to expose recipe-type lifecycle, UI, and proxy
 * conversion hooks to graph processors and KubeJS without coupling those systems
 * to the recipe implementation. Events are posted in the context that triggers
 * the hook: UI events on the client, proxy conversion during recipe-manager
 * loading, and server hooks on the logical server. Subclasses may be cancelable;
 * cancellation is honored only when the concrete subclass carries Forge's
 * cancelable marker.</p>
 */
@Getter
public class RecipeTypeEvent extends Event implements ILDLRegister {
    @GraphParameterGet
    public final MBDRecipeType recipeType;

    /**
     * Creates an event for one recipe type.
     *
     * @param recipeType recipe type being extended
     */
    public RecipeTypeEvent(MBDRecipeType recipeType) {
        this.recipeType = recipeType;
    }

    /**
     * Posts this event to custom event targets.
     *
     * <p>Side effects: currently delegates to KubeJS event posting. Future graph
     * dispatch should use the same event instance so listener mutations and
     * cancellation state are preserved.</p>
     *
     * @return this event after dispatch
     */
    public RecipeTypeEvent postCustomEvent() {
        // TODO post to the graph events
//        machine.getDefinition().machineEvents().postGraphEvent(this);
        // post to the KubeJS events
        postKubeJSEvent();
        return this;
    }

    /**
     * Posts this event to KubeJS when integration is loaded.
     *
     * <p>Side effects: calls server and, on the client, client KubeJS event
     * buses. If a KubeJS callback interrupts with false and this event is
     * cancelable, the event is canceled. Posting exceptions are logged and do not
     * escape.</p>
     *
     * @return this event after KubeJS dispatch
     */
    public RecipeTypeEvent postKubeJSEvent() {
        // post to the KubeJS events
        if (MBD2.isKubeJSLoaded()) {
            try {
                if (LDLib.isClient()) {
                    if (MBDServerEvents.postRecipeTypeEvent(this).interruptFalse() && isCancelable()) {
                        setCanceled(true);
                    } else if (MBDClientEvents.postRecipeTypeEvent(this).interruptFalse() && isCancelable()) {
                        setCanceled(true);
                    }
                } else {
                    if (MBDServerEvents.postRecipeTypeEvent(this).interruptFalse() && isCancelable()) {
                        setCanceled(true);
                    }
                }
            } catch (Exception e) {
                MBD2.LOGGER.error("Failed to post KubeJS event {}", this, e);
            }
        }
        return this;
    }

    /**
     * Returns a compact diagnostic representation.
     *
     * @return event type, recipe type, and cancellation state
     */
    @Override
    public String toString() {
        return "RecipeTypeEvent{" +
                "recipeType=" + recipeType +
                ", eventName='" + getClass().getSimpleName() + '\'' +
                ", isCanceled=" + isCanceled() +
                '}';
    }
}
