package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDEntityMachineEventDispatcher;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MBDLivingMachineEntity extends LivingEntity implements IMachineEntity {
    @Getter
    private final MultiManagedStorage rootStorage = new MultiManagedStorage();
    @Getter
    private final long offset = MBD2.RND.nextLong();
    private final EntityMachineBlockEntity machineHolder;
    @Getter
    private final IMachine metaMachine;
    @Getter
    private boolean machineLoaded;

    protected MBDLivingMachineEntity(EntityType<? extends LivingEntity> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level);
        machineHolder = new EntityMachineBlockEntity(this);
        metaMachine = definition.createMachine(machineHolder);
        machineHolder.setMachine(metaMachine);
    }

    @Override
    public LivingEntity self() {
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        tickMachine();
    }

    protected void tickMachine() {
        if (level().isClientSide) {
            clientTickMachine();
        } else {
            serverTickMachine();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        var result = interactMachine(player, hand);
        return result == InteractionResult.PASS ? super.interact(player, hand) : result;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        dropMachineContents();
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return metaMachine instanceof MBDMachine machine ? machine.getDefinition().asStack() : ItemStack.EMPTY;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && MBD2.isKubeJSLoaded() && metaMachine instanceof MBDEntityMachine machine) {
            MBDEntityMachineEventDispatcher.postRemoved(machine, this, reason);
        }
        removeMachine(reason);
        super.remove(reason);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && MBD2.isKubeJSLoaded() && metaMachine instanceof MBDEntityMachine machine) {
            MBDEntityMachineEventDispatcher.postSpawned(machine, this);
        }
    }

    @Override
    public void setMachineLoaded(boolean machineLoaded) {
        this.machineLoaded = machineLoaded;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadMachineData(tag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveMachineData(tag);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
        if (capability == MBDCapabilities.CAPABILITY_MACHINE) {
            return MBDCapabilities.CAPABILITY_MACHINE.orEmpty(capability, LazyOptional.of(this::getMetaMachine));
        }
        if (metaMachine instanceof MBDMachine machine) {
            var result = machine.getCapability(capability, facing);
            if (result.isPresent()) return result;
        }
        return super.getCapability(capability, facing);
    }
}
