package com.lowdragmc.mbd2.common.item;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.CombinedBlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.error.SinglePredicateError;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateStates;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.common.autobuild.AutoBuildPlacementExecutor;
import com.lowdragmc.mbd2.common.autobuild.SlowMultiblockDisassemblyScheduler;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import com.lowdragmc.mbd2.utils.MultiFluidHandler;
import com.lowdragmc.mbd2.utils.MultiItemHandler;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standalone tool that dismantles a controller's currently matching multiblock
 * pattern into bound storage or the player's inventory.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockDisassemblyToolItem extends Item {
    private static final String TAG_BOUND_DIM = "mbd2_disassembly_bound_dim";
    private static final String TAG_BOUND_POS = "mbd2_disassembly_bound_pos";
    private static final String TAG_BOUND_SOURCE_TYPE = "mbd2_disassembly_bound_source_type";
    private static final String TAG_SLOW = "mbd2_disassembly_slow";
    private static final String SOURCE_TYPE_ME = "me";
    private static final Direction[] HORIZONTAL_FACINGS = {Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST};
    private static final int MAX_PARTIAL_PATTERN_VARIANTS = 64;

    private boolean handledBlockUse;

    public MultiblockDisassemblyToolItem() {
        super(new Item.Properties().fireResistant().stacksTo(1));
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return isSlowMode(stack)
                ? "item.mbd2.mbd_disassembly_tool.slow"
                : "item.mbd2.mbd_disassembly_tool.fast";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        components.add(Component.translatable("item.mbd2.mbd_disassembly_tool.tooltip.0").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("item.mbd2.mbd_disassembly_tool.tooltip.1").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable(isSlowMode(stack)
                ? "item.mbd2.mbd_disassembly_tool.tooltip.mode.slow"
                : "item.mbd2.mbd_disassembly_tool.tooltip.mode.fast").withStyle(ChatFormatting.DARK_GREEN));
        BoundPos bound = readBoundPos(stack);
        if (bound != null) {
            BlockPos pos = bound.pos();
            String tooltipKey = isBoundSourceME(stack)
                    ? "item.mbd2.mbd_disassembly_tool.tooltip.bound.me"
                    : "item.mbd2.mbd_disassembly_tool.tooltip.bound";
            components.add(Component.translatable(tooltipKey,
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (handledBlockUse) {
            handledBlockUse = false;
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!player.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            boolean slow = !isSlowMode(stack);
            setSlowMode(stack, slow);
            serverPlayer.displayClientMessage(Component.translatable(slow
                    ? "item.mbd2.mbd_disassembly_tool.mode.slow"
                    : "item.mbd2.mbd_disassembly_tool.mode.fast"), true);
            player.getInventory().setChanged();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.isCrouching()) {
            handledBlockUse = true;
            return bindStorage(stack, context, serverPlayer);
        }
        IMultiController controller = IMultiController.ofController(context.getLevel(), context.getClickedPos()).orElse(null);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        handledBlockUse = true;
        disassembleController(stack, serverPlayer, controller);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult bindStorage(ItemStack stack, UseOnContext context, ServerPlayer player) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.bind.failure"), true);
            return InteractionResult.SUCCESS;
        }

        if (BuilderMaterialBindings.hasMEItemStorage(be)) {
            writeBoundPos(stack, level, pos, true);
            player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.bind.me.success",
                    pos.getX(), pos.getY(), pos.getZ()), true);
            player.getInventory().setChanged();
            return InteractionResult.SUCCESS;
        }

        if (!BuilderMaterialBindings.hasItemHandler(be) && !BuilderMaterialBindings.hasFluidHandler(be)) {
            player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.bind.failure"), true);
            return InteractionResult.SUCCESS;
        }

        writeBoundPos(stack, level, pos, false);
        player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.bind.success",
                pos.getX(), pos.getY(), pos.getZ()), true);
        player.getInventory().setChanged();
        return InteractionResult.SUCCESS;
    }

    private void disassembleController(ItemStack stack, ServerPlayer player, IMultiController controller) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MatchPlan plan = findMatchingPlan(player.level(), controller);
        if (plan == null || plan.entries().isEmpty()) {
            player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.failure.no_match"), true);
            return;
        }
        BoundHandlers handlers = resolveBoundHandlers(player, stack);
        if (controller.isFormed()) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(controller.getMultiblockState());
            controller.onStructureInvalid(false);
        }
        if (isSlowMode(stack)) {
            SlowMultiblockDisassemblyScheduler.replace(player, serverLevel.dimension(), plan.entries(), handlers.itemHandler(), handlers.fluidHandler());
            player.displayClientMessage(Component.translatable("item.mbd2.mbd_disassembly_tool.started.slow", plan.entries().size()), true);
            return;
        }
        int removed = 0;
        for (DisassemblyEntry entry : plan.entries()) {
            if (executeDisassemblyEntry(player, serverLevel, entry, handlers.itemHandler(), handlers.fluidHandler())) {
                removed++;
            }
        }
        player.displayClientMessage(Component.translatable(removed > 0
                ? "item.mbd2.mbd_disassembly_tool.finished.fast"
                : "item.mbd2.mbd_disassembly_tool.failure.no_space", removed), true);
    }

    @Nullable
    private static MatchPlan findMatchingPlan(Level level, IMultiController controller) {
        var lock = controller.getPatternLock();
        lock.lock();
        try {
            MatchPlan formedPlan = tryCreateFormedMatchingPlan(level, controller);
            if (formedPlan != null && !formedPlan.entries().isEmpty()) {
                return formedPlan;
            }
            List<BlockPattern> patterns = getDisassemblyPatterns(controller);
            if (patterns.isEmpty()) {
                return null;
            }
            for (BlockPattern pattern : patterns) {
                MatchPlan plan = tryCreateStrictMatchingPlan(level, controller, pattern);
                if (plan != null && !plan.entries().isEmpty()) {
                    return plan;
                }
            }
            MatchPlan bestPartialPlan = null;
            for (BlockPattern pattern : patterns) {
                bestPartialPlan = betterPlan(bestPartialPlan, tryCreateDisassemblyMatchingPlan(level, controller, pattern));
                bestPartialPlan = betterPlan(bestPartialPlan, tryCreateGeometryMatchingPlan(level, controller, pattern));
            }
            return bestPartialPlan;
        } finally {
            lock.unlock();
        }
    }

    private static List<BlockPattern> getDisassemblyPatterns(IMultiController controller) {
        List<BlockPattern> patterns = new ArrayList<>();
        addPattern(patterns, controller.getMultiblockState().getMatchedPattern());
        if (controller instanceof MBDMultiblockMachine multiblock) {
            for (BlockPattern pattern : multiblock.getDefinition().getPatterns(multiblock)) {
                addPattern(patterns, pattern);
            }
        }
        addPattern(patterns, controller.getPattern());
        return patterns;
    }

    private static boolean addPattern(List<BlockPattern> patterns, @Nullable BlockPattern pattern) {
        if (pattern instanceof CombinedBlockPattern combinedPattern) {
            boolean added = false;
            for (BlockPattern candidate : combinedPattern.getPatterns()) {
                added |= addPattern(patterns, candidate);
            }
            return added;
        }
        if (pattern == null || patterns.contains(pattern)) {
            return false;
        }
        patterns.add(pattern);
        return true;
    }

    @Nullable
    private static MatchPlan tryCreateFormedMatchingPlan(Level level, IMultiController controller) {
        if (!controller.isFormed()) {
            return null;
        }
        MultiblockState state = controller.getMultiblockState();
        Map<BlockPos, TraceabilityPredicate> predicates = state.getFormedMatchContext().get("predicates");
        if (predicates == null || predicates.isEmpty()) {
            return null;
        }
        return createMatchingPlan(level, controller, predicates, state.getPatternFacing(), state.getPatternBaseFacing(), true);
    }

    @Nullable
    private static MatchPlan tryCreateStrictMatchingPlan(Level level, IMultiController controller, BlockPattern pattern) {
        MultiblockState normalState = new MultiblockState(level, controller.getPos());
        if (pattern.checkPatternAt(normalState, true)) {
            return createMatchingPlan(level, controller, normalState, true);
        }
        return null;
    }

    @Nullable
    private static MatchPlan tryCreateDisassemblyMatchingPlan(Level level, IMultiController controller, BlockPattern pattern) {
        MultiblockState disassemblyState = new MultiblockState(level, controller.getPos());
        if (pattern.checkPatternAt(disassemblyState, true, MultiblockDisassemblyToolItem::matchesPredicateForDisassembly)) {
            return createMatchingPlan(level, controller, disassemblyState, true);
        }
        return null;
    }

    @Nullable
    private static MatchPlan tryCreateGeometryMatchingPlan(Level level, IMultiController controller, BlockPattern pattern) {
        MatchPlan bestPlan = null;
        for (Direction facing : getCandidateFacings(controller)) {
            for (Map<BlockPos, TraceabilityPredicate> predicates : pattern.getPredicatePositionVariants(controller.getPos(), facing, MAX_PARTIAL_PATTERN_VARIANTS)) {
                if (predicates.isEmpty()) {
                    continue;
                }
                bestPlan = betterPlan(bestPlan, createMatchingPlan(level, controller, predicates, facing, pattern.mbd2$getBaseFacing(), true));
            }
        }
        return bestPlan;
    }

    private static Direction[] getCandidateFacings(IMultiController controller) {
        return controller.hasFrontFacing()
                ? new Direction[]{controller.getFrontFacing().orElse(Direction.NORTH)}
                : HORIZONTAL_FACINGS;
    }

    @Nullable
    private static MatchPlan betterPlan(@Nullable MatchPlan current, @Nullable MatchPlan candidate) {
        if (candidate == null || candidate.entries().isEmpty()) {
            return current;
        }
        if (current == null || candidate.entries().size() > current.entries().size()) {
            return candidate;
        }
        return current;
    }

    @Nullable
    private static MatchPlan createMatchingPlan(Level level, IMultiController controller, MultiblockState state, boolean verifyEntries) {
        Map<BlockPos, TraceabilityPredicate> predicates = state.getMatchContext().get("predicates");
        if (predicates == null || predicates.isEmpty()) {
            return null;
        }
        return createMatchingPlan(level, controller, predicates, state.getPatternFacing(), state.getPatternBaseFacing(), verifyEntries);
    }

    @Nullable
    private static MatchPlan createMatchingPlan(Level level, IMultiController controller, Map<BlockPos, TraceabilityPredicate> predicates,
                                                Direction patternFacing, Direction patternBaseFacing, boolean verifyEntries) {
        if (predicates.isEmpty()) {
            return null;
        }
        List<DisassemblyEntry> entries = new ArrayList<>();
        for (var entry : predicates.entrySet()) {
            TraceabilityPredicate predicate = entry.getValue();
            if (predicate == null || predicate.isAny() || predicate.isAir()) {
                continue;
            }
            BlockState blockState = level.getBlockState(entry.getKey());
            if (blockState.isAir()) {
                continue;
            }
            DisassemblyEntry disassemblyEntry = new DisassemblyEntry(entry.getKey().immutable(), controller.getPos().immutable(), predicate,
                    patternFacing, patternBaseFacing);
            if (!verifyEntries || matchesEntryPredicate(level, disassemblyEntry, blockState)) {
                entries.add(disassemblyEntry);
            }
        }
        entries.sort(Comparator.comparing((DisassemblyEntry entry) -> entry.pos().equals(controller.getPos())));
        return new MatchPlan(entries);
    }

    public static boolean executeDisassemblyEntry(ServerPlayer player,
                                                  ServerLevel level,
                                                  DisassemblyEntry entry,
                                                  @Nullable IItemHandler boundItemHandler,
                                                  @Nullable IFluidHandler boundFluidHandler) {
        BlockPos pos = entry.pos();
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!matchesEntryPredicate(level, entry, state)) {
            return false;
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, player.getMainHandItem());
        FluidStack fluid = fluidToRemove(state.getFluidState());
        if (!canStoreItems(player, drops, boundItemHandler) || !canStoreFluid(player, fluid, boundFluidHandler)) {
            return false;
        }
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
            return false;
        }
        level.levelEvent(2001, pos, Block.getId(state));
        storeItems(player, drops, boundItemHandler);
        storeFluid(player, fluid, boundFluidHandler);
        return true;
    }

    private static boolean matchesEntryPredicate(Level level, DisassemblyEntry entry, BlockState state) {
        if (entry.predicate().isAny() || entry.predicate().isAir()) {
            return false;
        }
        CheckState checkState = new CheckState(level, entry.controllerPos());
        if (!checkState.prepare(entry.pos(), entry.predicate(), entry.patternFacing(), entry.patternBaseFacing())) {
            return false;
        }
        BlockState represented = checkState.getRepresentedBlockState();
        if (represented == null || represented.isAir()) {
            represented = state;
        }
        for (SimplePredicate simplePredicate : entry.predicate().limited) {
            if (limitedMatchesForDisassembly(simplePredicate, checkState, represented, false)) {
                return true;
            }
        }
        for (SimplePredicate simplePredicate : entry.predicate().common) {
            if (commonMatchesForDisassembly(simplePredicate, checkState, represented, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPredicateForDisassembly(MultiblockState state, TraceabilityPredicate predicate) {
        if (predicate == null) {
            return false;
        }
        state.io = IO.BOTH;
        boolean hasAny = containsAnyPredicate(predicate);
        BlockState represented = state.getRepresentedBlockState();
        boolean matched = false;
        for (SimplePredicate simplePredicate : predicate.limited) {
            if (limitedMatchesForDisassembly(simplePredicate, state, represented, true)) {
                matched = true;
            }
        }
        if (!matched) {
            for (SimplePredicate simplePredicate : predicate.common) {
                if (commonMatchesForDisassembly(simplePredicate, state, represented, true)) {
                    matched = true;
                    break;
                }
            }
        }
        matched |= hasAny;
        if (matched) {
            state.setError(null);
        }
        return matched;
    }

    private static boolean commonMatchesForDisassembly(SimplePredicate predicate, MultiblockState checkState, BlockState represented, boolean allowAir) {
        if (predicate == SimplePredicate.ANY) {
            return false;
        }
        if (predicate == SimplePredicate.AIR) {
            return allowAir && predicate.predicate.test(checkState) && simpleInnerConditionsMatch(predicate, checkState);
        }
        return baseMatchesForDisassembly(predicate, checkState, represented) && simpleInnerConditionsMatch(predicate, checkState);
    }

    private static boolean limitedMatchesForDisassembly(SimplePredicate predicate, MultiblockState checkState, BlockState represented, boolean allowAir) {
        if (predicate == SimplePredicate.ANY) {
            return false;
        }
        if (predicate == SimplePredicate.AIR && !allowAir) {
            return false;
        }
        boolean base = predicate == SimplePredicate.AIR
                ? predicate.predicate.test(checkState)
                : baseMatchesForDisassembly(predicate, checkState, represented);
        return testGlobalForDisassembly(predicate, checkState, base)
                && testLayerForDisassembly(predicate, checkState, base)
                && simpleInnerConditionsMatch(predicate, checkState);
    }

    private static boolean baseMatchesForDisassembly(SimplePredicate predicate, MultiblockState checkState, BlockState represented) {
        if (predicate instanceof PredicateStates) {
            return statePredicateMatchesRepresentedBlock(predicate, represented);
        }
        return predicate.predicate.test(checkState);
    }

    private static boolean statePredicateMatchesRepresentedBlock(SimplePredicate predicate, BlockState represented) {
        if (predicate.candidates == null || represented == null) {
            return false;
        }
        BlockInfo[] infos = predicate.candidates.get();
        if (infos == null) {
            return false;
        }
        for (BlockInfo info : infos) {
            if (info == null || info.getBlockState() == null) {
                continue;
            }
            BlockState expected = info.getBlockState();
            if (!expected.getFluidState().isEmpty()
                    && expected.getFluidState().getType() == represented.getFluidState().getType()) {
                return true;
            }
            if (expected.getBlock() != Blocks.AIR && expected.getBlock() == represented.getBlock()) {
                return true;
            }
        }
        return false;
    }

    private static boolean testGlobalForDisassembly(SimplePredicate predicate, MultiblockState state, boolean base) {
        if (predicate.minCount == -1 && predicate.maxCount == -1) return true;
        Integer count = state.getGlobalCount().get(predicate);
        count = (count == null ? 0 : count) + (base ? 1 : 0);
        state.getGlobalCount().put(predicate, count);
        if (predicate.maxCount == -1 || count <= predicate.maxCount) return base;
        state.setError(new SinglePredicateError(predicate, 0));
        return false;
    }

    private static boolean testLayerForDisassembly(SimplePredicate predicate, MultiblockState state, boolean base) {
        if (predicate.minLayerCount == -1 && predicate.maxLayerCount == -1) return true;
        Integer count = state.getLayerCount().get(predicate);
        count = (count == null ? 0 : count) + (base ? 1 : 0);
        state.getLayerCount().put(predicate, count);
        if (predicate.maxLayerCount == -1 || count <= predicate.maxLayerCount) return base;
        state.setError(new SinglePredicateError(predicate, 2));
        return false;
    }

    private static boolean simpleInnerConditionsMatch(SimplePredicate predicate, MultiblockState checkState) {
        if (!predicate.nbt.isEmpty() && !checkState.world.isClientSide) {
            BlockEntity be = checkState.getTileEntity();
            if (be == null) {
                return false;
            }
            CompoundTag tag = be.saveWithFullMetadata();
            if (!tag.equals(tag.copy().merge(predicate.nbt))) {
                return false;
            }
        }
        if (!predicate.controllerNbt.isEmpty() && !checkState.world.isClientSide) {
            IMultiController controller = checkState.getController();
            if (controller != null) {
                CompoundTag tag = controller.getHolder().saveWithFullMetadata();
                if (!tag.equals(tag.copy().merge(predicate.controllerNbt))) {
                    return true;
                }
            }
        }
        if (predicate.controllerFront.isEnable()) {
            IMultiController controller = checkState.getController();
            Direction front = controller == null
                    ? checkState.getPatternFacing()
                    : controller.getFrontFacing().orElse(checkState.getPatternFacing());
            return front == predicate.controllerFront.getValue();
        }
        return true;
    }

    private static boolean canStoreItems(ServerPlayer player, List<ItemStack> drops, @Nullable IItemHandler boundItemHandler) {
        List<ItemStack> virtualPlayer = copyPlayerInventory(player);
        List<ItemStack> virtualBound = boundItemHandler == null ? null : copyHandlerSlots(boundItemHandler);
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            ItemStack remaining = drop.copy();
            if (boundItemHandler != null) {
                remaining = insertIntoVirtualHandler(boundItemHandler, virtualBound, remaining);
            }
            remaining = insertIntoVirtualInventoryItemContainers(virtualPlayer, remaining);
            remaining = insertIntoVirtualPlayerInventory(player, virtualPlayer, remaining);
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void storeItems(ServerPlayer player, List<ItemStack> drops, @Nullable IItemHandler boundItemHandler) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            ItemStack remaining = drop.copy();
            if (boundItemHandler != null) {
                remaining = ItemHandlerHelper.insertItemStacked(boundItemHandler, remaining, false);
            }
            remaining = insertIntoInventoryItemContainers(player, remaining);
            remaining = insertIntoPlayerInventory(player, remaining, false);
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
    }

    private static boolean canStoreFluid(ServerPlayer player, FluidStack fluid, @Nullable IFluidHandler boundFluidHandler) {
        if (fluid.isEmpty()) {
            return true;
        }
        FluidStack remaining = fluid.copy();
        if (boundFluidHandler != null) {
            int filled = boundFluidHandler.fill(remaining, IFluidHandler.FluidAction.SIMULATE);
            remaining.shrink(filled);
        }
        if (remaining.isEmpty()) {
            return true;
        }
        return fillInventoryFluidContainers(player, remaining, true).isEmpty();
    }

    private static void storeFluid(ServerPlayer player, FluidStack fluid, @Nullable IFluidHandler boundFluidHandler) {
        if (fluid.isEmpty()) {
            return;
        }
        FluidStack remaining = fluid.copy();
        if (boundFluidHandler != null) {
            int filled = boundFluidHandler.fill(remaining, IFluidHandler.FluidAction.EXECUTE);
            remaining.shrink(filled);
        }
        if (!remaining.isEmpty()) {
            fillInventoryFluidContainers(player, remaining, false);
        }
    }

    private static FluidStack fillInventoryFluidContainers(ServerPlayer player, FluidStack fluid, boolean simulate) {
        FluidStack remaining = fluid.copy();
        for (int i = 0; i < player.getInventory().items.size() && !remaining.isEmpty(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            var handlerOptional = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
            if (handlerOptional.isEmpty()) {
                continue;
            }
            IFluidHandlerItem handler = handlerOptional.get();
            int filled = handler.fill(remaining, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (filled <= 0) {
                continue;
            }
            remaining.shrink(filled);
            if (!simulate) {
                player.getInventory().setItem(i, handler.getContainer());
            }
        }
        if (!simulate) {
            player.getInventory().setChanged();
        }
        return remaining;
    }

    private static FluidStack fluidToRemove(FluidState state) {
        if (state.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(state.getType(), 1000);
    }

    private static List<ItemStack> copyPlayerInventory(Player player) {
        List<ItemStack> copy = new ArrayList<>(player.getInventory().items.size());
        for (ItemStack stack : player.getInventory().items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static List<ItemStack> copyHandlerSlots(IItemHandler handler) {
        List<ItemStack> copy = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            copy.add(handler.getStackInSlot(i).copy());
        }
        return copy;
    }

    private static ItemStack insertIntoVirtualInventoryItemContainers(List<ItemStack> inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < inventory.size() && !remaining.isEmpty(); i++) {
            ItemStack containerStack = inventory.get(i);
            if (containerStack.isEmpty()) {
                continue;
            }
            var handlerOptional = containerStack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
            if (handlerOptional.isEmpty()) {
                continue;
            }
            IItemHandler handler = handlerOptional.get();
            ItemStack before = remaining.copy();
            remaining = insertIntoItemHandler(handler, remaining);
            if (!ItemStack.matches(before, remaining)) {
                inventory.set(i, containerStack);
            }
        }
        return remaining;
    }

    private static ItemStack insertIntoInventoryItemContainers(Player player, ItemStack stack) {
        ItemStack remaining = insertIntoVirtualInventoryItemContainers(player.getInventory().items, stack);
        if (remaining.getCount() != stack.getCount()) {
            player.getInventory().setChanged();
        }
        return remaining;
    }

    private static ItemStack insertIntoItemHandler(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    private static ItemStack insertIntoVirtualHandler(IItemHandler handler, @Nullable List<ItemStack> slots, ItemStack stack) {
        if (slots == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = slots.get(i);
            if (existing.isEmpty()) {
                continue;
            }
            mergeIntoVirtualSlot(handler, slots, i, remaining);
        }
        for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = slots.get(i);
            if (!existing.isEmpty() || !handler.isItemValid(i, remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), Math.min(handler.getSlotLimit(i), remaining.getMaxStackSize()));
            if (moved <= 0) {
                continue;
            }
            slots.set(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return remaining;
    }

    private static void mergeIntoVirtualSlot(IItemHandler handler, List<ItemStack> slots, int slot, ItemStack remaining) {
        ItemStack existing = slots.get(slot);
        if (!ItemStack.isSameItemSameTags(existing, remaining) || !handler.isItemValid(slot, remaining)) {
            return;
        }
        int limit = Math.min(existing.getMaxStackSize(), handler.getSlotLimit(slot));
        int moved = Math.min(remaining.getCount(), limit - existing.getCount());
        if (moved <= 0) {
            return;
        }
        existing.grow(moved);
        remaining.shrink(moved);
    }

    private static ItemStack insertIntoVirtualPlayerInventory(Player player, List<ItemStack> slots, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = slots.get(i);
            if (!ItemStack.isSameItemSameTags(existing, remaining)) {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), player.getInventory().getMaxStackSize());
            int moved = Math.min(remaining.getCount(), limit - existing.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.grow(moved);
            remaining.shrink(moved);
        }
        for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
            if (!slots.get(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), player.getInventory().getMaxStackSize()));
            if (moved <= 0) {
                continue;
            }
            slots.set(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return remaining;
    }

    private static ItemStack insertIntoPlayerInventory(Player player, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> slots = simulate ? copyPlayerInventory(player) : player.getInventory().items;
        ItemStack remaining = insertIntoVirtualPlayerInventory(player, slots, stack);
        if (!simulate) {
            player.getInventory().setChanged();
        }
        return remaining;
    }

    private static BoundHandlers resolveBoundHandlers(ServerPlayer player, ItemStack stack) {
        BoundPos bound = readBoundPos(stack);
        if (bound == null || !Objects.equals(bound.dimension(), player.level().dimension().location())) {
            return BoundHandlers.EMPTY;
        }
        BlockEntity be = player.level().getBlockEntity(bound.pos());
        if (be == null) {
            return BoundHandlers.EMPTY;
        }
        IItemHandler itemHandler = null;
        List<IItemHandler> itemHandlers = isBoundSourceME(stack)
                ? BuilderMaterialBindings.collectMEItemHandlers(be)
                : BuilderMaterialBindings.collectItemHandlers(be);
        if (itemHandlers.size() == 1) {
            itemHandler = itemHandlers.get(0);
        } else if (itemHandlers.size() > 1) {
            MultiItemHandler multiItemHandler = new MultiItemHandler(itemHandlers);
            if (!multiItemHandler.isEmpty()) {
                itemHandler = multiItemHandler;
            }
        }
        IFluidHandler fluidHandler = null;
        List<IFluidHandler> fluidHandlers = AutoBuildPlacementExecutor.collectFluidHandlers(be);
        if (fluidHandlers.size() == 1) {
            fluidHandler = fluidHandlers.get(0);
        } else if (fluidHandlers.size() > 1) {
            MultiFluidHandler multiFluidHandler = new MultiFluidHandler(fluidHandlers);
            if (!multiFluidHandler.isEmpty()) {
                fluidHandler = multiFluidHandler;
            }
        }
        return new BoundHandlers(itemHandler, fluidHandler);
    }

    private static boolean containsAnyPredicate(TraceabilityPredicate predicate) {
        return predicate.isAny() || predicate.common.contains(SimplePredicate.ANY) || predicate.limited.contains(SimplePredicate.ANY);
    }

    private static boolean isSlowMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_SLOW);
    }

    private static void setSlowMode(ItemStack stack, boolean slow) {
        stack.getOrCreateTag().putBoolean(TAG_SLOW, slow);
    }

    private static void writeBoundPos(ItemStack stack, Level level, BlockPos pos, boolean meSource) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_BOUND_DIM, level.dimension().location().toString());
        tag.putLong(TAG_BOUND_POS, pos.asLong());
        if (meSource) {
            tag.putString(TAG_BOUND_SOURCE_TYPE, SOURCE_TYPE_ME);
        } else {
            tag.remove(TAG_BOUND_SOURCE_TYPE);
        }
    }

    private static boolean isBoundSourceME(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && SOURCE_TYPE_ME.equals(tag.getString(TAG_BOUND_SOURCE_TYPE));
    }

    @Nullable
    private static BoundPos readBoundPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_DIM) || !tag.contains(TAG_BOUND_POS)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_BOUND_DIM));
        if (dimension == null) {
            return null;
        }
        return new BoundPos(dimension, BlockPos.of(tag.getLong(TAG_BOUND_POS)));
    }

    public record DisassemblyEntry(BlockPos pos,
                                   BlockPos controllerPos,
                                   TraceabilityPredicate predicate,
                                   Direction patternFacing,
                                   Direction patternBaseFacing) {
    }

    private record MatchPlan(List<DisassemblyEntry> entries) {
    }

    private record BoundPos(ResourceLocation dimension, BlockPos pos) {
    }

    private record BoundHandlers(@Nullable IItemHandler itemHandler, @Nullable IFluidHandler fluidHandler) {
        private static final BoundHandlers EMPTY = new BoundHandlers(null, null);
    }

    private static final class CheckState extends MultiblockState {
        private CheckState(Level world, BlockPos controllerPos) {
            super(world, controllerPos);
        }

        private boolean prepare(BlockPos pos, TraceabilityPredicate predicate, Direction patternFacing, Direction patternBaseFacing) {
            clean();
            setPatternContext(patternFacing, patternBaseFacing);
            return update(pos, predicate);
        }
    }
}
