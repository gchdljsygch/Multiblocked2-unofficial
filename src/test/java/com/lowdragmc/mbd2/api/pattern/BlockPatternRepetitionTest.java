package com.lowdragmc.mbd2.api.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPatternRepetitionTest {

    @Test
    void rejectsInvalidRepeatRanges() {
        assertThrows(IllegalArgumentException.class, () -> BlockPattern.validateAisleRepetitionRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> BlockPattern.validateAisleRepetitionRange(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> BlockPattern.validateAisleRepetitionRange(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BlockPattern.validateAisleRepetitionRange(1, BlockPattern.MAX_AISLE_REPETITION + 1));
    }

    @Test
    void rejectsMultipleControllersAndARepeatableControllerAisle() {
        TraceabilityPredicate controllerA = new TraceabilityPredicate().setController();
        TraceabilityPredicate controllerB = new TraceabilityPredicate().setController();
        assertThrows(IllegalArgumentException.class, () -> new BlockPattern(
                new TraceabilityPredicate[][][]{{{controllerA}}, {{controllerB}}},
                structureDirections(), new int[][]{{1, 1}, {1, 1}}, new int[]{0, 0, 0, 0, 0}));

        assertThrows(IllegalArgumentException.class, () -> new BlockPattern(
                new TraceabilityPredicate[][][]{{{new TraceabilityPredicate().setController()}}},
                structureDirections(), new int[][]{{1, 2}}, new int[]{0, 0, 0, 0, 0}));
    }

    @Test
    void exposesDefensiveRepeatConfigurationAndCartesianCombinations() {
        BlockPattern pattern = createPattern();

        assertEquals(List.of(
                new BlockPattern.AisleRepeat(0, 1, 2),
                new BlockPattern.AisleRepeat(2, 2, 3)), pattern.getRepeatableAisles());
        assertEquals(4, pattern.getRepetitionCombinationCount());

        List<int[]> combinations = pattern.getAisleRepetitionCombinations();
        assertEquals(4, combinations.size());
        assertArrayEquals(new int[]{1, 1, 2}, combinations.get(0));
        assertArrayEquals(new int[]{1, 1, 3}, combinations.get(1));
        assertArrayEquals(new int[]{2, 1, 2}, combinations.get(2));
        assertArrayEquals(new int[]{2, 1, 3}, combinations.get(3));

        int[] range = pattern.getAisleRepetitionRange(0);
        int[][] ranges = pattern.getAisleRepetitionRanges();
        range[0] = 99;
        ranges[0][0] = 99;
        combinations.get(0)[0] = 99;
        assertArrayEquals(new int[]{1, 2}, pattern.getAisleRepetitionRange(0));
        assertArrayEquals(new int[]{1, 2}, pattern.getAisleRepetitionRanges()[0]);
        assertArrayEquals(new int[]{1, 1, 2}, pattern.getAisleRepetitionCombinations().get(0));
    }

    @SuppressWarnings("deprecation")
    @Test
    void legacyPublicArrayCannotBypassValidatedRepetitionConfiguration() {
        BlockPattern pattern = createPattern();

        pattern.aisleRepetitions[0][0] = 99;
        pattern.aisleRepetitions[1][1] = 99;

        assertArrayEquals(new int[]{1, 2}, pattern.getAisleRepetitionRange(0));
        assertArrayEquals(new int[]{1, 1}, pattern.getAisleRepetitionRange(1));
        assertEquals(4, pattern.getRepetitionCombinationCount());
    }

    @Test
    void integratedMatchBacktracksAndPublishesActualRepeatCounts() {
        TraceabilityPredicate first = new TraceabilityPredicate();
        TraceabilityPredicate repeated = new TraceabilityPredicate();
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        BlockPattern pattern = new BlockPattern(new TraceabilityPredicate[][][]{
                {{first}},
                {{repeated}},
                {{controller}}
        }, structureDirections(), new int[][]{{1, 1}, {1, 2}, {1, 1}}, new int[]{0, 0, 2, 2, 3});
        InMemoryMatchState state = new InMemoryMatchState();
        state.setCommitSuccessfulMatches(false);

        boolean matched = pattern.checkPatternAt(state, BlockPos.ZERO, Direction.NORTH, false,
                (worldState, predicate) -> {
                    int z = ((InMemoryMatchState) worldState).currentPos.getZ();
                    if (predicate == first) {
                        return z == 2;
                    }
                    if (predicate == repeated) {
                        return z == 1 || z == 0;
                    }
                    return predicate == controller && z == 0;
                });

        assertTrue(matched);
        assertArrayEquals(new int[]{1, 1, 1}, state.getMatchedAisleRepetitions());
        assertEquals(List.of(new BlockPattern.MatchedAisleRepeat(1, 1, 1, 2)),
                state.getMatchedRepeatableAisles());
    }

    @Test
    void fixedAislesMatchWithoutBacktrackingAndPublishConfiguredCounts() {
        TraceabilityPredicate first = new TraceabilityPredicate();
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        BlockPattern pattern = new BlockPattern(new TraceabilityPredicate[][][]{
                {{first}},
                {{controller}}
        }, structureDirections(), new int[][]{{1, 1}, {1, 1}}, new int[]{0, 0, 1, 1, 1});
        InMemoryMatchState state = new InMemoryMatchState();
        state.setCommitSuccessfulMatches(false);

        assertTrue(pattern.checkPatternAt(state, BlockPos.ZERO, Direction.NORTH, false,
                (worldState, predicate) -> {
                    int z = ((InMemoryMatchState) worldState).currentPos.getZ();
                    return predicate == first ? z == 1 : predicate == controller && z == 0;
                }));
        assertArrayEquals(new int[]{1, 1}, state.getMatchedAisleRepetitions());
    }

    @Test
    void retainsCommittedActualRepeatCountsAndReturnsDefensiveCopies() {
        BlockPattern pattern = createPattern();
        MultiblockState state = new MultiblockState(null, BlockPos.ZERO);
        state.clean();
        state.setMatchedPattern(pattern, 0);
        state.setMatchedAisleRepetitions(new int[]{2, 1, 3});
        state.commitCache();

        int[] actual = state.getMatchedAisleRepetitions();
        actual[0] = 99;
        state.setMatchedPattern(null);

        assertArrayEquals(new int[]{2, 1, 3}, state.getMatchedAisleRepetitions());
        assertEquals(2, state.getMatchedAisleRepetition(0).orElseThrow());
        assertTrue(state.getMatchedAisleRepetition(9).isEmpty());
        assertEquals(List.of(
                new BlockPattern.MatchedAisleRepeat(0, 2, 1, 2),
                new BlockPattern.MatchedAisleRepeat(2, 3, 2, 3)), state.getMatchedRepeatableAisles());

        state.clearCommittedCache();
        assertArrayEquals(new int[0], state.getMatchedAisleRepetitions());
    }

    @Test
    void rejectsCenterOffsetsThatDisagreeWithTheControllerOrRepeatRanges() {
        TraceabilityPredicate[][][] predicates = {
                {{new TraceabilityPredicate()}},
                {{new TraceabilityPredicate().setController()}}
        };
        int[][] ranges = {{1, 2}, {1, 1}};

        assertThrows(IllegalArgumentException.class, () -> new BlockPattern(
                predicates, structureDirections(), ranges, new int[]{0, 0, 0, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> new BlockPattern(
                predicates, structureDirections(), ranges, new int[]{0, 0, 1, 1, 1}));
    }

    @Test
    void matchSnapshotsRestoreBuiltInContextAndPositionCaches() {
        MultiblockState state = new MultiblockState(null, BlockPos.ZERO);
        state.clean();
        LongOpenHashSet renderMask = state.getMatchContext()
                .getOrCreate("renderMask", LongOpenHashSet::new);
        BlockPos retained = new BlockPos(1, 2, 3);
        BlockPos rolledBack = new BlockPos(4, 5, 6);
        renderMask.add(retained.asLong());
        state.addPosCache(retained);
        MultiblockState.MatchSnapshot snapshot = state.createMatchSnapshot();

        renderMask.add(rolledBack.asLong());
        state.addPosCache(rolledBack);
        state.restoreMatchSnapshot(snapshot);

        LongOpenHashSet restoredMask = state.getMatchContext().get("renderMask");
        assertEquals(1, restoredMask.size());
        assertTrue(restoredMask.contains(retained.asLong()));
        assertEquals(List.of(retained), state.getCache());
    }

    @Test
    void rejectsSelectionsOutsideTheConfiguredPreviewRange() {
        BlockPattern pattern = createPattern();

        assertThrows(IllegalArgumentException.class, () -> pattern.getPreview(new int[]{1, 1}));
        assertThrows(IllegalArgumentException.class, () -> pattern.getPreview(new int[]{0, 1, 2}));
        assertThrows(IllegalArgumentException.class, () -> pattern.getPreview(new int[]{1, 2, 2}));
    }

    private static BlockPattern createPattern() {
        return new BlockPattern(new TraceabilityPredicate[][][]{
                {{new TraceabilityPredicate()}},
                {{new TraceabilityPredicate().setController()}},
                {{new TraceabilityPredicate()}}
        }, structureDirections(), new int[][]{{1, 2}, {1, 1}, {2, 3}}, new int[]{0, 0, 1, 1, 2});
    }

    private static RelativeDirection[] structureDirections() {
        return new RelativeDirection[]{RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT};
    }

    private static final class InMemoryMatchState extends MultiblockState {
        private BlockPos currentPos = BlockPos.ZERO;

        private InMemoryMatchState() {
            super(null, BlockPos.ZERO);
        }

        @Override
        protected boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
            currentPos = posIn.immutable();
            this.predicate = predicate;
            setError(null);
            return true;
        }

        @Override
        public BlockEntity getTileEntity() {
            return null;
        }
    }
}
