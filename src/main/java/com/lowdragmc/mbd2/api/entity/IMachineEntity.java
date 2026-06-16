package com.lowdragmc.mbd2.api.entity;

import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.MachineInteractionHelper;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineFixedTickEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineInteractEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineTickEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineRightClickEvent;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDEntityMachineEventDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;

public interface IMachineEntity {
    String MACHINE_DATA_TAG = "MBD2MachineData";

    default Entity self() {
        return (Entity) this;
    }

    MultiManagedStorage getRootStorage();

    IMachine getMetaMachine();

    long getOffset();

    boolean isMachineLoaded();

    void setMachineLoaded(boolean machineLoaded);

    default void loadMachine() {
        if (!isMachineLoaded()) {
            getMetaMachine().onLoad();
            setMachineLoaded(true);
        }
    }

    default void unloadMachine() {
        if (isMachineLoaded()) {
            getMetaMachine().onUnload();
            setMachineLoaded(false);
        }
    }

    default void chunkUnloadMachine() {
        if (isMachineLoaded()) {
            getMetaMachine().onChunkUnloaded();
            setMachineLoaded(false);
        }
    }

    default void removeMachine(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            chunkUnloadMachine();
        } else {
            unloadMachine();
        }
    }

    default InteractionResult interactMachine(Player player, InteractionHand hand) {
        if (getMetaMachine() instanceof MBDEntityMachine machine) {
            var entityEvent = new EntityMachineInteractEvent(machine, self(), player, hand);
            entityEvent.postCustomEvent();
            MinecraftForge.EVENT_BUS.post(entityEvent);
            if (entityEvent.getInteractionResult() != InteractionResult.PASS) {
                return entityEvent.getInteractionResult();
            }
            var event = new MachineRightClickEvent(machine, player, hand, null);
            event.setInteractionResult(InteractionResult.PASS);
            MinecraftForge.EVENT_BUS.post(event.postCustomEvent());
            if (event.getInteractionResult() != InteractionResult.PASS) {
                return event.getInteractionResult();
            }
            if (machine.shouldOpenUI(hand, null) &&
                    !MachineInteractionHelper.shouldBypassMachineUI(player.getItemInHand(hand))) {
                return machine.openUI(player);
            }
        }
        return InteractionResult.PASS;
    }

    default void dropMachineContents() {
        if (!(getMetaMachine() instanceof MBDEntityMachine machine)) return;
        var drops = new ArrayList<ItemStack>();
        machine.onDrops(self(), drops);
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            self().spawnAtLocation(stack);
        }
    }

    default void serverTickMachine() {
        loadMachine();
        if (getMetaMachine() instanceof com.lowdragmc.mbd2.common.machine.MBDMachine machine) {
            if (machine instanceof MBDEntityMachine entityMachine) {
                var event = new EntityMachineTickEvent(entityMachine, self());
                event.postCustomEvent();
                MinecraftForge.EVENT_BUS.post(event);
                if (event.isCanceled()) {
                    return;
                }
                postEntityFixedTickEvent(entityMachine);
            }
            machine.serverTick();
        }
    }

    default void postEntityFixedTickEvent(MBDEntityMachine machine) {
        var timer = machine.getOffsetTimer();
        var interval = Math.max(1, machine.getDefinition().entityAISettings().getFixedTickInterval());
        if (timer % interval == 0) {
            MinecraftForge.EVENT_BUS.post(new EntityMachineFixedTickEvent(machine, self(), interval, timer).postCustomEvent());
        }
        if (MBD2.isKubeJSLoaded()) {
            MBDEntityMachineEventDispatcher.postEntityMachineFixedTickEvery(machine, self(), timer);
        }
    }

    default void clientTickMachine() {
        loadMachine();
        if (getMetaMachine() instanceof com.lowdragmc.mbd2.common.machine.MBDMachine machine) {
            machine.clientTick();
        }
    }

    default void saveMachineData(CompoundTag tag) {
        var machineTag = new CompoundTag();
        ((IAutoPersistBlockEntity) getMetaMachine().getHolder()).saveManagedPersistentData(machineTag, false);
        tag.put(MACHINE_DATA_TAG, machineTag);
    }

    default void loadMachineData(CompoundTag tag) {
        if (tag.contains(MACHINE_DATA_TAG, CompoundTag.TAG_COMPOUND)) {
            ((IAutoPersistBlockEntity) getMetaMachine().getHolder()).loadManagedPersistentData(tag.getCompound(MACHINE_DATA_TAG));
        }
    }
}
