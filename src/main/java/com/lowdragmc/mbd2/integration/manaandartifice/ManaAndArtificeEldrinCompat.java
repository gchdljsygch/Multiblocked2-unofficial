package com.lowdragmc.mbd2.integration.manaandartifice;

import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.integration.manaandartifice.trait.ManaAndArtificeEldrinCapabilityTrait;
import com.mna.api.blocks.tile.IEldrinCapacitorTile;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class ManaAndArtificeEldrinCompat {
    private ManaAndArtificeEldrinCompat() {
    }

    public static @Nullable ManaAndArtificeEldrinCapabilityTrait getTrait(BlockEntity blockEntity) {
        if (blockEntity instanceof IMachineBlockEntity holder && holder.getMetaMachine() instanceof MBDMachine machine) {
            for (var trait : machine.getAdditionalTraits()) {
                if (trait instanceof ManaAndArtificeEldrinCapabilityTrait eldrinTrait) {
                    return eldrinTrait;
                }
            }
        }
        return null;
    }

    public static @Nullable IEldrinCapacitorTile getCapacitor(BlockEntity blockEntity) {
        return getTrait(blockEntity);
    }
}
