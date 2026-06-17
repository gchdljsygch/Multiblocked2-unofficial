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

/**
 * Entity contract for objects that own an MBD machine.
 *
 * <p>The business goal is to reuse machine logic on entity-backed machines: lifecycle hooks are forwarded to the
 * meta-machine, interaction events are posted through Forge and KubeJS integration points, and managed machine data
 * is stored inside the entity's NBT. Server/client tick helpers should be called from the owning entity tick on the
 * matching logical side.</p>
 */
public interface IMachineEntity {
    String MACHINE_DATA_TAG = "MBD2MachineData";

    /**
     * Casts this interface to its entity implementation.
     *
     * @return backing entity
     */
    default Entity self() {
        return (Entity) this;
    }

    /**
     * Returns the root sync-data storage for the entity machine.
     *
     * @return managed storage used by the machine holder
     */
    MultiManagedStorage getRootStorage();

    /**
     * Returns the meta-machine owned by this entity.
     *
     * @return machine instance
     */
    IMachine getMetaMachine();

    /**
     * Returns the stable tick offset used by the machine.
     *
     * @return offset value for periodic work distribution
     */
    long getOffset();

    /**
     * Reports whether the meta-machine has received its load callback.
     *
     * @return {@code true} after {@link #loadMachine()} until unload/removal
     */
    boolean isMachineLoaded();

    /**
     * Updates the loaded flag.
     *
     * @param machineLoaded new loaded state
     */
    void setMachineLoaded(boolean machineLoaded);

    /**
     * Runs the machine load hook once.
     */
    default void loadMachine() {
        if (!isMachineLoaded()) {
            getMetaMachine().onLoad();
            setMachineLoaded(true);
        }
    }

    /**
     * Runs the normal machine unload hook once.
     */
    default void unloadMachine() {
        if (isMachineLoaded()) {
            getMetaMachine().onUnload();
            setMachineLoaded(false);
        }
    }

    /**
     * Runs the chunk-unload hook once.
     */
    default void chunkUnloadMachine() {
        if (isMachineLoaded()) {
            getMetaMachine().onChunkUnloaded();
            setMachineLoaded(false);
        }
    }

    /**
     * Dispatches removal to chunk-unload or normal-unload behavior.
     *
     * @param reason vanilla entity removal reason
     */
    default void removeMachine(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            chunkUnloadMachine();
        } else {
            unloadMachine();
        }
    }

    /**
     * Handles player interaction with the entity machine.
     *
     * <p>Side effects: posts entity-machine and generic machine right-click events, may open the machine UI, and
     * returns the first non-pass interaction result supplied by events or UI opening.</p>
     *
     * @param player interacting player
     * @param hand   interaction hand
     * @return interaction result
     */
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

    /**
     * Drops machine-provided item contents at the entity position.
     */
    default void dropMachineContents() {
        if (!(getMetaMachine() instanceof MBDEntityMachine machine)) return;
        var drops = new ArrayList<ItemStack>();
        machine.onDrops(self(), drops);
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            self().spawnAtLocation(stack);
        }
    }

    /**
     * Ticks the entity machine on the logical server.
     *
     * <p>Side effects: ensures the machine is loaded, posts tick/fixed-tick events, and runs machine server tick
     * logic unless the tick event is canceled.</p>
     */
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

    /**
     * Posts fixed-interval entity machine events.
     *
     * @param machine entity-backed machine
     */
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

    /**
     * Ticks the entity machine on the logical client.
     *
     * <p>Side effects: ensures the machine is loaded and runs client tick logic for visual/sync state.</p>
     */
    default void clientTickMachine() {
        loadMachine();
        if (getMetaMachine() instanceof com.lowdragmc.mbd2.common.machine.MBDMachine machine) {
            machine.clientTick();
        }
    }

    /**
     * Saves machine managed persistent data into an entity tag.
     *
     * @param tag destination entity tag
     */
    default void saveMachineData(CompoundTag tag) {
        var machineTag = new CompoundTag();
        ((IAutoPersistBlockEntity) getMetaMachine().getHolder()).saveManagedPersistentData(machineTag, false);
        tag.put(MACHINE_DATA_TAG, machineTag);
    }

    /**
     * Loads machine managed persistent data from an entity tag.
     *
     * @param tag source entity tag
     */
    default void loadMachineData(CompoundTag tag) {
        if (tag.contains(MACHINE_DATA_TAG, CompoundTag.TAG_COMPOUND)) {
            ((IAutoPersistBlockEntity) getMetaMachine().getHolder()).loadManagedPersistentData(tag.getCompound(MACHINE_DATA_TAG));
        }
    }
}
