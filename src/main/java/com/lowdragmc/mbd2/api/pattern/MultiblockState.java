package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.block.ProxyPartBlock;
import com.lowdragmc.mbd2.api.blockentity.ProxyPartBlockEntity;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.pattern.error.PatternError;
import com.lowdragmc.mbd2.api.pattern.error.PatternStringError;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.api.pattern.util.PatternMatchContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mutable working state for one multiblock controller's pattern checks.
 *
 * <p>The business goal is to reuse one state object across structure matching,
 * cache invalidation, diagnostics, part discovery, and block-change handling.
 * Pattern checks may run under a controller lock and can be triggered from async
 * validation; callers must not share the same state object across concurrent
 * checks without external synchronization.</p>
 */
public class MultiblockState {
    public final static PatternError UNLOAD_ERROR = new PatternStringError("mbd2.multiblock.pattern.error.chunk");
    public final static PatternError UNINIT_ERROR = new PatternStringError("mbd2.multiblock.pattern.error.init");

    private BlockPos pos;
    private BlockState blockState;
    private BlockEntity tileEntity;
    private boolean tileEntityInitialized;
    @Getter
    private final PatternMatchContext matchContext;
    @Getter
    private Map<SimplePredicate, Integer> globalCount;
    @Getter
    private Map<SimplePredicate, Integer> layerCount;
    public TraceabilityPredicate predicate;
    public IO io;
    public PatternError error;
    @Getter
    public final Level world;
    public final BlockPos controllerPos;
    public IMultiController lastController;
    @Getter
    private boolean isInternalStructureForming;
    @Getter
    private boolean isInternalStructureInvaliding;
    @Getter
    private Direction patternFacing = Direction.NORTH;
    @Getter
    private Direction patternBaseFacing = Direction.NORTH;
    @Getter
    @Nullable
    private BlockPattern matchedPattern;
    @Getter
    private int matchedPatternIndex = -1;

    // persist
    public LongOpenHashSet cache;
    private LongOpenHashSet formedCache = new LongOpenHashSet();
    private PatternMatchContext formedMatchContext = new PatternMatchContext();
    private boolean hasCommittedMatch;
    private boolean commitSuccessfulMatches = true;

    /**
     * Creates pattern state for a controller position in a level.
     *
     * @param world level containing the controller and structure blocks
     * @param controllerPos controller block position
     */
    public MultiblockState(Level world, BlockPos controllerPos) {
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
    }

    /**
     * Clears transient match data before a new pattern check.
     *
     * <p>Side effects: resets the match context, global/layer predicate counts,
     * and working matched-position cache. The committed formed cache used by
     * world-change invalidation is preserved until a new match succeeds or the
     * structure invalidates.</p>
     */
    protected void clean() {
        this.matchContext.reset();
        this.globalCount = new HashMap<>();
        this.layerCount = new HashMap<>();
        cache = new LongOpenHashSet();
    }

    /**
     * Records the facing context for the current pattern check.
     *
     * @param facing controller/front facing being tested; {@code null} becomes
     * north
     * @param baseFacing pattern base facing used for rotation; {@code null}
     * becomes north
     */
    protected void setPatternContext(Direction facing, Direction baseFacing) {
        this.patternFacing = facing == null ? Direction.NORTH : facing;
        this.patternBaseFacing = baseFacing == null ? Direction.NORTH : baseFacing;
    }

    /**
     * Stores the matched pattern without an explicit multi-pattern index.
     *
     * @param matchedPattern matched pattern, or {@code null} when no pattern
     * matched
     */
    public void setMatchedPattern(@Nullable BlockPattern matchedPattern) {
        setMatchedPattern(matchedPattern, matchedPattern == null ? -1 : 0);
    }

    /**
     * Stores the matched pattern and its index in a combined pattern list.
     *
     * @param matchedPattern matched pattern, or {@code null} when no pattern
     * matched
     * @param matchedPatternIndex index in the owning pattern list; ignored and
     * normalized to {@code -1} when {@code matchedPattern} is null
     */
    public void setMatchedPattern(@Nullable BlockPattern matchedPattern, int matchedPatternIndex) {
        this.matchedPattern = matchedPattern;
        this.matchedPatternIndex = matchedPattern == null ? -1 : matchedPatternIndex;
    }

    /**
     * Moves this state cursor to a pattern position.
     *
     * <p>Side effects: clears cached block state and block entity for the
     * previous position, stores the active predicate, and records an unload error
     * when the target position is not loaded.</p>
     *
     * @param posIn absolute world position being tested
     * @param predicate predicate associated with the position
     * @return {@code true} when the position is loaded and ready to test
     */
    protected boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
        this.pos = posIn;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.predicate = predicate;
        this.error = null;
        if (!world.isLoaded(posIn)) {
            error = UNLOAD_ERROR;
            return false;
        }
        return true;
    }

    /**
     * Tests a single predicate at an explicit position.
     *
     * <p>Business goal: support editor diagnostics and targeted checks without
     * running a full pattern. Side effects: clears transient match state and
     * updates the cursor, facing context, error, and match context.</p>
     *
     * @param pos position to test
     * @param predicate predicate to evaluate
     * @param patternFacing facing context for relative predicates
     * @param patternBaseFacing base facing context for rotated predicates
     * @return {@code true} when the position is loaded and the predicate matches
     */
    public boolean testPredicateAt(BlockPos pos, TraceabilityPredicate predicate, Direction patternFacing, Direction patternBaseFacing) {
        clean();
        setPatternContext(patternFacing, patternBaseFacing);
        return update(pos, predicate) && predicate.test(this);
    }

    /**
     * Resolves the controller for this state.
     *
     * <p>Side effects: caches the last resolved controller and sets
     * {@link #UNLOAD_ERROR} when the controller chunk is not loaded.</p>
     *
     * @return controller machine at {@link #controllerPos}, or {@code null} when
     * missing/unloaded
     */
    public IMultiController getController() {
        if (world.isLoaded(controllerPos)) {
            var machineOptional = IMachine.ofMachine(world, controllerPos);
            if (machineOptional.isPresent() && machineOptional.get() instanceof IMultiController controller) {
                return lastController = controller;
            }
        } else {
            error = UNLOAD_ERROR;
        }
        return null;
    }

    /**
     * Returns whether the last check has an error.
     *
     * @return {@code true} when {@link #error} is non-null
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Returns the stable match context for formed-structure consumers.
     * <p>
     * Pattern checks reuse and clear {@link #matchContext}; formed controllers must read the last successful match
     * snapshot so later failed checks do not drop part membership, IO maps, masks, or predicate caches.
     *
     * @return committed match context when available, otherwise the live check context
     */
    public PatternMatchContext getFormedMatchContext() {
        return hasCommittedMatch ? formedMatchContext : matchContext;
    }

    public boolean shouldCommitSuccessfulMatches() {
        return commitSuccessfulMatches;
    }

    public void setCommitSuccessfulMatches(boolean commitSuccessfulMatches) {
        this.commitSuccessfulMatches = commitSuccessfulMatches;
    }

    /**
     * Stores the current pattern error and back-links it to this state.
     *
     * @param error error to store, or {@code null} to clear the current error
     */
    public void setError(PatternError error) {
        this.error = error;
        if (error != null) {
            error.setWorldState(this);
        }
    }

    /**
     * Returns the block state at the current cursor position.
     *
     * <p>Side effects: lazily reads and caches the state from the level.</p>
     *
     * @return cached block state at {@link #getPos()}
     */
    public BlockState getBlockState() {
        if (this.blockState == null) {
            this.blockState = this.world.getBlockState(this.pos);
        }
        if (this.blockState == null) {
            System.out.printf("error");
        }
        return this.blockState;
    }

    /**
     * Returns the block state represented by the current cursor position.
     *
     * <p>Formed structures may replace matched blocks with proxy blocks for rendering. Predicates should still test
     * the original captured state, otherwise every proxy position would either fail immediately or match without
     * checking the actual block/tag/state requirement.</p>
     *
     * @return captured original state for proxy positions, otherwise the live block state
     */
    public BlockState getRepresentedBlockState() {
        BlockState state = getBlockState();
        if (state.getBlock() == ProxyPartBlock.BLOCK && getTileEntity() instanceof ProxyPartBlockEntity proxyPartBlockEntity &&
                proxyPartBlockEntity.getOriginalState() != null) {
            return proxyPartBlockEntity.getOriginalState();
        }
        return state;
    }

    /**
     * Returns the block entity at the current cursor position when present.
     *
     * <p>Side effects: lazily reads and caches the block entity once per cursor
     * position.</p>
     *
     * @return block entity, or {@code null} when the current block has none
     */
    @Nullable
    public BlockEntity getTileEntity() {
        if (!getBlockState().hasBlockEntity()) {
            return null;
        }
        if (this.tileEntity == null && !this.tileEntityInitialized) {
            this.tileEntity = this.world.getBlockEntity(this.pos);
            this.tileEntityInitialized = true;
        }

        return this.tileEntity;
    }

    /**
     * Returns the current cursor position.
     *
     * @return immutable copy of the current pattern-test position
     */
    public BlockPos getPos() {
        return this.pos.immutable();
    }

    /**
     * Reads the block state adjacent to the current cursor position.
     *
     * <p>Side effects: when the cursor is mutable, temporarily moves it and then
     * restores it before returning.</p>
     *
     * @param face adjacent direction
     * @return neighboring block state
     */
    public BlockState getOffsetState(Direction face) {
        if (pos instanceof BlockPos.MutableBlockPos) {
            ((BlockPos.MutableBlockPos) pos).move(face);
            BlockState blockState = world.getBlockState(pos);
            ((BlockPos.MutableBlockPos) pos).move(face.getOpposite());
            return blockState;
        }
        return world.getBlockState(this.pos.relative(face));
    }

    /**
     * Adds a position to the structure cache.
     *
     * @param pos matched structure position to track for future invalidation
     */
    public void addPosCache(BlockPos pos) {
        if (cache == null) {
            cache = new LongOpenHashSet();
        }
        cache.add(pos.asLong());
    }

    /**
     * Commits the current successful match data as the formed-structure snapshot.
     * <p>
     * Failed pattern checks may leave {@link #cache} and {@link #matchContext} containing only the positions visited
     * before failure. Keeping a separate committed snapshot prevents those temporary attempts from shrinking the
     * invalidation area or dropping matched parts of an already formed structure.
     */
    public void commitCache() {
        formedCache = cache == null ? new LongOpenHashSet() : new LongOpenHashSet(cache);
        formedMatchContext = copyMatchContext(matchContext);
        LongSet partPositions = formedMatchContext.getOrCreate("partPositions", LongOpenHashSet::new);
        if (cache != null) {
            for (long cachedPos : cache) {
                addPartPosition(partPositions, cachedPos);
            }
        }
        Map<Long, IO> ioMap = formedMatchContext.get("ioMap");
        if (ioMap != null) {
            for (long matchedPos : ioMap.keySet()) {
                addPartPosition(partPositions, matchedPos);
            }
        }
        for (long partPos : partPositions) {
            formedCache.add(partPos);
        }
        hasCommittedMatch = true;
    }

    /**
     * Captures the currently formed match data before a validating pattern check
     * overwrites it.
     */
    public FormedSnapshot createFormedSnapshot() {
        return new FormedSnapshot(
                hasCommittedMatch,
                matchedPattern,
                matchedPatternIndex,
                formedCache == null ? new LongOpenHashSet() : new LongOpenHashSet(formedCache),
                copyMatchContext(formedMatchContext));
    }

    /**
     * Checks whether the last successful pattern check committed the same
     * structure topology as a previously captured formed snapshot.
     */
    public boolean matchesFormedSnapshot(FormedSnapshot snapshot) {
        if (snapshot == null || snapshot.hasCommittedMatch() != hasCommittedMatch) {
            return false;
        }
        if (!hasCommittedMatch) {
            return false;
        }
        return snapshot.matchedPattern() == matchedPattern &&
                snapshot.matchedPatternIndex() == matchedPatternIndex &&
                Objects.equals(snapshot.formedCache(), formedCache) &&
                matchContextEquals(snapshot.formedMatchContext(), formedMatchContext);
    }

    private boolean matchContextEquals(PatternMatchContext left, PatternMatchContext right) {
        if (left.entrySet().size() != right.entrySet().size()) {
            return false;
        }
        for (var entry : left.entrySet()) {
            if (!right.containsKey(entry.getKey()) || !Objects.equals(entry.getValue(), right.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public record FormedSnapshot(boolean hasCommittedMatch,
                                 @Nullable BlockPattern matchedPattern,
                                 int matchedPatternIndex,
                                 LongOpenHashSet formedCache,
                                 PatternMatchContext formedMatchContext) {
    }

    private void addPartPosition(LongSet partPositions, long pos) {
        BlockPos blockPos = BlockPos.of(pos);
        if (!blockPos.equals(controllerPos)) {
            IMultiPart.ofPart(world, blockPos).ifPresent(part -> partPositions.add(pos));
        }
    }

    /**
     * Clears the committed formed-structure snapshot after invalidation.
     */
    public void clearCommittedCache() {
        formedCache.clear();
        formedMatchContext.reset();
        hasCommittedMatch = false;
        commitSuccessfulMatches = true;
    }

    @SuppressWarnings("unchecked")
    private PatternMatchContext copyMatchContext(PatternMatchContext source) {
        var result = new PatternMatchContext();
        for (var entry : source.entrySet()) {
            Object value = entry.getValue();
            result.set(entry.getKey(), switch (entry.getKey()) {
                case "parts" -> new HashSet<>((Set<IMultiPart>) value);
                case "predicates" -> new HashMap<>((Map<BlockPos, TraceabilityPredicate>) value);
                case "ioMap" -> new Long2ObjectOpenHashMap<>((Map<Long, IO>) value);
                case "slots" -> copySlots((Map<Long, Set<String>>) value);
                case "renderMask", "openUIMask", "partPositions" -> new LongOpenHashSet((LongSet) value);
                default -> value;
            });
        }
        return result;
    }

    private Map<Long, Set<String>> copySlots(Map<Long, Set<String>> source) {
        var result = new Long2ObjectOpenHashMap<Set<String>>();
        for (var entry : source.entrySet()) {
            result.put(entry.getKey().longValue(), new HashSet<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Checks whether a position is in this state's structure cache.
     *
     * @param pos position to query
     * @return {@code true} when cached
     */
    public boolean isPosInCache(BlockPos pos) {
        LongOpenHashSet source = getStableCache();
        return source != null && source.contains(pos.asLong());
    }

    /**
     * Returns cached structure positions.
     *
     * @return immutable-style collection copy of cached block positions; empty
     * when no cache exists
     */
    public Collection<BlockPos> getCache() {
        LongOpenHashSet source = getStableCache();
        if (source == null) {
            return java.util.Collections.emptyList();
        }
        return source.stream().map(BlockPos::of).collect(Collectors.toList());
    }

    @Nullable
    private LongOpenHashSet getStableCache() {
        return formedCache != null && !formedCache.isEmpty() ? formedCache : cache;
    }

    /**
     * Handles a server-side block-state change inside or near a formed
     * multiblock.
     *
     * <p>Business goal: keep structure mappings fresh after controller or part
     * changes. Preconditions: should run on the server thread. Side effects: may
     * invalidate a removed controller, re-check the pattern under the controller
     * lock, call structure formed/invalid hooks, update saved-data mappings, and
     * enqueue async rechecks. Proxy part block changes and internally triggered
     * formation/invalidation changes are ignored.</p>
     *
     * @param pos changed block position
     * @param state new block state
     */
    public void onBlockStateChanged(BlockPos pos, BlockState state) {
        if (world instanceof ServerLevel serverLevel) {
            if (pos.equals(controllerPos)) {
                if (lastController != null) {
                    if (!state.is(lastController.getBlockState().getBlock())) {
                        if (!isInternalStructureInvaliding) {
                            lastController.onStructureInvalid(true);
                            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                            mwsd.removeMapping(this);
                        }
                    }
                }
            } else if (state.getBlock() == ProxyPartBlock.BLOCK) {
                // ignore if it's a proxy part block
            } else {
                if (isInternalStructureForming || isInternalStructureInvaliding) {
                    // ignore if it's internal structure forming or invaliding
                    return;
                }
                IMultiController controller = getController();
                if (controller != null) {
                    if (!controller.isFormed()) {
                        return;
                    }
                    if (shouldInvalidateRenderedPosition(pos, state)) {
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.removeMapping(this);
                        isInternalStructureInvaliding = true;
                        controller.onStructureInvalid();
                        isInternalStructureInvaliding = false;
                        mwsd.addAsyncLogic(controller);
                        return;
                    }
                    // TODO vaBlocks
//                    if (controller.isFormed() && state.getBlock() instanceof ActiveBlock) {
//                        LongSet activeBlocks = getMatchContext().getOrDefault("vaBlocks", LongSets.emptySet());
//                        if (activeBlocks.contains(pos.asLong())) {
//                            // fine! it's caused by active blocks.
//                            // speed up here!
//                            return;
//                        }
//                    }
                    var previousSnapshot = createFormedSnapshot();
                    if (controller.checkPatternWithLock()) {
                        // Refresh only when the successful match changed the formed topology.
                        if (!matchesFormedSnapshot(previousSnapshot)) {
                            isInternalStructureForming = true;
                            try {
                                controller.onStructureFormed();
                            } finally {
                                isInternalStructureForming = false;
                            }
                        }
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.addMapping(this);
                        mwsd.removeAsyncLogic(controller);
                    } else {
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.removeMapping(this);
                        isInternalStructureInvaliding = true;
                        // invalid structure
                        controller.onStructureInvalid();
                        isInternalStructureInvaliding = false;
                        mwsd.addAsyncLogic(controller);
                    }
                }
            }
        }
    }

    private boolean shouldInvalidateRenderedPosition(BlockPos pos, BlockState state) {
        LongSet renderMask = getFormedMatchContext().get("renderMask");
        return renderMask != null && renderMask.contains(pos.asLong()) && state.getBlock() != ProxyPartBlock.BLOCK;
    }

}
