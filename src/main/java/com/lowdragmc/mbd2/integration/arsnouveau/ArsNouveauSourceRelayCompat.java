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

/**
 * Bridge used by the Ars Nouveau relay mixin to recognize MBD Source traits.
 *
 * <p>The helper treats native Ars Nouveau source machines and MBD machines with
 * {@link ArsNouveauSourceCapabilityTrait} as compatible relay endpoints. Lookups are read-only
 * except for normal block-entity retrieval and should be called on the logical server.</p>
 */
public final class ArsNouveauSourceRelayCompat {
    private ArsNouveauSourceRelayCompat() {
    }

    /**
     * Checks whether a position is an MBD machine exposing Source through a trait.
     *
     * @param level level containing the candidate block entity
     * @param pos candidate position
     * @return {@code true} when the position resolves to an MBD Source trait
     */
    public static boolean isMBDSource(Level level, BlockPos pos) {
        return getMBDSource(level, pos) != null;
    }

    /**
     * Resolves either a native Ars Nouveau source machine or an MBD Source wrapper.
     *
     * @param level level containing the candidate block entity
     * @param pos candidate position
     * @return Source endpoint, or {@code null} when the block cannot provide Source
     */
    public static @Nullable ISourceTile getSource(Level level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbstractSourceMachine sourceMachine) {
            return sourceMachine;
        }
        return getMBDSource(blockEntity);
    }

    /**
     * Resolves only MBD Source traits at a position.
     */
    private static @Nullable ISourceTile getMBDSource(Level level, BlockPos pos) {
        return getMBDSource(level.getBlockEntity(pos));
    }

    /**
     * Finds the first Ars Nouveau Source trait on an MBD machine block entity.
     */
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
