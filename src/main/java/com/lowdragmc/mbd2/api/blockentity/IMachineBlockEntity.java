package com.lowdragmc.mbd2.api.blockentity;

import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IRPCBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Block entity contract for objects that own an {@link IMachine}.
 *
 * <p>The business goal is to combine LowDragLib async sync, RPC, persistence, and vanilla naming into one block
 * entity surface. Default helpers delegate lifecycle and NBT persistence to the meta-machine. Mutating methods should
 * be called on the logical server thread unless the underlying sync-data API documents otherwise.</p>
 */
public interface IMachineBlockEntity extends IAsyncAutoSyncBlockEntity, IRPCBlockEntity, IAutoPersistBlockEntity, Nameable {

    /**
     * Casts this interface to its block entity implementation.
     *
     * @return backing block entity
     */
    default BlockEntity self() {
        return (BlockEntity) this;
    }

    /**
     * Returns the backing block entity level.
     *
     * @return level, or {@code null} while detached
     */
    default Level level() {
        return self().getLevel();
    }

    /**
     * Returns the backing block entity position.
     *
     * @return block position
     */
    default BlockPos pos() {
        return self().getBlockPos();
    }

    /**
     * Notifies neighboring blocks that this machine block updated.
     *
     * <p>Side effects: calls vanilla neighbor update when attached to a level.</p>
     */
    default void notifyBlockUpdate() {
        if (level() != null) {
            level().updateNeighborsAt(pos(), level().getBlockState(pos()).getBlock());
        }
    }

    /**
     * Schedules a block render/update packet for clients.
     *
     * <p>Client side sends a block update locally; server side sends block event id {@code 1}. Detached block
     * entities are ignored.</p>
     */
    default void scheduleRenderUpdate() {
        var pos = pos();
        if (level() != null) {
            var state = level().getBlockState(pos);
            if (level().isClientSide) {
                level().sendBlockUpdated(pos, state, state, 1 << 3);
            } else {
                level().blockEvent(pos, state.getBlock(), 1, 0);
            }
        }
    }

    /**
     * Returns game time shifted by this block entity's stable offset.
     *
     * @return {@code level.getGameTime() + getOffset()} when attached, otherwise just {@link #getOffset()}
     */
    default long getOffsetTimer() {
        return level() == null ? getOffset() : (level().getGameTime() + getOffset());
    }

    /**
     * Returns the meta-machine owned by this block entity.
     *
     * @return non-null machine instance
     */
    IMachine getMetaMachine();

    /**
     * Returns the stable tick offset used to spread periodic work.
     *
     * @return offset value used by {@link #getOffsetTimer()}
     */
    long getOffset();

    /**
     * Saves LowDragLib-managed and machine-owned custom persisted data.
     *
     * @param tag     destination NBT tag
     * @param forDrop {@code true} when serializing into dropped item data
     */
    @Override
    default void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        IAutoPersistBlockEntity.super.saveCustomPersistedData(tag, forDrop);
        getMetaMachine().saveCustomPersistedData(tag, forDrop);
    }

    /**
     * Loads LowDragLib-managed and machine-owned custom persisted data.
     *
     * @param tag source NBT tag
     */
    @Override
    default void loadCustomPersistedData(CompoundTag tag) {
        IAutoPersistBlockEntity.super.loadCustomPersistedData(tag);
        getMetaMachine().loadCustomPersistedData(tag);
    }

    /**
     * Returns the display name shown by vanilla name-aware UI.
     *
     * @return custom machine name when present, otherwise the block name
     */
    @Override
    default Component getName() {
        return Objects.requireNonNullElse(getCustomName(), self().getBlockState().getBlock().getName());
    }

    /**
     * Returns the custom name provided by the meta-machine.
     *
     * @return custom name, or {@code null}
     */
    @Override
    @Nullable
    default Component getCustomName() {
        return getMetaMachine().getCustomName();
    }
}
