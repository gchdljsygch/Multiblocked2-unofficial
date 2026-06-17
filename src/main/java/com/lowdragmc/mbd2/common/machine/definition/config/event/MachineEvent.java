package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.ILDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDClientEvents;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDServerEvents;
import lombok.Getter;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

import java.util.*;

/**
 * Base Forge event for block-backed machine lifecycle, interaction, recipe, UI,
 * and integration hooks.
 * <p>
 * Each machine event is also an LDL editor event. Calling
 * {@link #postCustomEvent()} first dispatches the event to the machine
 * definition's graph event handlers, then to KubeJS when that integration is
 * loaded. The caller is still responsible for posting the returned event to the
 * Forge event bus when Forge listeners should observe it.
 * <p>
 * Public fields annotated with {@link GraphParameterGet} are copied into graph
 * parameters before processing. Fields annotated with {@link GraphParameterSet}
 * may be read back after processing. If the event class is {@link Cancelable},
 * a writable {@code cancel} parameter is also exposed.
 * <p>
 * Thread safety: events are mutable, single-use objects. Create, post, and read
 * them on the same logical thread that owns the triggering machine operation.
 */
@Getter
public class MachineEvent extends Event implements ILDLRegister {
    /**
     * Machine instance that triggered the event. Graph handlers receive this as
     * the {@code machine} parameter.
     */
    @GraphParameterGet
    public final MBDMachine machine;

    /**
     * Creates an event for a machine.
     *
     * @param machine triggering machine; must remain valid for the duration of
     *                event processing
     */
    public MachineEvent(MBDMachine machine) {
        this.machine = machine;
    }

    /**
     * Dispatches this event to machine graph handlers and KubeJS hooks.
     *
     * @return this event after graph/KubeJS handlers have applied any writable
     * parameters or cancellation
     */
    public MachineEvent postCustomEvent() {
        // post to the graph events
        machine.getDefinition().machineEvents().postGraphEvent(this);
        // post to the KubeJS events
        postKubeJSEvent();
        return this;
    }

    /**
     * Dispatches this event to KubeJS hooks when KubeJS is loaded.
     * <p>
     * A KubeJS interrupt-false result cancels this event only when the event
     * type is cancelable. Exceptions are logged and do not propagate to the
     * machine lifecycle caller.
     *
     * @return this event after KubeJS handling
     */
    public MachineEvent postKubeJSEvent() {
        // post to the KubeJS events
        if (MBD2.isKubeJSLoaded()) {
            try {
                if (LDLib.isClient()) {
                    if (MBDServerEvents.postMachineEvent(this).interruptFalse() && isCancelable()) {
                        setCanceled(true);
                    } else if (MBDClientEvents.postMachineEvent(this).interruptFalse() && isCancelable()) {
                        setCanceled(true);
                    }
                } else {
                    if (MBDServerEvents.postMachineEvent(this).interruptFalse() && isCancelable()) {
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
     * Get the exposed parameters for the given event class, it will detect all public fields with annotations in the class.
     * <br>
     * {@link GraphParameterGet} marked fields will be used to pass parameters to the graph.
     * <br<
     * {@link GraphParameterSet} marked fields will be used to gather parameters from the graph.
     *
     * @param clazz event class
     * @return newly-created exposed parameter descriptors in reflection order
     */
    public static List<ExposedParameter<?>> getExposedParameters(Class<? extends MachineEvent> clazz) {
        var parameters = new ArrayList<ExposedParameter<?>>();
        for (var field : clazz.getFields()) {
            if (field.isAnnotationPresent(GraphParameterGet.class)) {
                var annotation = field.getAnnotation(GraphParameterGet.class);
                var identity = field.getName();
                var displayName = field.getName();
                var type = field.getType();
                List<String> tips = null;
                if (!annotation.identity().isEmpty()) {
                    identity = annotation.identity();
                }
                if (!annotation.displayName().isEmpty()) {
                    displayName = annotation.displayName();
                }
                if (annotation.type() != ExposedParameter.class) {
                    type = annotation.type();
                }
                if (annotation.tips().length > 0) {
                    tips = Arrays.asList(annotation.tips());
                }
                parameters.add(new ExposedParameter<>(identity, type)
                        .setTips(tips)
                        .setAccessor(ExposedParameter.ParameterAccessor.Get)
                        .setDisplayName(displayName));
            }
            if (field.isAnnotationPresent(GraphParameterSet.class)) {
                var annotation = field.getAnnotation(GraphParameterSet.class);
                var identity = field.getName();
                var displayName = field.getName();
                var type = field.getType();
                List<String> tips = null;
                if (!annotation.identity().isEmpty()) {
                    identity = annotation.identity();
                }
                if (!annotation.displayName().isEmpty()) {
                    displayName = annotation.displayName();
                }
                if (annotation.type() != ExposedParameter.class) {
                    type = annotation.type();
                }
                if (annotation.tips().length > 0) {
                    tips = Arrays.asList(annotation.tips());
                }
                parameters.add(new ExposedParameter<>(identity, type)
                        .setTips(tips)
                        .setAccessor(ExposedParameter.ParameterAccessor.Set)
                        .setDisplayName(displayName));
            }
        }
        if (clazz.isAnnotationPresent(Cancelable.class)) {
            parameters.add(new ExposedParameter<>("cancel", Boolean.class)
                    .setAccessor(ExposedParameter.ParameterAccessor.Set)
                    .setDisplayName("cancel"));
        }
        return parameters;
    }

    /**
     * Bind (pass) the parameters to the graph before the graph is processed.
     *
     * @param exposedParameters graph parameter map keyed by exposed identity
     */
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        Optional.ofNullable(exposedParameters.get("machine")).ifPresent(p -> p.setValue(machine));
    }

    /**
     * Gather (get) the parameters from the graph after the graph has been processed.
     *
     * @param exposedParameters graph parameter map keyed by exposed identity
     */
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        if (isCancelable()) {
            Optional.ofNullable(exposedParameters.get("cancel")).ifPresent(p -> {
                if (p.getValue() instanceof Boolean cancel) {
                    setCanceled(cancel);
                }
            });
        }
    }

    @Override
    public String toString() {
        return "MachineEvent{" +
                "machine=" + machine +
                ", eventName='" + getClass().getSimpleName() + '\'' +
                ", isCanceled=" + isCanceled() +
                '}';
    }
}
