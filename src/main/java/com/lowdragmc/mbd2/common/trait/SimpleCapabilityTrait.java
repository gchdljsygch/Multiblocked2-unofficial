package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public abstract class SimpleCapabilityTrait extends RecipeCapabilityTrait {
    public CapabilityIO getCapabilityIOOverride = null;

    public SimpleCapabilityTrait(MBDMachine machine, SimpleCapabilityTraitDefinition definition) {
        super(machine, definition);
    }

    @Override
    public SimpleCapabilityTraitDefinition getDefinition() {
        return (SimpleCapabilityTraitDefinition)super.getDefinition();
    }

    public IO CapabilityIO (@Nullable Direction side) {
        var front = getMachine().getFrontFacing().orElse(Direction.NORTH);
        var IO = getCapabilityIOOverride == null ? getDefinition().getCapabilityIO() : getCapabilityIOOverride;
        return IO.getIO(front, side);
    }

}
