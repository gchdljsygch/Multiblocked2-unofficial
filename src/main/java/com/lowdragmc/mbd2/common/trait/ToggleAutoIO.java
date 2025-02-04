package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;


public class ToggleAutoIO extends AutoIO implements IToggleConfigurable {
    @Getter
    @Setter
    @Persisted
    private boolean enable;
}
