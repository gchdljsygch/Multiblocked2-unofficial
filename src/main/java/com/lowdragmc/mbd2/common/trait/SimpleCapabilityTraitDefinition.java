package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class SimpleCapabilityTraitDefinition extends RecipeCapabilityTraitDefinition implements IUIProviderTrait {

    @Configurable(name = "config.definition.trait.capability_io", subConfigurable = true,
            tips = {"config.definition.trait.capability_io.tooltip.0", "config.definition.trait.capability_io.tooltip.1"})
    private final CapabilityIO capabilityIO = new CapabilityIO();

    @Configurable(name = "config.definition.trait.gui_io", tips = "config.definition.trait.gui_io.tooltip")
    private IO guiIO = IO.BOTH;

    @Override
    public abstract SimpleCapabilityTrait createTrait(MBDMachine machine);

}
