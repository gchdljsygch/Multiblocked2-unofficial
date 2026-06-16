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

public class EntityMachineBlockEntity extends BlockEntity implements IMachineBlockEntity {
    private static final BlockState VIRTUAL_STATE = Blocks.BARRIER.defaultBlockState();
    private static final BlockEntityType<?> VIRTUAL_TYPE = BlockEntityType.BANNER;

    private final IMachineEntity machineEntity;
    @Getter
    private IMachine metaMachine;

    public EntityMachineBlockEntity(IMachineEntity machineEntity) {
        super(VIRTUAL_TYPE, machineEntity.self().blockPosition(), VIRTUAL_STATE);
        this.machineEntity = machineEntity;
    }

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

    public IMachineEntity getMachineEntity() {
        return machineEntity;
    }

    @Override
    public MultiManagedStorage getRootStorage() {
        return machineEntity.getRootStorage();
    }

    @Override
    public BlockEntity getSelf() {
        return this;
    }

    @Override
    public Level getLevel() {
        return machineEntity.self().level();
    }

    @Override
    public BlockPos getBlockPos() {
        return machineEntity.self().blockPosition();
    }

    @Override
    public BlockState getBlockState() {
        var block = MBDRegistries.FAKE_MACHINE().block();
        return block == null ? VIRTUAL_STATE : block.defaultBlockState();
    }

    @Override
    public void setChanged() {
        // Entity data is persisted by the entity itself.
    }

    @Override
    public boolean isRemoved() {
        return machineEntity.self().isRemoved();
    }

    @Override
    public long getOffset() {
        return machineEntity.getOffset();
    }

    @Override
    public Component getName() {
        return getCustomName() == null ? machineEntity.self().getName() : getCustomName();
    }

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

    @Override
    public AABB getRenderBoundingBox() {
        return machineEntity.self().getBoundingBoxForCulling();
    }
}
