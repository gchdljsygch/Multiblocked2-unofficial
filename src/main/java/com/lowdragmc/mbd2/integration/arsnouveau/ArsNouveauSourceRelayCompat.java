package com.lowdragmc.mbd2.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.integration.arsnouveau.trait.ArsNouveauSourceCapabilityTrait;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class ArsNouveauSourceRelayCompat {
    private ArsNouveauSourceRelayCompat() {
    }

    public static boolean isMBDSource(Level level, BlockPos pos) {
        return getMBDSource(level, pos) != null;
    }

    public static @Nullable ISourceTile getSource(Level level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbstractSourceMachine sourceMachine) {
            return sourceMachine;
        }
        return getMBDSource(blockEntity);
    }

    private static @Nullable ISourceTile getMBDSource(Level level, BlockPos pos) {
        return getMBDSource(level.getBlockEntity(pos));
    }

    private static @Nullable ISourceTile getMBDSource(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof IMachineBlockEntity holder && holder.getMetaMachine() instanceof MBDMachine machine) {
            for (var trait : machine.getAdditionalTraits()) {
                if (trait instanceof ArsNouveauSourceCapabilityTrait sourceTrait) {
                    return sourceTrait.getRelaySource();
                }
            }
        }
        return null;
    }
}
