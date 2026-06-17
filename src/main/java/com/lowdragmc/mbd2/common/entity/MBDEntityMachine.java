package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.common.gui.factory.EntityMachineUIFactory;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineOpenUIEvent;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.lowdragmc.mbd2.common.trait.entity.EntityHandlerTraitDefinition;
import com.lowdragmc.mbd2.common.trait.fluid.FluidTankCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.ForgeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.LongFeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTraitDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * {@link MBDMachine} specialization whose physical owner is an entity instead of a placed block.
 *
 * <p>The business goal is to reuse machine traits, UI, render state, drops, and scripting events for entity-backed
 * definitions while suppressing behavior that only makes sense for blocks, such as neighbor block updates and redstone
 * output. The holder is expected to be an {@link EntityMachineBlockEntity}; methods that read entity state must run on
 * the owning entity's normal logical client/server thread.</p>
 */
public class MBDEntityMachine extends MBDMachine {

    /**
     * Creates an entity machine attached to a virtual machine holder.
     *
     * @param machineHolder holder that bridges block-entity APIs to the owning entity
     * @param definition    entity machine definition used for rendering, UI, traits, and AI settings
     * @param args          optional definition-specific construction arguments
     */
    public MBDEntityMachine(IMachineBlockEntity machineHolder, EntityMachineDefinition definition, Object... args) {
        super(machineHolder, definition, args);
    }

    /**
     * Returns the entity-specific definition type.
     *
     * @return entity machine definition backing this runtime instance
     */
    @Override
    public EntityMachineDefinition getDefinition() {
        return (EntityMachineDefinition) super.getDefinition();
    }

    /**
     * Selects the renderer for the entity machine's current state.
     *
     * <p>A dynamic block renderer installed at runtime wins; otherwise the definition's entity-state renderer is used
     * for the current machine state and requested front face.</p>
     *
     * @param frontFacing direction the renderer should treat as the machine front
     * @return renderer for the current state
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public IRenderer getRealRenderer(Direction frontFacing) {
        var state = getMachineState();
        var blockRenderer = getDynamicBlockRenderer();
        if (blockRenderer == null) {
            return getDefinition().getEntityStateRenderer(state, frontFacing);
        }
        return blockRenderer;
    }

    /**
     * Returns the block-model location used when this entity machine is rendered as a model.
     *
     * @return model resource for the current entity-state renderer, or empty when no model renderer is available
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public Optional<ResourceLocation> getBlockModelLocationForRendering() {
        var renderer = getDynamicBlockRenderer();
        return getModelLocation(renderer == null ? getDefinition().getEntityStateBlockRenderer(getMachineState()) : renderer);
    }

    /**
     * Filters trait definitions before loading them on an entity machine.
     *
     * @param traitDefinition candidate trait definition
     * @return {@code true} when the entity definition supports the trait on entities
     */
    @Override
    protected boolean canLoadTrait(TraitDefinition traitDefinition) {
        return getDefinition().isTraitSupportedOnEntity(traitDefinition);
    }

    /**
     * Resolves front-facing from the owning entity when possible.
     *
     * @return entity-defined facing, falling back to the base machine holder state
     */
    @Override
    public Optional<Direction> getFrontFacing() {
        return entityHolderOptional()
                .flatMap(holder -> getDefinition().getEntityFrontFacing(holder.getMachineEntity().self()))
                .or(() -> super.getFrontFacing());
    }

    /**
     * Accepts any direction because entity definitions own facing validation.
     *
     * @param facing requested facing
     * @return always {@code true}
     */
    @Override
    public boolean isFacingValid(Direction facing) {
        return true;
    }

    /**
     * Stores front-facing on the owning entity when the entity holder is available.
     *
     * @param facing new facing direction
     */
    @Override
    public void setFrontFacing(Direction facing) {
        var holder = entityHolderOptional();
        if (holder.isPresent()) {
            getDefinition().setEntityFrontFacing(holder.get().getMachineEntity().self(), facing);
        } else {
            super.setFrontFacing(facing);
        }
    }

    /**
     * Suppresses block render update scheduling.
     *
     * <p>Entity render state is refreshed by the entity/rendering system rather than by block update packets.</p>
     */
    @Override
    public void scheduleRenderUpdate() {
    }

    /**
     * Suppresses vanilla neighbor block updates.
     */
    @Override
    public void notifyBlockUpdate() {
    }

    /**
     * Reacts to machine-state changes on the logical client.
     *
     * <p>Entity machines do not send block updates; the only immediate side effect here is playing the configured state
     * sound on clients.</p>
     *
     * @param newValue new machine-state id
     * @param oldValue previous machine-state id
     */
    @Override
    public void updateState(String newValue, String oldValue) {
        if (isRemote()) {
            playStateSound(newValue);
        }
    }

    /**
     * Suppresses redstone output recalculation for entity machines.
     */
    @Override
    public void updateSignal() {
    }

    /**
     * Entity machines do not expose block redstone connections.
     *
     * @param direction queried side
     * @return always {@code false}
     */
    @Override
    public boolean canConnectRedstone(Direction direction) {
        return false;
    }

    /**
     * Entity machines do not emit weak redstone power.
     *
     * @param direction queried side
     * @return always {@code 0}
     */
    @Override
    public int getOutputSignal(Direction direction) {
        return 0;
    }

    /**
     * Entity machines do not emit direct redstone power.
     *
     * @param direction queried side
     * @return always {@code 0}
     */
    @Override
    public int getOutputDirectSignal(Direction direction) {
        return 0;
    }

    /**
     * Entity machines are not dropped as their backing fake block item.
     *
     * @return empty stack; entity drops are produced by trait hooks instead
     */
    @Override
    public ItemStack getDropItem() {
        return ItemStack.EMPTY;
    }

    /**
     * Adds entity-machine drops from additional traits.
     *
     * @param entity entity being removed or killed
     * @param drops  mutable drop list receiving trait outputs
     */
    @Override
    public void onDrops(net.minecraft.world.entity.Entity entity, List<ItemStack> drops) {
        getAdditionalTraits().forEach(trait -> trait.onMachineDrop(entity, drops));
    }

    /**
     * Checks whether interaction should open the entity machine UI.
     *
     * @param hand interaction hand
     * @param hit  block hit result; usually {@code null} for direct entity interaction
     * @return definition UI flag
     */
    @Override
    public boolean shouldOpenUI(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return getDefinition().machineSettings().hasUI();
    }

    /**
     * Opens the entity machine UI for a server player.
     *
     * <p>Side effects: posts {@link MachineOpenUIEvent}; a canceled event returns pass and does not open the UI.
     * Client callers receive a sided success result but actual UI construction is server initiated.</p>
     *
     * @param player player requesting the UI
     * @return interaction result representing whether the request was accepted
     */
    @Override
    public InteractionResult openUI(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var event = new MachineOpenUIEvent(this, player);
            MinecraftForge.EVENT_BUS.post(event.postCustomEvent());
            if (event.isCanceled()) {
                return InteractionResult.PASS;
            }
            EntityMachineUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /**
     * Creates the modular UI for this entity machine.
     *
     * @param entityPlayer player who will view the UI
     * @return UI instance, or {@code null} when the base machine cannot create one
     */
    @Override
    public @Nullable ModularUI createUI(Player entityPlayer) {
        return super.createUI(entityPlayer);
    }

    /**
     * Keeps appearance queries from substituting neighboring block state.
     *
     * @param state      current fake block state
     * @param side       queried side
     * @param queryState querying block state
     * @param queryPos   querying position
     * @return unchanged state
     */
    @Override
    public BlockState getAppearance(BlockState state, Direction side, BlockState queryState, net.minecraft.core.BlockPos queryPos) {
        return state;
    }

    /**
     * Returns the required entity holder.
     *
     * @return virtual holder cast to {@link EntityMachineBlockEntity}
     * @throws ClassCastException if this machine was constructed with a non-entity holder
     */
    public EntityMachineBlockEntity entityHolder() {
        return (EntityMachineBlockEntity) getHolder();
    }

    /**
     * Returns the entity holder when this machine is attached to one.
     *
     * @return optional entity holder
     */
    public Optional<EntityMachineBlockEntity> entityHolderOptional() {
        return getHolder() instanceof EntityMachineBlockEntity holder ? Optional.of(holder) : Optional.empty();
    }

    /**
     * Returns the entity that owns this machine.
     *
     * @return backing machine entity
     */
    public com.lowdragmc.mbd2.api.entity.IMachineEntity getMachineEntity() {
        return entityHolder().getMachineEntity();
    }

    /**
     * Identifies built-in trait types that are safe on entity machines by default.
     *
     * <p>The allowed set covers inventory, fluid, Forge Energy/long FE, entity handling, and recipe thread traits.
     * Definitions can apply stricter policy through {@link EntityMachineDefinition#isTraitSupportedOnEntity(TraitDefinition)}.</p>
     *
     * @param traitDefinition trait definition being tested
     * @return {@code true} for default entity-compatible trait implementations
     */
    public static boolean isDefaultEntitySafeTrait(TraitDefinition traitDefinition) {
        return traitDefinition instanceof ItemSlotCapabilityTraitDefinition ||
                traitDefinition instanceof FluidTankCapabilityTraitDefinition ||
                traitDefinition instanceof ForgeEnergyCapabilityTraitDefinition ||
                traitDefinition instanceof LongFeEnergyCapabilityTraitDefinition ||
                traitDefinition instanceof EntityHandlerTraitDefinition ||
                traitDefinition instanceof RecipeThreadTraitDefinition;
    }
}
