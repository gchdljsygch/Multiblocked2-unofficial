package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.pattern.error.PatternError;
import com.lowdragmc.mbd2.api.pattern.error.PatternStringError;
import com.lowdragmc.mbd2.api.pattern.error.SinglePredicateError;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.api.pattern.util.PatternMatchContext;
import com.lowdragmc.mbd2.api.pattern.util.PatternStateRotation;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import com.lowdragmc.mbd2.common.autobuild.AutoBuildPlacementExecutor;
import com.lowdragmc.mbd2.common.autobuild.SlowAutoBuildScheduler;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import com.lowdragmc.mbd2.utils.MultiFluidHandler;
import com.lowdragmc.mbd2.utils.MultiItemHandler;
import com.lowdragmc.mbd2.utils.PatternAutoBuildPlacement;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Compiled multiblock structure pattern used for validation, preview rendering,
 * and player auto-building.
 *
 * <p>The business goal is to turn a symbolic pattern definition into concrete
 * world checks around a controller position. Pattern checks may be called from
 * async controller logic, so predicates and state updates used during checking
 * should stay read-only with respect to world and machine state. Auto-build is
 * different: it is an interactive world mutation path and must run on the normal
 * player/server interaction thread.</p>
 */
public class BlockPattern {

    static Direction[] FACINGS = {Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
    static Direction[] FACINGS_H = {Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST};
    public final int[][] aisleRepetitions;
    public final RelativeDirection[] structureDir;
    protected final TraceabilityPredicate[][][] blockMatches; //[z][y][x]
    protected final int fingerLength; //z size
    protected final int thumbLength; //y size
    protected final int palmLength; //x size
    protected final int[] centerOffset; // x, y, z, minZ, maxZ
    private Direction mbd2$baseFacing = Direction.NORTH;

    /**
     * Creates a compiled pattern.
     *
     * <p>Preconditions: {@code predicatesIn} is indexed as {@code [z][y][x]},
     * {@code structureDir} has three orthogonal relative directions, and
     * {@code centerOffset} contains controller offsets as
     * {@code [x, y, z, minZ, maxZ]}. Side effects: stores the supplied arrays by
     * reference.</p>
     *
     * @param predicatesIn     predicate grid for every pattern position
     * @param structureDir     mapping from pattern axes to controller-relative axes
     * @param aisleRepetitions min/max repetition count for each z-slice
     * @param centerOffset     controller location and repeat-search bounds
     */
    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, RelativeDirection[] structureDir, int[][] aisleRepetitions, int[] centerOffset) {
        this.blockMatches = predicatesIn;
        this.fingerLength = predicatesIn.length;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;

        if (this.fingerLength > 0) {
            this.thumbLength = predicatesIn[0].length;

            if (this.thumbLength > 0) {
                this.palmLength = predicatesIn[0][0].length;
            } else {
                this.palmLength = 0;
            }
        } else {
            this.thumbLength = 0;
            this.palmLength = 0;
        }

        this.centerOffset = centerOffset;
    }

    /**
     * Returns the base horizontal facing used when rotating preview and
     * auto-build block states.
     *
     * @return horizontal base facing; defaults to north
     */
    public Direction mbd2$getBaseFacing() {
        return mbd2$baseFacing;
    }

    /**
     * Sets the base horizontal facing for pattern state rotation.
     *
     * <p>Side effects: vertical or {@code null} values reset the base facing to
     * north.</p>
     *
     * @param facing horizontal facing encoded in the pattern definition
     */
    public void mbd2$setBaseFacing(Direction facing) {
        if (facing == null || facing.getAxis() == Direction.Axis.Y) {
            this.mbd2$baseFacing = Direction.NORTH;
            return;
        }
        this.mbd2$baseFacing = facing;
    }

    /**
     * Estimates the maximum number of block positions covered by this pattern.
     *
     * <p>Business goal: let controllers scale async check frequency for large
     * multiblocks. Side effects: none.</p>
     *
     * @return estimated block count, clamped to {@link Integer#MAX_VALUE}
     */
    public int getEstimatedBlockCount() {
        if (fingerLength <= 0 || thumbLength <= 0 || palmLength <= 0) {
            return 0;
        }
        long repetitions = 0;
        for (int i = 0; i < fingerLength; i++) {
            int maxRepetition = 1;
            if (i < aisleRepetitions.length && aisleRepetitions[i].length > 1) {
                maxRepetition = Math.max(aisleRepetitions[i][0], aisleRepetitions[i][1]);
            }
            repetitions += Math.max(1, maxRepetition);
        }
        long blocks = repetitions * thumbLength * palmLength;
        return blocks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) blocks;
    }

    /**
     * Builds concrete predicate-position maps for possible repeat counts.
     *
     * <p>Business goal: tools such as partial disassembly can reason about a
     * pattern's geometry without requiring the whole structure to match first.
     * Side effects: none.</p>
     *
     * @param centerPos   absolute controller position
     * @param facing      controller/front facing used to resolve relative axes
     * @param maxVariants upper bound for generated repeat combinations
     * @return predicate maps keyed by absolute block position
     */
    public List<Map<BlockPos, TraceabilityPredicate>> getPredicatePositionVariants(BlockPos centerPos, Direction facing, int maxVariants) {
        List<Map<BlockPos, TraceabilityPredicate>> variants = new ArrayList<>();
        if (fingerLength <= 0 || thumbLength <= 0 || palmLength <= 0 || maxVariants <= 0) {
            return variants;
        }
        int[] maxRepetitions = new int[fingerLength];
        for (int layer = 0; layer < fingerLength; layer++) {
            maxRepetitions[layer] = getMaxRepetition(layer);
        }
        variants.add(buildPredicatePositionVariant(centerPos, facing, maxRepetitions));
        collectPredicatePositionVariants(centerPos, facing, maxVariants, variants, new int[fingerLength], 0);
        return variants;
    }

    private void collectPredicatePositionVariants(BlockPos centerPos, Direction facing, int maxVariants,
                                                  List<Map<BlockPos, TraceabilityPredicate>> variants,
                                                  int[] repetitions, int layer) {
        if (variants.size() >= maxVariants) {
            return;
        }
        if (layer >= fingerLength) {
            variants.add(buildPredicatePositionVariant(centerPos, facing, repetitions));
            return;
        }
        int min = 1;
        int max = 1;
        if (layer < aisleRepetitions.length && aisleRepetitions[layer].length > 1) {
            min = Math.max(0, aisleRepetitions[layer][0]);
            max = Math.max(min, aisleRepetitions[layer][1]);
        }
        for (int repeat = min; repeat <= max && variants.size() < maxVariants; repeat++) {
            repetitions[layer] = repeat;
            collectPredicatePositionVariants(centerPos, facing, maxVariants, variants, repetitions, layer + 1);
        }
    }

    private int getMaxRepetition(int layer) {
        if (layer < aisleRepetitions.length && aisleRepetitions[layer].length > 1) {
            return Math.max(0, Math.max(aisleRepetitions[layer][0], aisleRepetitions[layer][1]));
        }
        return 1;
    }

    private Map<BlockPos, TraceabilityPredicate> buildPredicatePositionVariant(BlockPos centerPos, Direction facing, int[] repetitions) {
        Map<BlockPos, TraceabilityPredicate> predicates = new LinkedHashMap<>();
        int z = 0;
        for (int c = 0; c < centerOffset[2] && c < repetitions.length; c++) {
            z -= repetitions[c];
        }
        for (int c = 0; c < fingerLength; c++) {
            int repeatCount = c < repetitions.length ? repetitions[c] : 1;
            for (int r = 0; r < repeatCount; r++) {
                for (int b = 0, y = -centerOffset[1]; b < thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < palmLength; a++, x++) {
                        TraceabilityPredicate predicate = blockMatches[c][b][a];
                        if (predicate.addCache()) {
                            BlockPos pos = setActualRelativeOffset(x, y, z, facing)
                                    .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            predicates.put(pos.immutable(), predicate);
                        }
                    }
                }
                z++;
            }
        }
        return predicates;
    }

    /**
     * Checks this pattern at the state's controller position without requiring a
     * controller capability.
     *
     * <p>Side effects: mutates {@code worldState}'s transient match context,
     * error, cache, and matched-pattern fields.</p>
     *
     * @param worldState world state whose {@code controllerPos} is used as the
     *                   center
     * @param facing     pattern facing to test
     * @return {@code true} when the pattern matches
     */
    public boolean checkPatternAtWithoutController(MultiblockState worldState, Direction facing) {
        var centerPos = worldState.controllerPos;
        return checkPatternAt(worldState, centerPos, facing, false);
    }

    /**
     * Checks this pattern around the controller recorded in the world state.
     *
     * <p>Business goal: test the controller's valid front direction, or all
     * horizontal directions for non-facing controllers. Side effects: clears any
     * previous matched pattern and updates {@code worldState} with match data,
     * errors, part sets, IO maps, and optional predicate caches.</p>
     *
     * @param worldState    mutable state object reused during pattern checks
     * @param savePredicate {@code true} to store the predicate matched at each
     *                      cached position for later diagnostics/rendering
     * @return {@code true} when any allowed facing matches
     */
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        return checkPatternAt(worldState, savePredicate, (state, predicate) -> predicate.test(state));
    }

    /**
     * Checks this pattern using a caller supplied predicate evaluator.
     *
     * <p>This is used by tools that need a different interpretation of the same
     * pattern positions without changing normal multiblock formation semantics.</p>
     */
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate,
                                  BiPredicate<MultiblockState, TraceabilityPredicate> predicateMatcher) {
        worldState.setMatchedPattern(null);
        IMultiController controller = worldState.getController();
        if (controller == null) {
            worldState.setError(new PatternStringError("no controller found"));
            return false;
        }
        BlockPos centerPos = controller.getPos();
        Direction[] facings = controller.hasFrontFacing() ?
                new Direction[]{controller.getFrontFacing().orElseThrow()} :
                FACINGS_H;
        for (Direction facing : facings) {
            if (checkPatternAt(worldState, centerPos, facing, savePredicate, predicateMatcher)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks this pattern at an explicit center and facing.
     *
     * <p>Preconditions: callers must coordinate external synchronization when
     * sharing {@code worldState}; controllers normally use their pattern lock.
     * This method may be used by async pattern logic, so predicates should not
     * perform world mutation. Side effects: resets and repopulates match context,
     * count maps, position cache, matched pattern, IO data, part discovery, and
     * error state.</p>
     *
     * @param worldState    mutable state object for world access and diagnostics
     * @param centerPos     absolute controller/anchor position
     * @param facing        tested controller front direction
     * @param savePredicate {@code true} to remember predicates per cached
     *                      position
     * @return {@code true} when all repeated aisles, predicates, and count limits
     * match
     */
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction facing, boolean savePredicate) {
        return checkPatternAt(worldState, centerPos, facing, savePredicate, (state, predicate) -> predicate.test(state));
    }

    /**
     * Checks this pattern at an explicit center and facing using a caller
     * supplied predicate evaluator.
     */
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction facing, boolean savePredicate,
                                  BiPredicate<MultiblockState, TraceabilityPredicate> predicateMatcher) {
        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];
        worldState.clean();
        worldState.setMatchedPattern(null);
        worldState.setPatternContext(facing, mbd2$getBaseFacing());
        PatternMatchContext matchContext = worldState.getMatchContext();
        Map<SimplePredicate, Integer> globalCount = worldState.getGlobalCount();
        Map<SimplePredicate, Integer> layerCount = worldState.getLayerCount();
        //Checking aisles
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            //Checking repeatable slices
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                Set<IMultiPart> parts = matchContext.getOrCreate("parts", HashSet::new);
                Set<IMultiPart> addedParts = new HashSet<>();
                List<BlockPos> touchedPositions = new ArrayList<>();
                Map<SimplePredicate, Integer> globalCountSnapshot = new HashMap<>(globalCount);
                //Checking single slice
                layerCount.clear();

                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        worldState.setError(null);
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        BlockPos pos = setActualRelativeOffset(x, y, z, facing).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        BlockPos immutablePos = pos.immutable();
                        touchedPositions.add(immutablePos);
                        if (!worldState.update(pos, predicate)) {
                            return false;
                        }
                        boolean canPartShared = true;
                        var machineOptional = IMachine.ofMachine(worldState.getTileEntity());
                        IMultiPart matchedPart = null;
                        if (machineOptional.isPresent() && machineOptional.orElseThrow() instanceof IMultiPart part &&
                                part.isPartEnabled() && !immutablePos.equals(worldState.controllerPos)) {
                            if (!predicate.isAny()) {
                                if (part.isAttachedToController() && !part.canShared() && !part.hasController(worldState.controllerPos)) { // check part can be shared
                                    canPartShared = false;
                                    worldState.setError(new PatternStringError("multiblocked.pattern.error.share"));
                                } else {
                                    matchedPart = part;
                                }
                            }
                        }
                        // TODO vaBlock
//                        if (worldState.getBlockState().getBlock() instanceof ActiveBlock) {
//                            matchContext.getOrCreate("vaBlocks", LongOpenHashSet::new).add(worldState.getPos().asLong());
//                        }
                        if (!predicateMatcher.test(worldState, predicate) || !canPartShared) { // matching failed
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {//retreat to see if the first aisle can start later
                                    r = c = 0;
                                    z = minZ++;
                                    resetPatternAttempt(worldState, matchContext, globalCount, layerCount);
                                    findFirstAisle = false;
                                } else {
                                    rollbackRepeatAttempt(worldState, matchContext, globalCount, globalCountSnapshot, layerCount, touchedPositions, parts, addedParts);
                                }
                            } else {
                                z++;//continue searching for the first aisle
                                resetPatternAttempt(worldState, matchContext, globalCount, layerCount);
                            }
                            continue loop;
                        }
                        if (matchedPart != null) {
                            if (parts.add(matchedPart)) {
                                addedParts.add(matchedPart);
                            }
                            matchContext.getOrCreate("partPositions", LongOpenHashSet::new).add(immutablePos.asLong());
                        }
                        if (predicate.addCache()) {
                            worldState.addPosCache(immutablePos);
                            if (savePredicate) {
                                matchContext.getOrCreate("predicates", HashMap::new).put(immutablePos, predicate);
                            }
                        }
                        matchContext.getOrCreate("ioMap", Long2ObjectOpenHashMap::new).put(worldState.getPos().asLong(), worldState.io);
                    }
                }
                findFirstAisle = true;
                z++;

                //Check layer-local matcher predicate
                for (Map.Entry<SimplePredicate, Integer> entry : layerCount.entrySet()) {
                    if (entry.getValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
            }
            //Repetitions out of range
            if (r < aisleRepetitions[c][0] || worldState.hasError() || !findFirstAisle) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }
        }

        //Check count matches amount
        for (Map.Entry<SimplePredicate, Integer> entry : globalCount.entrySet()) {
            if (entry.getValue() < entry.getKey().minCount) {
                worldState.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }

        worldState.setError(null);
        worldState.setMatchedPattern(this);
        if (worldState.shouldCommitSuccessfulMatches()) {
            worldState.commitCache();
        }
        return true;
    }

    /**
     * Restores all mutable match state that may have been written while testing one optional repeated aisle.
     *
     * <p>Optional repetitions are allowed to fail after the minimum repeat count is satisfied. Any cache,
     * predicate, IO, slot, render mask, UI mask, count, or part state created by that failed attempt must be
     * removed so the already-accepted structure stays authoritative.</p>
     */
    private static void rollbackRepeatAttempt(MultiblockState worldState, PatternMatchContext matchContext,
                                              Map<SimplePredicate, Integer> globalCount,
                                              Map<SimplePredicate, Integer> globalCountSnapshot,
                                              Map<SimplePredicate, Integer> layerCount,
                                              List<BlockPos> touchedPositions,
                                              Set<IMultiPart> parts,
                                              Set<IMultiPart> addedParts) {
        globalCount.clear();
        globalCount.putAll(globalCountSnapshot);
        layerCount.clear();
        parts.removeAll(addedParts);
        removePositionsFromContext(worldState, matchContext, touchedPositions);
    }

    /**
     * Clears all accumulated state before retrying the whole pattern search from a later first aisle.
     */
    private static void resetPatternAttempt(MultiblockState worldState, PatternMatchContext matchContext,
                                            Map<SimplePredicate, Integer> globalCount,
                                            Map<SimplePredicate, Integer> layerCount) {
        if (worldState.cache != null) {
            worldState.cache.clear();
        }
        globalCount.clear();
        layerCount.clear();
        matchContext.reset();
    }

    /**
     * Removes per-position match context entries created while testing positions that no longer belong to a match.
     */
    private static void removePositionsFromContext(MultiblockState worldState, PatternMatchContext matchContext,
                                                   List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        Map<BlockPos, TraceabilityPredicate> predicates = matchContext.get("predicates");
        Map<Long, IO> ioMap = matchContext.get("ioMap");
        Map<Long, Set<String>> slots = matchContext.get("slots");
        LongSet renderMask = matchContext.get("renderMask");
        LongSet openUIMask = matchContext.get("openUIMask");
        LongSet partPositions = matchContext.get("partPositions");
        for (BlockPos pos : positions) {
            long posKey = pos.asLong();
            if (worldState.cache != null) {
                worldState.cache.remove(posKey);
            }
            if (predicates != null) {
                predicates.remove(pos);
            }
            if (ioMap != null) {
                ioMap.remove(posKey);
            }
            if (slots != null) {
                slots.remove(posKey);
            }
            if (renderMask != null) {
                renderMask.remove(posKey);
            }
            if (openUIMask != null) {
                openUIMask.remove(posKey);
            }
            if (partPositions != null) {
                partPositions.remove(posKey);
            }
        }
    }

    /**
     * Attempts to place missing structure blocks for a player.
     *
     * <p>Business goal: help players build the minimum repeated version of a
     * multiblock from inventory, bound item/fluid handlers, or creative access.
     * Preconditions: call from a normal player interaction context on the world
     * thread. Side effects: may consume player inventory, drain bound handlers,
     * place blocks or fluids, schedule slow-build placements, award placement
     * criteria/stats through the placement executor, and temporarily restore
     * existing machine/block states after placement planning.</p>
     *
     * @param player     player requesting auto-build
     * @param worldState pattern state for the target controller
     */
    public void autoBuild(Player player, MultiblockState worldState) {
        Level world = player.level();
        int minZ = -centerOffset[4];
        worldState.clean();
        IMultiController controller = worldState.getController();
        if (controller == null) {
            return;
        }
        BlockPos centerPos = controller.getPos();
        Direction facing = controller.getFrontFacing().orElse(Direction.NORTH);
        worldState.setPatternContext(facing, mbd2$getBaseFacing());
        Rotation rotation = computeRotation(this, facing);
        Map<SimplePredicate, Integer> cacheGlobal = worldState.getGlobalCount();
        Map<SimplePredicate, Integer> cacheLayer = worldState.getLayerCount();
        Map<BlockPos, Object> blocks = new HashMap<>();
        blocks.put(centerPos, controller);
        List<PatternAutoBuildPlacement> nonFluidPlacements = new ArrayList<>();
        List<PatternAutoBuildPlacement> fluidPlacements = new ArrayList<>();
        int[] reservedPerSlot = player.isCreative() ? null : new int[player.getInventory().items.size()];
        IItemHandler boundItemHandler = null;
        IFluidHandler boundFluidHandler = null;
        ItemStack builderStack = ItemStack.EMPTY;
        boolean slowBuild = false;
        if (!player.isCreative()) {
            builderStack = BuilderMaterialBindings.findBuilderStack(player);
            if (!builderStack.isEmpty()) {
                slowBuild = BuilderMaterialBindings.isSlowBuild(builderStack);
                var dim = world.dimension().location();

                var itemBound = BuilderMaterialBindings.readBoundItemPos(builderStack);
                if (itemBound != null && dim != null && dim.equals(itemBound.dimension())) {
                    BlockEntity be = world.getBlockEntity(itemBound.pos());
                    if (be != null) {
                        var handlers = collectItemHandlers(be);
                        if (handlers.size() == 1) {
                            boundItemHandler = handlers.get(0);
                        } else if (handlers.size() > 1) {
                            MultiItemHandler multi = new MultiItemHandler(handlers);
                            if (!multi.isEmpty()) {
                                boundItemHandler = multi;
                            }
                        }
                    }
                }

                var fluidBound = BuilderMaterialBindings.readBoundFluidPos(builderStack);
                if (fluidBound != null && dim != null && dim.equals(fluidBound.dimension())) {
                    BlockEntity be = world.getBlockEntity(fluidBound.pos());
                    if (be != null) {
                        var handlers = collectFluidHandlers(be);
                        if (handlers.size() == 1) {
                            boundFluidHandler = handlers.get(0);
                        } else if (handlers.size() > 1) {
                            MultiFluidHandler multi = new MultiFluidHandler(handlers);
                            if (!multi.isEmpty()) {
                                boundFluidHandler = multi;
                            }
                        }
                    }
                }
            }
        }

        for (int c = 0, z = minZ++; c < this.fingerLength; c++) {
            for (int r = 0; r < aisleRepetitions[c][0]; r++) {
                cacheLayer.clear();
                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        BlockPos pos = setActualRelativeOffset(x, y, z, facing).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        worldState.update(pos, predicate);

                        BlockState existing = world.getBlockState(pos);
                        boolean emptyBlock = world.isEmptyBlock(pos);
                        boolean existingHasFluid = hasAnyFluid(existing);
                        if (!emptyBlock && !existingHasFluid) {
                            blocks.put(pos, existing);
                            for (SimplePredicate limit : predicate.limited) {
                                limit.testLimited(worldState);
                            }
                            continue;
                        }

                        boolean find = false;
                        Map<BlockInfo, Boolean> candidateRotation = new IdentityHashMap<>();
                        BlockInfo[] infos = new BlockInfo[0];
                        for (SimplePredicate limit : predicate.limited) {
                            if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != facing)
                                continue;
                            if (limit.minLayerCount > 0) {
                                if (!cacheLayer.containsKey(limit)) {
                                    cacheLayer.put(limit, 1);
                                } else if (cacheLayer.get(limit) < limit.minLayerCount && (limit.maxLayerCount == -1 || cacheLayer.get(limit) < limit.maxLayerCount)) {
                                    cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                            infos = limit.candidates == null ? null : limit.candidates.get();
                            recordCandidateRotation(candidateRotation, infos, !limit.controllerFront.isEnable());
                            find = true;
                            break;
                        }
                        if (!find) {
                            for (SimplePredicate limit : predicate.limited) {
                                if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != facing)
                                    continue;
                                if (limit.maxLayerCount != -1 && cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount)
                                    continue;
                                if (limit.maxCount != -1 && cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount)
                                    continue;

                                cacheLayer.put(limit, cacheLayer.getOrDefault(limit, 0) + 1);
                                cacheGlobal.put(limit, cacheGlobal.getOrDefault(limit, 0) + 1);

                                BlockInfo[] candidates = limit.candidates == null ? null : limit.candidates.get();
                                infos = ArrayUtils.addAll(infos, candidates);
                                recordCandidateRotation(candidateRotation, candidates, !limit.controllerFront.isEnable());
                            }
                            for (SimplePredicate common : predicate.common) {
                                if (common.controllerFront.isEnable() && common.controllerFront.getValue() != facing)
                                    continue;
                                BlockInfo[] candidates = common.candidates == null ? null : common.candidates.get();
                                infos = ArrayUtils.addAll(infos, candidates);
                                recordCandidateRotation(candidateRotation, candidates, !common.controllerFront.isEnable());
                            }
                        }

                        List<BlockInfo> candidateInfos = new ArrayList<>();
                        List<ItemStack> candidates = new ArrayList<>();
                        if (infos != null) {
                            for (BlockInfo info : infos) {
                                if (info == null) continue;
                                BlockState s = info.getBlockState();
                                if (s == null || s.getBlock() == Blocks.AIR) continue;
                                ItemStack asItem = info.getItemStackForm();
                                if (asItem.isEmpty()) continue;
                                if (!(asItem.getItem() instanceof BlockItem || asItem.getItem() instanceof BucketItem))
                                    continue;
                                candidateInfos.add(info);
                                candidates.add(asItem);
                            }
                        }

                        BlockInfo expectedFluidInfo = pickExpectedFluidInfo(candidateInfos);
                        if (!emptyBlock && existingHasFluid && existing.getBlock() instanceof LiquidBlock && expectedFluidInfo != null) {
                            BlockState expectedFluidState = expectedFluidInfo.getBlockState();
                            if (expectedFluidState != null
                                    && expectedFluidState.getBlock() instanceof LiquidBlock
                                    && existing.getFluidState().isSource()
                                    && existing.getFluidState().getType() == expectedFluidState.getFluidState().getType()) {
                                blocks.put(pos, existing);
                                for (SimplePredicate limit : predicate.limited) {
                                    limit.testLimited(worldState);
                                }
                                continue;
                            }
                        }

                        ItemStack found = null;
                        int foundSlot = -1;
                        PatternAutoBuildPlacement.Source source = PatternAutoBuildPlacement.Source.CREATIVE;
                        int sourceSlot = -1;
                        if (!player.isCreative()) {
                            for (int i = 0; i < player.getInventory().items.size(); i++) {
                                ItemStack itemStack = player.getInventory().items.get(i);
                                if (itemStack.isEmpty()) continue;
                                if (reservedPerSlot != null && reservedPerSlot[i] >= itemStack.getCount()) continue;
                                if (matchesAny(candidates, itemStack)) {
                                    found = itemStack.copy();
                                    foundSlot = i;
                                    source = PatternAutoBuildPlacement.Source.PLAYER_INVENTORY;
                                    if (reservedPerSlot != null) {
                                        reservedPerSlot[i]++;
                                    }
                                    break;
                                }
                            }

                            if (found == null && boundFluidHandler != null) {
                                FluidStack request = toRequiredFluidStack(expectedFluidInfo);
                                if (request != null && boundFluidHandler.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount() >= request.getAmount()) {
                                    ItemStack bucketCandidate = findBucketCandidate(candidates, request.getFluid());
                                    if (bucketCandidate != null) {
                                        found = bucketCandidate.copyWithCount(1);
                                        source = PatternAutoBuildPlacement.Source.BOUND_FLUID_HANDLER;
                                    }
                                }
                            }

                            if (found == null && boundItemHandler != null) {
                                ItemStack match = findMatchingStackInHandler(boundItemHandler, candidates);
                                if (!match.isEmpty()) {
                                    found = match.copyWithCount(1);
                                    source = PatternAutoBuildPlacement.Source.BOUND_ITEM_HANDLER;
                                }
                            }
                        } else {
                            for (ItemStack candidate : candidates) {
                                found = candidate.copy();
                                if (!found.isEmpty() && (found.getItem() instanceof BlockItem || found.getItem() instanceof BucketItem)) {
                                    source = PatternAutoBuildPlacement.Source.CREATIVE;
                                    break;
                                }
                                found = null;
                            }
                        }
                        if (found == null) {
                            continue;
                        }

                        BlockInfo expectedInfo = null;
                        boolean rotatePlacementState = true;
                        for (BlockInfo info : candidateInfos) {
                            if (ItemStack.isSameItemSameTags(info.getItemStackForm(), found)) {
                                expectedInfo = info;
                                rotatePlacementState = candidateRotation.getOrDefault(info, true);
                                break;
                            }
                        }

                        boolean expectsFluid = (found.getItem() instanceof BucketItem)
                                || (expectedInfo != null && expectedInfo.getBlockState() != null && hasAnyFluid(expectedInfo.getBlockState()));
                        if (expectsFluid) {
                            if (!emptyBlock && !existingHasFluid) {
                                continue;
                            }
                            fluidPlacements.add(new PatternAutoBuildPlacement(pos, found, foundSlot, expectedInfo, rotation, rotatePlacementState, source, sourceSlot));
                        } else {
                            nonFluidPlacements.add(new PatternAutoBuildPlacement(pos, found, foundSlot, expectedInfo, rotation, rotatePlacementState, source, sourceSlot));
                        }
                    }
                }
                z++;
            }
        }

        if (slowBuild && player instanceof ServerPlayer serverPlayer) {
            List<PatternAutoBuildPlacement> all = new ArrayList<>(nonFluidPlacements.size() + fluidPlacements.size());
            all.addAll(nonFluidPlacements);
            all.addAll(fluidPlacements);
            SlowAutoBuildScheduler.replace(serverPlayer, world.dimension(), all, boundItemHandler, boundFluidHandler);
            return;
        }

        for (PatternAutoBuildPlacement p : nonFluidPlacements) {
            AutoBuildPlacementExecutor.executePlacement(player, world, p, blocks, boundItemHandler, boundFluidHandler);
        }
        for (PatternAutoBuildPlacement p : fluidPlacements) {
            AutoBuildPlacementExecutor.executePlacement(player, world, p, blocks, boundItemHandler, boundFluidHandler);
        }

        blocks.forEach((pos, block) -> {
            if (block instanceof IMultiController) return;
            if (block instanceof BlockState state) {
                world.setBlock(pos, state, 3);
            } else if (block instanceof IMachine machine) {
                world.setBlock(pos, machine.getBlockState(), 3);
            }
        });
    }

    /**
     * Pattern-index overload for callers that select from multiple patterns.
     *
     * <p>The current implementation ignores {@code patternIndex} because a
     * {@link BlockPattern} instance already represents one compiled pattern.</p>
     *
     * @param player       player requesting auto-build
     * @param worldState   pattern state for the target controller
     * @param patternIndex selected pattern index from a higher-level pattern list
     */
    public void autoBuild(Player player, MultiblockState worldState, int patternIndex) {
        autoBuild(player, worldState);
    }

    private static boolean hasAnyFluid(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    private static boolean matchesAny(List<ItemStack> candidates, ItemStack stack) {
        ItemStack s = Objects.requireNonNull(stack);
        for (ItemStack candidate : candidates) {
            if (ItemStack.isSameItemSameTags(Objects.requireNonNull(candidate), s)) return true;
        }
        return false;
    }

    private static void recordCandidateRotation(Map<BlockInfo, Boolean> rotations, BlockInfo[] infos, boolean rotateExpectedState) {
        if (infos == null) return;
        for (BlockInfo info : infos) {
            if (info != null) {
                rotations.putIfAbsent(info, rotateExpectedState);
            }
        }
    }

    private static BlockInfo pickExpectedFluidInfo(List<BlockInfo> infos) {
        for (BlockInfo info : infos) {
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null) continue;
            if (state.getBlock() instanceof LiquidBlock) return info;
        }
        return null;
    }

    private static FluidStack toRequiredFluidStack(BlockInfo expected) {
        if (expected == null) return null;
        BlockState state = expected.getBlockState();
        if (state == null) return null;
        if (!hasAnyFluid(state)) return null;
        var fluid = state.getFluidState().getType();
        if (fluid == null) return null;
        return new FluidStack(fluid, 1000);
    }

    private static ItemStack findBucketCandidate(List<ItemStack> candidates, net.minecraft.world.level.material.Fluid fluid) {
        for (ItemStack c : candidates) {
            if (c == null || c.isEmpty()) continue;
            if (c.getItem() instanceof BucketItem b && b.getFluid() == fluid) {
                return c;
            }
        }
        return null;
    }

    private static ItemStack findMatchingStackInHandler(IItemHandler handler, List<ItemStack> candidates) {
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty()) continue;
            if (inSlot.getItem() instanceof BucketItem) continue;
            if (matchesAny(candidates, inSlot)) {
                return inSlot.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<IItemHandler> collectItemHandlers(BlockEntity be) {
        return AutoBuildPlacementExecutor.collectItemHandlers(be);
    }

    private static List<IFluidHandler> collectFluidHandlers(BlockEntity be) {
        return AutoBuildPlacementExecutor.collectFluidHandlers(be);
    }

    private static Rotation computeRotation(BlockPattern pattern, Direction currentFacing) {
        Direction base = pattern.mbd2$getBaseFacing();
        if (base == null) return Rotation.NONE;
        return PatternStateRotation.horizontalRotation(base, currentFacing);
    }

    /**
     * Builds a preview block grid for a set of aisle repetitions.
     *
     * <p>Side effects: none on the world. Candidate suppliers may be evaluated,
     * and block states are rotated from the pattern base facing toward north so
     * the preview has a consistent orientation.</p>
     *
     * @param repetition repetition count per aisle; each entry should be within
     *                   the aisle's min/max range
     * @return dense preview grid indexed by normalized x/y/z coordinates
     */
    public BlockInfo[][][] getPreview(int[] repetition) {
        Rotation previewRotation = PatternStateRotation.horizontalRotation(mbd2$getBaseFacing(), Direction.NORTH);
        Map<SimplePredicate, Integer> cacheGlobal = new HashMap<>();
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int l = 0, x = 0; l < this.fingerLength; l++) {
            for (int r = 0; r < repetition[l]; r++) {
                //Checking single slice
                Map<SimplePredicate, Integer> cacheLayer = new HashMap<>();
                for (int y = 0; y < this.thumbLength; y++) {
                    for (int z = 0; z < this.palmLength; z++) {
                        var predicate = this.blockMatches[l][y][z];
                        BlockInfo info = null;
                        boolean rotatePreviewState = true;
                        if (predicate.isController) {
                            info = new ControllerBlockInfo(Direction.NORTH);
                            rotatePreviewState = false;
                        } else {
                            boolean find = false;
                            BlockInfo[] infos = null;
                            for (SimplePredicate limit : predicate.limited) { // check layer and previewCount
                                if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != Direction.NORTH)
                                    continue;
                                if (limit.minLayerCount > 0) {
                                    if (!cacheLayer.containsKey(limit)) {
                                        cacheLayer.put(limit, 1);
                                    } else if (cacheLayer.get(limit) < limit.minLayerCount) {
                                        cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                                    } else {
                                        continue;
                                    }
                                    if (cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                                        if (!cacheGlobal.containsKey(limit)) {
                                            cacheGlobal.put(limit, 1);
                                        } else if (cacheGlobal.get(limit) < limit.previewCount) {
                                            cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                        } else {
                                            continue;
                                        }
                                    }
                                } else {
                                    continue;
                                }
                                infos = limit.candidates == null ? null : limit.candidates.get();
                                rotatePreviewState = !limit.controllerFront.isEnable();
                                find = true;
                                break;
                            }
                            if (!find) { // check global and previewCount
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != Direction.NORTH)
                                        continue;
                                    if (limit.minCount == -1 && limit.previewCount == -1) continue;
                                    if (cacheGlobal.getOrDefault(limit, 0) < limit.previewCount) {
                                        if (!cacheGlobal.containsKey(limit)) {
                                            cacheGlobal.put(limit, 1);
                                        } else if (cacheGlobal.get(limit) < limit.previewCount) {
                                            cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                        } else {
                                            continue;
                                        }
                                    } else if (limit.minCount > 0) {
                                        if (!cacheGlobal.containsKey(limit)) {
                                            cacheGlobal.put(limit, 1);
                                        } else if (cacheGlobal.get(limit) < limit.minCount) {
                                            cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    rotatePreviewState = !limit.controllerFront.isEnable();
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) { // check common with previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.controllerFront.isEnable() && common.controllerFront.getValue() != Direction.NORTH)
                                        continue;
                                    if (common.previewCount > 0) {
                                        if (!cacheGlobal.containsKey(common)) {
                                            cacheGlobal.put(common, 1);
                                        } else if (cacheGlobal.get(common) < common.previewCount) {
                                            cacheGlobal.put(common, cacheGlobal.get(common) + 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = common.candidates == null ? null : common.candidates.get();
                                    rotatePreviewState = !common.controllerFront.isEnable();
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) { // check without previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.controllerFront.isEnable() && common.controllerFront.getValue() != Direction.NORTH)
                                        continue;
                                    if (common.previewCount == -1) {
                                        infos = common.candidates == null ? null : common.candidates.get();
                                        rotatePreviewState = !common.controllerFront.isEnable();
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) { // check max
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != Direction.NORTH)
                                        continue;
                                    if (limit.previewCount != -1) {
                                        continue;
                                    } else if (limit.maxCount != -1 || limit.maxLayerCount != -1) {
                                        if (cacheGlobal.getOrDefault(limit, 0) < limit.maxCount) {
                                            if (!cacheGlobal.containsKey(limit)) {
                                                cacheGlobal.put(limit, 1);
                                            } else {
                                                cacheGlobal.put(limit, cacheGlobal.get(limit) + 1);
                                            }
                                        } else if (cacheLayer.getOrDefault(limit, 0) < limit.maxLayerCount) {
                                            if (!cacheLayer.containsKey(limit)) {
                                                cacheLayer.put(limit, 1);
                                            } else {
                                                cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                                            }
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }

                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    rotatePreviewState = !limit.controllerFront.isEnable();
                                    break;
                                }
                            }
                            info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[0];
                        }
                        BlockPos pos = setActualRelativeOffset(z, y, x, Direction.NORTH);

                        info = rotatePreviewInfo(info, previewRotation, rotatePreviewState);
                        blocks.put(pos, info);
                        minX = Math.min(pos.getX(), minX);
                        minY = Math.min(pos.getY(), minY);
                        minZ = Math.min(pos.getZ(), minZ);
                        maxX = Math.max(pos.getX(), maxX);
                        maxY = Math.max(pos.getY(), maxY);
                        maxZ = Math.max(pos.getZ(), maxZ);
                    }
                }
                x++;
            }
        }
        var result = new BlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        int finalMinX = minX;
        int finalMinY = minY;
        int finalMinZ = minZ;
        blocks.forEach((pos, info) -> result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info);
        return result;
    }

    private static BlockInfo rotatePreviewInfo(BlockInfo info, Rotation rotation, boolean rotatePreviewState) {
        if (!rotatePreviewState || rotation == Rotation.NONE || info == null || info == BlockInfo.EMPTY || info instanceof ControllerBlockInfo) {
            return info;
        }

        BlockState state = info.getBlockState();
        if (state == null) {
            return info;
        }

        BlockState rotatedState = PatternStateRotation.rotate(state, rotation);
        if (rotatedState == state) {
            return info;
        }

        var rotatedInfo = new BlockInfo();
        rotatedInfo.deserializeNBT(info.serializeNBT());
        rotatedInfo.setHasBlockEntity(info.hasBlockEntity());
        rotatedInfo.setBlockState(rotatedState);
        return rotatedInfo;
    }


    /**
     * Converts pattern-relative coordinates into facing-aware world offsets.
     *
     * @param x      pattern x coordinate
     * @param y      pattern y coordinate
     * @param z      pattern z coordinate
     * @param facing controller facing used to resolve relative directions
     * @return relative block offset from the controller/anchor position
     */
    private BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing) {
        int[] c0 = new int[]{x, y, z}, c1 = new int[3];
        for (int i = 0; i < 3; i++) {
            switch (structureDir[i].getActualFacing(facing)) {
                case UP -> c1[1] = c0[i];
                case DOWN -> c1[1] = -c0[i];
                case WEST -> c1[0] = -c0[i];
                case EAST -> c1[0] = c0[i];
                case NORTH -> c1[2] = -c0[i];
                case SOUTH -> c1[2] = c0[i];
            }
        }
        return new BlockPos(c1[0], c1[1], c1[2]);
    }
}
