package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;

/**
 * Editable definition for side-aware simple capability traits.
 *
 * <p>The business goal is to configure both external Forge capability access
 * ({@link #getCapabilityIO()}) and GUI exposure ({@link #getGuiIO()}) for traits
 * such as item slots, fluid tanks, and energy buffers. Instances are mutable in
 * the editor and should be treated as read-only once runtime traits are created.</p>
 */
@Getter
@Setter
public abstract class SimpleCapabilityTraitDefinition extends RecipeCapabilityTraitDefinition implements IUIProviderTrait {

    @Configurable(name = "config.definition.trait.capability_io", subConfigurable = true,
            tips = {"config.definition.trait.capability_io.tooltip.0", "config.definition.trait.capability_io.tooltip.1"})
    private final CapabilityIO capabilityIO = new CapabilityIO();

    @Configurable(name = "config.definition.trait.gui_io", tips = "config.definition.trait.gui_io.tooltip")
    private IO guiIO = IO.BOTH;

    /**
     * Creates the runtime simple capability trait for a machine.
     *
     * <p>Preconditions: call after this definition has been fully loaded from the
     * project file or editor state. Side effects should be limited to constructing
     * the trait and any trait-owned storage; world interaction belongs in trait
     * lifecycle callbacks.</p>
     *
     * @param machine machine that will own the created trait
     * @return new runtime trait using this side and GUI IO definition
     */
    @Override
    public abstract SimpleCapabilityTrait createTrait(MBDMachine machine);

}
