package com.lowdragmc.mbd2.common.block;

import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IBlockRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.MachineInteractionHelper;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import lombok.Getter;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;

/**
 * Vanilla block facade for a definition-backed MBD machine.
 *
 * <p>The business goal is to keep the registered block small and route machine-specific behavior to the
 * {@link MBDMachine} stored in the block entity: ticking, placement/removal hooks, shape/render selection, redstone,
 * drops, UI opening, and waterlogging all use the active {@link MBDMachineDefinition}. Instances are effectively
 * immutable after registration; the attached machine and block entity state are mutable and must be accessed from the
 * normal Minecraft logical server/client thread.</p>
 */
@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MBDMachineBlock extends Block implements EntityBlock, IBlockRendererProvider, SimpleWaterloggedBlock {

    private final MBDMachineDefinition definition;
    private final RotationState rotationState;

    /**
     * Creates a machine block for the supplied definition.
     *
     * <p>Side effects: registers the definition's default facing property when the rotation state exposes one and
     * registers the vanilla {@link BlockStateProperties#WATERLOGGED} property when the definition allows
     * waterlogging. The same properties must be present in {@link #createBlockStateDefinition(StateDefinition.Builder)}
     * during vanilla block construction.</p>
     *
     * @param properties vanilla block properties used for hardness, sound, collision, and related block behavior
     * @param definition non-null machine definition that supplies renderers, block entity type, rotation rules, and
     *                   interaction policy
     */
    public MBDMachineBlock(Properties properties, MBDMachineDefinition definition) {
        super(properties);
        this.definition = definition;
        this.rotationState = definition.blockProperties().rotationState();
        rotationState.property.ifPresent(property -> registerDefaultState(defaultBlockState().setValue(property, rotationState.defaultDirection)));
        if (definition.blockProperties().canBeWaterlogged()) {
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
        }
    }

    /**
     * Reads the facing encoded in a block state.
     *
     * @param state state belonging to this block
     * @return current front face when the definition has a rotation property, otherwise {@link Optional#empty()}
     */
    public Optional<Direction> getFrontFacing(BlockState state) {
        return rotationState.property.map(state::getValue);
    }

    /**
     * Creates the block entity registered by the machine definition.
     *
     * @param pos   block position being instantiated
     * @param state placed block state
     * @return new machine block entity, or {@code null} if the registered type refuses creation
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return getDefinition().blockEntityType().create(pos, state);
    }

    /**
     * Provides the per-tick bridge from vanilla block entity ticking into {@link MBDMachine}.
     *
     * <p>The ticker is only returned for the definition's exact block entity type. On each tick it resolves the machine
     * from the block entity and calls {@link MBDMachine#clientTick()} on the logical client or
     * {@link MBDMachine#serverTick()} on the logical server. No work is performed for non-MBD holders.</p>
     *
     * @param level           level that owns the block entity
     * @param state           current block state
     * @param blockEntityType candidate block entity type requested by vanilla
     * @param <T>             block entity type parameter from vanilla ticker lookup
     * @return ticker for matching machine block entities, otherwise {@code null}
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (blockEntityType == getDefinition().blockEntityType()) {
            return (world, pos, state1, blockEntity) -> {
                IMachine.ofMachine(blockEntity).filter(MBDMachine.class::isInstance).map(MBDMachine.class::cast).ifPresent(machine -> {
                    if (world.isClientSide) {
                        machine.clientTick();
                    } else {
                        machine.serverTick();
                    }
                });
            };
        }
        return null;
    }

    /**
     * Adds dynamic state properties required by the active machine definition.
     *
     * <p>Vanilla calls this while constructing a block. The definition is read from the registration context so the
     * rotation and waterlogging properties match the defaults registered by the constructor.</p>
     *
     * @param builder state definition builder being populated
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        var rotationState = MBDMachineDefinition.get().blockProperties().rotationState();
        rotationState.property.ifPresent(builder::add);
        if (MBDMachineDefinition.get().blockProperties().canBeWaterlogged()) {
            builder.add(BlockStateProperties.WATERLOGGED);
        }
    }

    /**
     * Returns the renderer configured for this definition.
     *
     * @param state current block state
     * @return renderer used by LowDragLib, or {@code null} when the definition falls back to normal model rendering
     */
    @Nullable
    @Override
    public IRenderer getRenderer(BlockState state) {
        return definition.blockRenderer();
    }

    /**
     * Converts the block's front-facing state into a model transform for client rendering.
     *
     * @param world client block view
     * @param pos   rendered block position
     * @param state rendered block state
     * @return model rotation matching the block facing, defaulting to north for non-rotating definitions
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public ModelState getModelState(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        return ModelFactory.getRotation(getRotationState().property.map(state::getValue).orElse(Direction.NORTH));
    }

    /**
     * Resolves the MBD machine stored at a world position.
     *
     * @param level block/entity access used for lookup
     * @param pos   machine block position
     * @return machine when the block entity implements the expected MBD machine contract, otherwise empty
     */
    public Optional<MBDMachine> getMachine(BlockGetter level, BlockPos pos) {
        return IMachine.ofMachine(level, pos).filter(MBDMachine.class::isInstance).map(MBDMachine.class::cast);
    }

    /**
     * Returns the collision/selection shape supplied by the live machine.
     *
     * @param pState   current block state
     * @param pLevel   block access for resolving the machine
     * @param pPos     machine block position
     * @param pContext collision context, usually the querying entity
     * @return machine-specific shape, or a full cube while no machine is attached
     */
    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return getMachine(pLevel, pPos).map(machine -> machine.getShape(pContext)).orElse(Shapes.block());
    }

    /**
     * Runs client-side ambient effects for the machine.
     *
     * @param state  current block state
     * @param level  client level
     * @param pos    machine block position
     * @param random random source for particle/sound effects
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        getMachine(level, pos).ifPresent(machine -> machine.animateTick(random));
    }

    /**
     * Reports whether vanilla should treat the collision shape as a full cube.
     *
     * @param state current block state
     * @param level block access
     * @param pos   block position
     * @return definition-level full-block collision flag
     */
    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return definition.blockProperties().collisionShapeFullBlock();
    }

    /**
     * Notifies the machine that its block has been placed.
     *
     * <p>Side effects occur only on the logical server. The machine can initialize owner, copied item data, inventory,
     * or traits from the placing entity and stack.</p>
     *
     * @param pLevel server/client level
     * @param pPos   placed position
     * @param pState placed block state
     * @param player living entity that placed the block, or {@code null} for non-entity placement
     * @param pStack item stack used for placement
     */
    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity player, ItemStack pStack) {
        if (!pLevel.isClientSide) {
            getMachine(pLevel, pPos).ifPresent(machine -> machine.onMachinePlaced(player, pStack));
        }
    }

    /**
     * Computes the initial block state for placement.
     *
     * <p>The returned state preserves waterlogged placement when enabled by the definition and chooses the facing from
     * the player's relative position. Vertical directions are selected when the player is close enough to the block
     * column and the rotation state permits up/down facing; otherwise horizontal-facing blocks face opposite the player,
     * while Y-axis-only definitions face up.</p>
     *
     * @param context vanilla placement context
     * @return initial state, or {@code null} if vanilla placement should fail
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RotationState rotationState = getRotationState();
        var player = context.getPlayer();
        var blockPos = context.getClickedPos();
        BlockState state;
        if (getDefinition().blockProperties().canBeWaterlogged()) {
            state = defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        } else {
            state = defaultBlockState();
        }
        return player == null ? state : rotationState.property.map(property -> {
            Vec3 pos = player.position();
            if (Math.abs(pos.x - (double) ((float) blockPos.getX() + 0.5F)) < 2.0D && Math.abs(pos.z - (double) ((float) blockPos.getZ() + 0.5F)) < 2.0D) {
                double d0 = pos.y + (double) player.getEyeHeight();
                if (d0 - (double) blockPos.getY() > 2.0D && rotationState.test(Direction.UP)) {
                    return state.setValue(property, Direction.UP);
                }
                if ((double) blockPos.getY() - d0 > 0.0D && rotationState.test(Direction.DOWN)) {
                    return state.setValue(property, Direction.DOWN);
                }
            }
            if (rotationState == RotationState.Y_AXIS) {
                return state.setValue(property, Direction.UP);
            } else {
                return state.setValue(property, player.getDirection().getOpposite());
            }
        }).orElse(state);
    }

    /**
     * Checks whether a fluid can be inserted by vanilla waterlogging mechanics.
     *
     * @param pLevel block access
     * @param pPos   target position
     * @param pState current state
     * @param pFluid fluid being inserted
     * @return {@code true} only for definition-enabled waterlogging accepted by vanilla
     */
    @Override
    public boolean canPlaceLiquid(BlockGetter pLevel, BlockPos pPos, BlockState pState, Fluid pFluid) {
        if (getDefinition().blockProperties().canBeWaterlogged()) {
            return SimpleWaterloggedBlock.super.canPlaceLiquid(pLevel, pPos, pState, pFluid);
        }
        return false;
    }

    /**
     * Inserts fluid into the block when waterlogging is enabled.
     *
     * @param levelAccessor mutable level access
     * @param blockPos      target block position
     * @param blockState    current block state
     * @param fluidState    fluid state being placed
     * @return {@code true} when vanilla waterlogging accepted the fluid
     */
    @Override
    public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
        if (getDefinition().blockProperties().canBeWaterlogged()) {
            return SimpleWaterloggedBlock.super.placeLiquid(levelAccessor, blockPos, blockState, fluidState);
        }
        return false;
    }

    /**
     * Removes water from a waterlogged machine block.
     *
     * @param levelAccessor mutable level access
     * @param blockPos      target block position
     * @param blockState    current block state
     * @return picked-up fluid stack, or {@link ItemStack#EMPTY} when waterlogging is disabled or absent
     */
    @Override
    public ItemStack pickupBlock(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState) {
        if (getDefinition().blockProperties().canBeWaterlogged()) {
            return SimpleWaterloggedBlock.super.pickupBlock(levelAccessor, blockPos, blockState);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the vanilla bucket pickup sound for waterlogged machine blocks.
     *
     * @return pickup sound when waterlogging is enabled, otherwise empty
     */
    @Override
    public Optional<SoundEvent> getPickupSound() {
        if (getDefinition().blockProperties().canBeWaterlogged()) {
            return SimpleWaterloggedBlock.super.getPickupSound();
        }

        return Optional.empty();
    }

    /**
     * Exposes the fluid state represented by the optional waterlogged property.
     *
     * @param state current block state
     * @return source water when waterlogged, otherwise the superclass fluid state
     */
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * Keeps waterlogged blocks scheduled for fluid ticks when neighboring shapes update.
     *
     * @param state       current block state
     * @param facing      direction of the neighbor update
     * @param facingState neighboring block state
     * @param world       mutable level access
     * @param pos         current position
     * @param facingPos   neighbor position
     * @return unchanged block state
     */
    @Override
    @Deprecated
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos) {
        if (state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return state;
    }

    /**
     * Adds definition-provided tooltip lines to the block item.
     *
     * @param stack   item stack being inspected
     * @param level   tooltip world context, or {@code null} outside a level
     * @param tooltip mutable tooltip list receiving display components
     * @param flag    vanilla tooltip detail flag
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        definition.appendHoverText(stack, tooltip);
    }

    /**
     * Forwards vanilla block events to the block entity.
     *
     * @param pState block state at the event position
     * @param pLevel level that received the event
     * @param pPos   event position
     * @param pId    vanilla event id
     * @param pParam vanilla event parameter
     * @return {@code true} when the block entity handled the event
     */
    @Override
    public boolean triggerEvent(BlockState pState, Level pLevel, BlockPos pPos, int pId, int pParam) {
        BlockEntity tile = pLevel.getBlockEntity(pPos);
        if (tile != null) {
            return tile.triggerEvent(pId, pParam);
        }
        return false;
    }

    /**
     * Rotates the front-facing block state property when the definition has one.
     *
     * @param state    state being rotated
     * @param rotation vanilla rotation transform
     * @return rotated state, or the original state for non-rotating definitions
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return rotationState.property.map(property -> state.setValue(property, rotation.rotate(state.getValue(property)))).orElse(state);
    }

    /**
     * Lets the machine and traits mutate vanilla block drops.
     *
     * <p>Side effects: creates a loot context from the builder, computes the normal block drops, then gives the attached
     * {@link MBDMachine} a chance to add, remove, or rewrite stacks. The entity that caused the drop is passed through
     * when vanilla supplied one.</p>
     *
     * @param state   destroyed block state
     * @param builder loot context builder supplied by vanilla
     * @return mutable drop list after machine hooks have run
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var context = builder.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        BlockEntity tileEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        var drops = super.getDrops(state, builder);
        IMachine.ofMachine(tileEntity).filter(MBDMachine.class::isInstance).map(MBDMachine.class::cast).ifPresent(machine -> machine.onDrops(entity, drops));
        return drops;
    }

    /**
     * Handles block replacement and rotation lifecycle hooks.
     *
     * <p>Side effects: when this block is replaced by another block, the machine receives its removal hook, neighboring
     * redstone is updated, and the block entity is removed. When the same block remains but its facing property changes,
     * the machine receives a rotation callback instead. This method must run on the vanilla level thread.</p>
     *
     * @param pState    old block state
     * @param pLevel    level containing the block
     * @param pPos      block position
     * @param pNewState replacement state
     * @param pIsMoving vanilla moving flag
     */
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.hasBlockEntity()) {
            if (!pState.is(pNewState.getBlock())) { // new block
                getMachine(pLevel, pPos).ifPresent(MBDMachine::onMachineRemoved);
                pLevel.updateNeighbourForOutputSignal(pPos, this);
                pLevel.removeBlockEntity(pPos);
            } else if (rotationState.property.isPresent()) { // old block different facing
                var oldFacing = pState.getValue(rotationState.property.get());
                var newFacing = pNewState.getValue(rotationState.property.get());
                if (newFacing != oldFacing) {
                    getMachine(pLevel, pPos).ifPresent(machine -> machine.onRotated(oldFacing, newFacing));
                }
            }
        }
    }

    /**
     * Reads dynamic light emission from the machine state.
     *
     * @param state current block state
     * @param level block access
     * @param pos   machine block position
     * @return light level in vanilla's {@code 0..15} range, or {@code 0} while no machine is attached
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return getMachine(level, pos).map(machine -> machine.getMachineState().getLightLevel()).orElse(0);
    }

    /**
     * Handles right-click interaction with the machine block.
     *
     * <p>The machine receives first chance through {@link MBDMachine#onUse(BlockState, Level, BlockPos, Player,
     * InteractionHand, BlockHitResult)}. If it passes and the definition wants a UI, the UI is opened unless the held
     * stack is registered as a bypass tool. Server-side UI opening and event posting are delegated to the machine.</p>
     *
     * @param state  current block state
     * @param world  level containing the block
     * @param pos    block position
     * @param player interacting player
     * @param hand   hand used for interaction
     * @param hit    exact hit result
     * @return first non-pass machine/UI result, otherwise {@link InteractionResult#PASS}
     */
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var machine = getMachine(world, pos).orElse(null);
        if (machine == null) return InteractionResult.PASS;
        var result = machine.onUse(state, world, pos, player, hand, hit);
        if (result != InteractionResult.PASS) return result;
        if (machine.shouldOpenUI(hand, hit) && !MachineInteractionHelper.shouldBypassMachineUI(player.getItemInHand(hand))) {
            return machine.openUI(player);
        }
        return InteractionResult.PASS;
    }

    /**
     * Delegates redstone-connectivity checks to the machine.
     *
     * @param state     current block state
     * @param level     block access
     * @param pos       machine block position
     * @param direction side queried by vanilla, or {@code null}
     * @return machine policy for a concrete side, otherwise superclass behavior
     */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        var machine = getMachine(level, pos).orElse(null);
        if (machine == null || direction == null) return super.canConnectRedstone(state, level, pos, direction);
        return machine.canConnectRedstone(direction);
    }

    /**
     * Returns the weak redstone signal emitted from the requested side.
     *
     * @param state     current block state
     * @param level     block access
     * @param pos       machine block position
     * @param direction side requested by vanilla
     * @return signal strength in vanilla's {@code 0..15} range
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        var machine = getMachine(level, pos).orElse(null);
        if (machine == null) return super.getSignal(state, level, pos, direction);
        // For some reason, Minecraft requests the output signal from the opposite side...
        return machine.getOutputSignal(direction.getOpposite());
    }

    /**
     * Returns the direct redstone signal emitted toward the requested side.
     *
     * @param state     current block state
     * @param level     block access
     * @param pos       machine block position
     * @param direction side requested by vanilla
     * @return direct signal strength in vanilla's {@code 0..15} range
     */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        var machine = getMachine(level, pos).orElse(null);
        if (machine == null) return super.getDirectSignal(state, level, pos, direction);
        return machine.getOutputDirectSignal(direction);
    }

    /**
     * Returns the comparator output for this machine block.
     *
     * @param state current block state
     * @param level level containing the block
     * @param pos   machine block position
     * @return comparator strength in vanilla's {@code 0..15} range
     */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        var machine = getMachine(level, pos).orElse(null);
        if (machine == null) return super.getAnalogOutputSignal(state, level, pos);
        return machine.getAnalogOutputSignal();
    }

    /**
     * Notifies the machine that a neighboring block changed.
     *
     * @param state    current block state
     * @param level    level containing the block
     * @param pos      machine block position
     * @param block    neighbor block that changed
     * @param fromPos  changed neighbor position
     * @param isMoving vanilla moving flag
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        getMachine(level, pos).ifPresent(machine -> machine.onNeighborChanged(block, fromPos, isMoving));
    }

    /**
     * Lets the machine choose the block appearance used for connected rendering queries.
     *
     * @param state      current block state
     * @param level      block/tint access
     * @param pos        machine block position
     * @param side       queried side
     * @param queryState state of the querying block, or {@code null}
     * @param queryPos   position of the querying block, or {@code null}
     * @return machine-selected appearance, otherwise the current state
     */
    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        return getMachine(level, pos).map(machine -> machine.getAppearance(state, side, queryState, queryPos)).orElse(state);
    }

    /**
     * Controls skylight propagation for transparent and waterlogged machine blocks.
     *
     * @param state current block state
     * @param level block access
     * @param pos   block position
     * @return {@code true} for transparent definitions or non-waterlogged states
     */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return getDefinition().blockProperties().transparent() || !(state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false));
    }

    /**
     * Removes the visual occlusion shape for transparent machine blocks.
     *
     * @param state current block state
     * @param level block access
     * @param pos   block position
     * @param ctx   collision context
     * @return empty shape for transparent definitions, otherwise superclass visual shape
     */
    @Override
    @Deprecated
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getDefinition().blockProperties().transparent() ? Shapes.empty() : super.getVisualShape(state, level, pos, ctx);
    }

    /**
     * Keeps transparent machine blocks from receiving vanilla darkening.
     *
     * @param state current block state
     * @param level block access
     * @param pos   block position
     * @return full brightness for transparent definitions, otherwise superclass brightness
     */
    @Override
    @Deprecated
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return getDefinition().blockProperties().transparent() ? 1F : super.getShadeBrightness(state, level, pos);
    }

    /**
     * Skips rendering faces between adjacent transparent machine blocks.
     *
     * @param state     current block state
     * @param state2    adjacent block state
     * @param direction shared face direction
     * @return transparent-definition face skipping policy, otherwise superclass behavior
     */
    @Override
    @Deprecated
    public boolean skipRendering(BlockState state, BlockState state2, Direction direction) {
        return getDefinition().blockProperties().transparent() ? (state2.is(this) || super.skipRendering(state, state2, direction)) : super.skipRendering(state, state2, direction);
    }


    /**
     * Computes the packed light map value used by LowDragLib rendering.
     *
     * @param world block/tint access
     * @param state rendered state
     * @param pos   rendered position
     * @return packed sky/block light, or {@link LightTexture#FULL_BRIGHT} for emissive rendering
     */
    @Override
    public int getLightMap(BlockAndTintGetter world, BlockState state, BlockPos pos) {
        if (state.emissiveRendering(world, pos)) {
            return LightTexture.FULL_BRIGHT;
        } else {
            int i = world.getBrightness(LightLayer.SKY, pos);
            int j = world.getBrightness(LightLayer.BLOCK, pos);
            int k = state.getLightEmission(world, pos);
            if (j < k) {
                j = k;
            }
            return i << 20 | j << 4;
        }
    }
}
