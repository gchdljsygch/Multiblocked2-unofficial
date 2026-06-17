package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;


/**
 * Toggleable automatic transfer configuration.
 *
 * <p>The business goal is to combine {@link AutoIO}'s per-side IO rules with an
 * enable flag that can be persisted and edited. The enable flag is mutable
 * runtime/configuration state and should be changed on the owning machine
 * thread.</p>
 */
public class ToggleAutoIO extends AutoIO implements IToggleConfigurable {
    @Getter
    @Setter
    @Persisted
    private boolean enable;
}
