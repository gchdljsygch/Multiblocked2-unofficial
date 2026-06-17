package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineRemovedEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineSpawnedEvent;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDEntityMachineEventDispatcher;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base non-living entity implementation that owns an MBD entity machine.
 *
 * <p>The business goal is to bind a vanilla {@link Entity} lifecycle to an {@link MBDEntityMachine}: a virtual
 * {@link EntityMachineBlockEntity} supplies block-entity APIs, machine data is saved inside entity NBT, interaction is
 * routed through machine events/UI, and capabilities are exposed from the meta-machine. All lifecycle and capability
 * methods are expected to run on Minecraft's normal logical server/client thread.</p>
 */
public abstract class MBDMachineEntity extends Entity implements IMachineEntity {
    @Getter
    private final MultiManagedStorage rootStorage = new MultiManagedStorage();
    @Getter
    private final long offset = MBD2.RND.nextLong();
    private final EntityMachineBlockEntity machineHolder;
    @Getter
    private final IMachine metaMachine;
    @Getter
    private boolean machineLoaded;

    /**
     * Creates an entity-backed machine instance.
     *
     * <p>Side effects: creates the virtual block entity holder, creates the definition's meta-machine, and installs it
     * into the holder. The machine load hook is intentionally deferred until the first logical-side tick.</p>
     *
     * @param entityType vanilla entity type registered for this definition
     * @param level      level that owns the entity
     * @param definition entity machine definition used to create the meta-machine
     */
    protected MBDMachineEntity(EntityType<?> entityType, Level level, EntityMachineDefinition definition) {
        super(entityType, level);
        machineHolder = new EntityMachineBlockEntity(this);
        metaMachine = definition.createMachine(machineHolder);
        machineHolder.setMachine(metaMachine);
    }

    /**
     * Ticks vanilla entity behavior and then the attached machine.
     */
    @Override
    public void tick() {
        super.tick();
        tickMachine();
    }

    /**
     * Dispatches machine ticking to the correct logical side.
     *
     * <p>Side effects: may load the machine, post entity-machine tick events, run recipe/trait logic, and update client
     * render/sync state through {@link IMachineEntity#serverTickMachine()} or {@link IMachineEntity#clientTickMachine()}.</p>
     */
    protected void tickMachine() {
        if (level().isClientSide) {
            clientTickMachine();
        } else {
            serverTickMachine();
        }
    }

    /**
     * Routes player interaction through machine interaction hooks before vanilla handling.
     *
     * @param player interacting player
     * @param hand   hand used for interaction
     * @return machine result when handled, otherwise vanilla entity interaction result
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        var result = interactMachine(player, hand);
        return result == InteractionResult.PASS ? super.interact(player, hand) : result;
    }

    /**
     * Applies simple break-on-hit behavior for non-living machine entities.
     *
     * <p>When the entity is not invulnerable, the server drops machine trait contents and kills the entity. The method
     * returns {@code true} after accepting damage even though vanilla health is not tracked by this base class.</p>
     *
     * @param source damage source
     * @param amount requested damage amount
     * @return {@code false} when invulnerable to the source, otherwise {@code true}
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        markHurt();
        if (!level().isClientSide) {
            dropMachineContents();
            kill();
        }
        return true;
    }

    /**
     * Allows ray picking while the entity is alive.
     *
     * @return {@code true} for live entities
     */
    @Override
    public boolean isPickable() {
        return isAlive();
    }

    /**
     * Returns the block item represented by the entity machine definition.
     *
     * @return definition item stack for MBD machines, otherwise empty
     */
    @Nullable
    @Override
    public ItemStack getPickResult() {
        return metaMachine instanceof MBDMachine machine ? machine.getDefinition().asStack() : ItemStack.EMPTY;
    }

    /**
     * Removes the entity and unloads the attached machine.
     *
     * <p>Side effects on the logical server: posts {@link EntityMachineRemovedEvent} for entity machines and dispatches
     * either normal unload or chunk-unload behavior based on the removal reason.</p>
     *
     * @param reason vanilla removal reason
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && metaMachine instanceof MBDEntityMachine machine) {
            MinecraftForge.EVENT_BUS.post(new EntityMachineRemovedEvent(machine, this, reason).postCustomEvent());
        }
        removeMachine(reason);
        super.remove(reason);
    }

    /**
     * Posts the entity-machine spawned event after vanilla world attachment.
     *
     * <p>Side effects occur on the logical server only.</p>
     */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && metaMachine instanceof MBDEntityMachine machine) {
            MinecraftForge.EVENT_BUS.post(new EntityMachineSpawnedEvent(machine, this).postCustomEvent());
        }
    }

    /**
     * Updates the guard used to keep machine load/unload hooks idempotent.
     *
     * @param machineLoaded {@code true} after the load hook has run, {@code false} after unload
     */
    @Override
    public void setMachineLoaded(boolean machineLoaded) {
        this.machineLoaded = machineLoaded;
    }

    /**
     * Defines no additional synchronized entity data.
     *
     * <p>Machine sync state is managed by LowDragLib storage on the virtual holder rather than vanilla
     * {@code SynchedEntityData} fields.</p>
     */
    @Override
    protected void defineSynchedData() {
    }

    /**
     * Loads machine-managed persistent data from the entity NBT.
     *
     * @param tag source entity tag
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        loadMachineData(tag);
    }

    /**
     * Saves machine-managed persistent data into the entity NBT.
     *
     * @param tag destination entity tag
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        saveMachineData(tag);
    }

    /**
     * Exposes the MBD machine capability and delegates other capabilities to the meta-machine.
     *
     * @param capability requested Forge capability
     * @param facing     queried side, or {@code null} for side-independent access
     * @param <T>        capability value type
     * @return machine capability, machine-delegated capability, or vanilla entity capability
     */
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
