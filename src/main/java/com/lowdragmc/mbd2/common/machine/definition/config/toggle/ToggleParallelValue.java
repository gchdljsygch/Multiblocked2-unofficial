package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;

/**
 * Toggle wrapper for a max-parallel recipe modifier.
 * <p>
 * When enabled, {@link #maxParallel} is used as a positive multiplier/limit for
 * recipe parallelism. {@link #modifyDuration} controls whether increasing
 * parallelism also modifies recipe duration in systems that support that
 * behavior.
 */
@Setter
@Getter
public class ToggleParallelValue implements IToggleConfigurable {

    @Getter
    @Setter
    @Persisted
    protected boolean enable;

    @Configurable(name = "config.machine_settings.max_parallel", tips = "config.machine_settings.max_parallel.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int maxParallel = 1;

    @Configurable(name = "config.machine_settings.modify_duration", tips = "config.machine_settings.modify_duration.tooltip")
    private boolean modifyDuration = false;

    /**
     * Creates a disabled parallel-value toggle with max parallel {@code 1}.
     */
    public ToggleParallelValue() {
        enable = false;
    }

}
