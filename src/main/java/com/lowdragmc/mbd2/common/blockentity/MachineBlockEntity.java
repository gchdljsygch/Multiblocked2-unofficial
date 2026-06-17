package com.lowdragmc.mbd2.common.blockentity;

import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A simple compound Interface for a BlockEntity which is holding a Machine feature.
 * <br>
 * Its using async system to sync data.
 */
public class MachineBlockEntity extends BlockEntity implements IMachineBlockEntity {
    @Getter
    public final MultiManagedStorage rootStorage = new MultiManagedStorage();
    @Getter
    private final long offset = MBD2.RND.nextLong();
    @Getter
    private IMachine metaMachine;

    /**
     * Creates a block entity and immediately attaches its machine facade.
     * <p>
     * The factory is invoked before the block entity has a level, so it must not depend on world access. Side effects:
     * stores the returned machine as this holder's active {@link IMachine}.
     *
     * @param type           registered block entity type
     * @param pos            world position of this block entity
     * @param blockState     current block state
     * @param machineFactory factory that creates the machine bound to this holder
     */
    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, Function<IMachineBlockEntity, IMachine> machineFactory) {
        super(type, pos, blockState);
        this.setMachine(machineFactory.apply(this));
    }

    /**
     * Replaces the machine facade owned by this block entity.
     * <p>
     * Replacing a live server-side machine calls {@link IMachine#onUnload()} on the old instance. If the previous machine
     * is an {@link MBDMachine}, it is also detached from this holder before the new machine is stored. The method is not
     * synchronized and should be called from the level thread or during controlled preview setup.
     *
     * @param newMachine machine facade to attach; passing the current instance is a no-op
     */
    public void setMachine(IMachine newMachine) {
        if (metaMachine == newMachine) return;
        if (metaMachine != null && level != null && !level.isClientSide) {
            metaMachine.onUnload();
        }
        if (metaMachine instanceof MBDMachine machine) {
            machine.detach();
        }
        metaMachine = newMachine;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        getMetaMachine().onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        getMetaMachine().onUnload();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        getMetaMachine().onLoad();
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
        return super.getCapability(cap, side);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (metaMachine instanceof MBDMachine machine) {
            var value = machine.getRenderBoundingBox();
            if (value != null) {
                return value;
            }
        }
        return super.getRenderBoundingBox();
    }
}
