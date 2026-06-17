package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual block entity holder used by entity-backed machines.
 *
 * <p>The business goal is to let {@link MBDMachine} implementations reuse the same holder, sync-data, persistence,
 * capability, renderer, and naming APIs that block machines use while the actual owner is an {@link IMachineEntity}.
 * This holder is not placed in chunk block-entity storage; position, level, removal state, render bounds, and root
 * storage are delegated to the owning entity. Mutations are expected to happen on Minecraft's normal logical
 * server/client thread for the owning entity.</p>
 */
public class EntityMachineBlockEntity extends BlockEntity implements IMachineBlockEntity {
    private static final BlockState VIRTUAL_STATE = Blocks.BARRIER.defaultBlockState();
    private static final BlockEntityType<?> VIRTUAL_TYPE = BlockEntityType.BANNER;

    private final IMachineEntity machineEntity;
    @Getter
    private IMachine metaMachine;

    /**
     * Creates a virtual holder for an entity machine.
     *
     * <p>The superclass receives a harmless virtual type/state and the entity's current block position. Subsequent
     * level and position reads are delegated to the entity, so callers should not treat the constructor position as a
     * stable block placement.</p>
     *
     * @param machineEntity non-null entity that owns the meta-machine and persistent storage
     */
    public EntityMachineBlockEntity(IMachineEntity machineEntity) {
        super(VIRTUAL_TYPE, machineEntity.self().blockPosition(), VIRTUAL_STATE);
        this.machineEntity = machineEntity;
    }

    /**
     * Installs the machine exposed by this holder.
     *
     * <p>Side effects: when replacing an existing machine on the logical server, the owning entity receives an unload
     * callback first. Existing {@link MBDMachine} instances are detached from this holder before the new machine is
     * stored. Normal callers pass the machine created by the owning {@link IMachineEntity}'s definition.</p>
     *
     * @param newMachine machine to expose through {@link #getMetaMachine()} and Forge capabilities
     */
    public void setMachine(IMachine newMachine) {
        if (metaMachine == newMachine) return;
        if (metaMachine != null && getLevel() != null && !getLevel().isClientSide) {
            machineEntity.unloadMachine();
        }
        if (metaMachine instanceof MBDMachine machine) {
            machine.detach();
        }
        metaMachine = newMachine;
    }

    /**
     * Returns the entity that owns this virtual holder.
     *
     * @return backing machine entity
     */
    public IMachineEntity getMachineEntity() {
        return machineEntity;
    }

    /**
     * Delegates LowDragLib managed storage to the entity.
     *
     * @return root storage persisted and synchronized through the entity machine contract
     */
    @Override
    public MultiManagedStorage getRootStorage() {
        return machineEntity.getRootStorage();
    }

    /**
     * Returns this virtual holder as its block entity identity.
     *
     * @return this block entity
     */
    @Override
    public BlockEntity getSelf() {
        return this;
    }

    /**
     * Returns the level of the owning entity.
     *
     * @return current entity level
     */
    @Override
    public Level getLevel() {
        return machineEntity.self().level();
    }

    /**
     * Returns the owning entity's current block position.
     *
     * @return dynamic entity block position
     */
    @Override
    public BlockPos getBlockPos() {
        return machineEntity.self().blockPosition();
    }

    /**
     * Returns a harmless visible block state for APIs that require one.
     *
     * <p>When the fake machine block is registered it is used so render/capability queries see the expected MBD block
     * identity; otherwise a barrier state is returned as a safe fallback.</p>
     *
     * @return fake machine block state or virtual fallback state
     */
    @Override
    public BlockState getBlockState() {
        var block = MBDRegistries.FAKE_MACHINE().block();
        return block == null ? VIRTUAL_STATE : block.defaultBlockState();
    }

    /**
     * Ignores vanilla block-entity dirty marking.
     *
     * <p>Entity-backed machine data is saved by the entity's NBT path, so there is no chunk block entity entry to mark
     * dirty.</p>
     */
    @Override
    public void setChanged() {
        // Entity data is persisted by the entity itself.
    }

    /**
     * Mirrors the owning entity removal state.
     *
     * @return {@code true} when the backing entity has been removed
     */
    @Override
    public boolean isRemoved() {
        return machineEntity.self().isRemoved();
    }

    /**
     * Delegates the stable tick offset to the entity.
     *
     * @return offset used for spreading periodic machine work
     */
    @Override
    public long getOffset() {
        return machineEntity.getOffset();
    }

    /**
     * Returns the display name used by UI and vanilla name-aware calls.
     *
     * @return custom machine name when present, otherwise the entity's current name
     */
    @Override
    public Component getName() {
        return getCustomName() == null ? machineEntity.self().getName() : getCustomName();
    }

    /**
     * Exposes the meta-machine and machine-provided capabilities.
     *
     * <p>The machine capability always resolves to {@link #getMetaMachine()}. Other Forge capabilities are delegated to
     * an attached {@link MBDMachine}; unsupported capabilities return an empty optional because there is no real
     * superclass block entity inventory or energy storage to query.</p>
     *
     * @param cap  requested Forge capability
     * @param side side of the virtual block, or {@code null} for side-independent access
     * @param <T>  capability value type
     * @return lazy optional containing the requested capability when available
     */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MBDCapabilities.CAPABILITY_MACHINE) {
            return MBDCapabilities.CAPABILITY_MACHINE.orEmpty(cap, LazyOptional.of(this::getMetaMachine));
        }
        if (metaMachine instanceof MBDMachine machine) {
            var result = machine.getCapability(cap, side);
            if (result.isPresent()) return result;
        }
        return LazyOptional.empty();
    }

    /**
     * Uses the entity's culling bounds for machine rendering.
     *
     * @return render bounding box covering the owning entity
     */
    @Override
    public AABB getRenderBoundingBox() {
        return machineEntity.self().getBoundingBoxForCulling();
    }
}
