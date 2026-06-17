package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.core.Direction;

/**
 * Trigger node that writes redstone output state to a machine.
 *
 * <p>Signal and direct-signal modes require a side; analog mode ignores side and writes the machine-wide analog output.
 * Numeric clamping, if any, is delegated to the target machine.</p>
 */
@LDLRegister(name = "set machine signal", group = "graph_processor.node.mbd2.machine")
public class SetSignalNode extends LinearTriggerNode {
    /**
     * Redstone output channel to mutate.
     */
    public enum Mode {
        /**
         * Writes normal side output signal.
         */
        SIGNAL,
        /**
         * Writes direct side output signal.
         */
        DIRECT_SIGNAL,
        /**
         * Writes analog output signal and ignores {@link SetSignalNode#side}.
         */
        ANALOG,
    }

    /**
     * Machine whose redstone output should be changed.
     */
    @InputPort
    public MBDMachine machine;
    /**
     * Target side for side-based modes.
     */
    @InputPort
    public Direction side;
    /**
     * Signal value to write.
     */
    @InputPort
    public int signal;

    // runtime
    /**
     * Output mode selected in the node configurator.
     */
    @Configurable
    public Mode mode = Mode.SIGNAL;

    /**
     * Applies the configured signal mode to the input machine.
     */
    @Override
    protected void process() {
        if (machine != null && (side != null || mode == Mode.ANALOG)) {
            switch (mode) {
                case ANALOG -> machine.setAnalogOutputSignal(signal);
                case SIGNAL -> machine.setOutputSignal(signal, side);
                case DIRECT_SIGNAL -> machine.setOutputDirectSignal(signal, side);
            }
        }
    }
}
