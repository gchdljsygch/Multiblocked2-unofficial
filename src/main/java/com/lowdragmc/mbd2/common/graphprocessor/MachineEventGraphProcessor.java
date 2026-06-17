package com.lowdragmc.mbd2.common.graphprocessor;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseGraph;
import com.lowdragmc.lowdraglib.gui.graphprocessor.processor.TriggerProcessor;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineEvent;

/**
 * Trigger processor that executes a LowDragLib graph for one concrete {@link MachineEvent} type.
 *
 * <p>The processor binds event parameters into the graph, runs the graph in depth-first order, and then gathers any
 * mutated exposed parameters back into the same event instance. Instances are stateful wrappers around a single graph
 * and are expected to run on the same thread that is dispatching the machine event.</p>
 */
public class MachineEventGraphProcessor extends TriggerProcessor {
    private final Class<? extends MachineEvent> eventType;

    /**
     * Creates a processor for events whose runtime class exactly matches {@code eventType}.
     *
     * @param eventType concrete event class accepted by this processor
     * @param graph     graph to execute; its exposed parameters are used as the event boundary
     */
    public MachineEventGraphProcessor(Class<? extends MachineEvent> eventType, BaseGraph graph) {
        super(graph);
        this.eventType = eventType;
        this.graph.updateComputeOrder(BaseGraph.ComputeOrderType.DepthFirst);
        this.updateComputeOrder();
    }

    /**
     * Executes the graph for {@code event} when its type matches this processor.
     *
     * <p>A mismatched event is logged and ignored. Successful execution mutates both the graph's node fields and the
     * event's exposed parameters according to the graph wiring.</p>
     *
     * @param event event instance to bind, run, and gather
     */
    public void postEvent(MachineEvent event) {
        if (event.getClass() != this.eventType) {
            MBD2.LOGGER.error("Attempted to post event of type " + event.getClass().getName() + " to processor of type " + this.eventType.getName());
            return;
        }
        // bind parameters -> run -> gather parameters
        event.bindParameters(graph.exposedParameters);
        run();
        event.gatherParameters(graph.exposedParameters);
    }

}
