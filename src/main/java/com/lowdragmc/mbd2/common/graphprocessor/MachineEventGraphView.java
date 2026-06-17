package com.lowdragmc.mbd2.common.graphprocessor;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseGraph;
import com.lowdragmc.lowdraglib.gui.graphprocessor.widget.GraphViewWidget;

import java.util.List;

/**
 * Editor graph view configured for MBD machine-event graphs.
 *
 * <p>The view extends LowDragLib's generic graph widget and appends the MBD machine, KubeJS, and Java node groups so
 * designers can build event logic from project-specific nodes. It is client/editor UI state and is not thread-safe.</p>
 */
public class MachineEventGraphView extends GraphViewWidget {
    /**
     * Creates a graph view widget for a machine-event graph.
     *
     * @param graph  graph model to display and edit
     * @param x      left position in the parent widget
     * @param y      top position in the parent widget
     * @param width  widget width in pixels
     * @param height widget height in pixels
     */
    public MachineEventGraphView(BaseGraph graph, int x, int y, int width, int height) {
        super(graph, x, y, width, height);
    }

    /**
     * Adds MBD-supported node groups to the graph view palette.
     *
     * @param supportNodeGroups mutable group list supplied by the base widget
     */
    @Override
    protected void setupNodeGroups(List<String> supportNodeGroups) {
        super.setupNodeGroups(supportNodeGroups);
        supportNodeGroups.add("graph_processor.node.mbd2.machine");
        supportNodeGroups.add("graph_processor.node.kjs");
        supportNodeGroups.add("graph_processor.node.java");
    }
}
