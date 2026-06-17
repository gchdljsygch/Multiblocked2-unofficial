package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Setter;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Base recipe trait for simple side-aware Forge capabilities.
 *
 * <p>The business goal is to combine recipe IO settings inherited from
 * {@link RecipeCapabilityTrait} with side routing from
 * {@link SimpleCapabilityTraitDefinition}. Instances may temporarily override
 * capability IO at runtime; that override is mutable and should be changed only
 * on the owning machine's logical thread.</p>
 */
public abstract class SimpleCapabilityTrait extends RecipeCapabilityTrait {
    /**
     * Runtime capability IO override used by dynamic machine behavior.
     */
    @Setter
    private CapabilityIO capabilityIOOverride = null;

    /**
     * Creates a simple capability trait bound to a machine.
     *
     * @param machine    machine that owns this trait
     * @param definition side and recipe IO definition for the trait
     */
    public SimpleCapabilityTrait(MBDMachine machine, SimpleCapabilityTraitDefinition definition) {
        super(machine, definition);
    }

    /**
     * Returns this trait's concrete definition type.
     *
     * @return simple capability definition that created this trait
     */
    @Override
    public SimpleCapabilityTraitDefinition getDefinition() {
        return (SimpleCapabilityTraitDefinition) super.getDefinition();
    }

    /**
     * Resolves capability IO for a queried world side.
     *
     * <p>Preconditions: {@code side == null} means an internal or unsided
     * capability query. The machine front defaults to north when the machine is
     * not directional. Side effects: none.</p>
     *
     * @param side queried block side, or {@code null} for unsided access
     * @return effective IO for the queried side after applying any runtime
     * override
     */
    public IO getCapabilityIO(@Nullable Direction side) {
        var front = getMachine().getFrontFacing().orElse(Direction.NORTH);
        var IO = capabilityIOOverride == null ? getDefinition().getCapabilityIO() : capabilityIOOverride;
        return IO.getIO(front, side);
    }

}
