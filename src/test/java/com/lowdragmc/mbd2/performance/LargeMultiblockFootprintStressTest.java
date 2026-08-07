package com.lowdragmc.mbd2.performance;

import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the compiled-pattern guard against many pathological, large
 * multiblock definitions without materializing their full world volume.
 */
@Tag("performance")
class LargeMultiblockFootprintStressTest {

    private static final int REQUIRED_MACHINE_COUNT = 100;
    private static final int MACHINE_COUNT = Integer.getInteger(
            "mbd2.stress.largeMultiblockMachineCount", REQUIRED_MACHINE_COUNT);
    private static final int WIDTH = 513;
    private static final int HEIGHT = 513;
    private static final int DEPTH = 257;
    private static final int DETECTION_DEPTH = 3;
    private static final long LOGICAL_BLOCK_COUNT = (long) WIDTH * HEIGHT * DEPTH;
    private static final int DETECTION_BLOCK_COUNT = WIDTH * HEIGHT * DETECTION_DEPTH;
    private static final RelativeDirection[] STRUCTURE_DIRECTIONS = {
            RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT
    };
    private static final int[][] AISLE_REPETITIONS = {
            {1, 1}, {DEPTH - 1, DEPTH - 1}
    };
    private static final int[] CENTER_OFFSET = {0, 0, 0, 0, 0};
    private static final BlockPattern DETECTION_PATTERN = createLargestSupportedDetectionPattern();

    @Test
    void rejectsOneHundredDefinitionsLargerThan512x512x256BeforeWorldScanning() {
        requireStressMachineCount();
        assertTrue(LOGICAL_BLOCK_COUNT > BlockPattern.MAX_EXPANDED_PATTERN_BLOCKS,
                "the fixture must exceed the compiled-pattern block limit");

        TraceabilityPredicate[][][] template = createLargePatternTemplate();
        int[] rejectedDefinitions = {0};
        StressTestSupport.measure("multiblock-513x513x257-definition-guard", MACHINE_COUNT, 1, MACHINE_COUNT, () -> {
            for (int index = 0; index < MACHINE_COUNT; index++) {
                try {
                    new BlockPattern(template, STRUCTURE_DIRECTIONS, AISLE_REPETITIONS, CENTER_OFFSET);
                } catch (IllegalArgumentException exception) {
                    if (!exception.getMessage().startsWith("Expanded pattern exceeds")) {
                        throw exception;
                    }
                    rejectedDefinitions[0]++;
                }
            }
        });

        assertEquals(MACHINE_COUNT, rejectedDefinitions[0],
                "every oversized definition must be rejected before it can trigger a world scan");
    }

    /**
     * Runs the real matcher on the largest accepted pattern that retains a
     * 513 by 513 cross-section. The 513 by 513 by 257 fixture is intentionally
     * rejected above, so it cannot safely enter this phase.
     */
    @Test
    void detectsOneHundredLargestSupportedPatternsThroughTheMatcher() {
        requireStressMachineCount();
        assertTrue(DETECTION_BLOCK_COUNT <= BlockPattern.MAX_EXPANDED_PATTERN_BLOCKS,
                "the detection fixture must remain within the compiled-pattern block limit");

        warmUpMatcher();
        long[] checkedPositions = {0};
        StressTestSupport.measure("multiblock-513x513x3-full-detection", MACHINE_COUNT, 1,
                (long) MACHINE_COUNT * DETECTION_BLOCK_COUNT, () -> {
                    for (int index = 0; index < MACHINE_COUNT; index++) {
                        var state = new InMemoryMatchState(new BlockPos(index * (WIDTH + 1), 64, 0));
                        if (!DETECTION_PATTERN.checkPatternAt(state, state.controllerPos, Direction.NORTH, true,
                                LargeMultiblockFootprintStressTest::matchesWithDefaultIo)) {
                            throw new AssertionError("large detection fixture did not match controller " + index);
                        }
                        if (state.getCheckedPositionCount() != DETECTION_BLOCK_COUNT) {
                            throw new AssertionError("matcher stopped after " + state.getCheckedPositionCount()
                                    + " positions for controller " + index);
                        }
                        checkedPositions[0] += state.getCheckedPositionCount();
                        state.discardTransientMatchData();
                    }
                });

        assertEquals((long) MACHINE_COUNT * DETECTION_BLOCK_COUNT, checkedPositions[0],
                "the matcher must visit every position for every large controller");
    }

    private static TraceabilityPredicate[][][] createLargePatternTemplate() {
        TraceabilityPredicate ordinary = new TraceabilityPredicate();
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        var predicates = new TraceabilityPredicate[2][HEIGHT][WIDTH];
        for (TraceabilityPredicate[][] aisle : predicates) {
            for (TraceabilityPredicate[] row : aisle) {
                Arrays.fill(row, ordinary);
            }
        }
        predicates[0][0][0] = controller;
        return predicates;
    }

    private static BlockPattern createLargestSupportedDetectionPattern() {
        TraceabilityPredicate ordinary = new TraceabilityPredicate();
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        var predicates = new TraceabilityPredicate[DETECTION_DEPTH][HEIGHT][WIDTH];
        for (TraceabilityPredicate[][] aisle : predicates) {
            for (TraceabilityPredicate[] row : aisle) {
                Arrays.fill(row, ordinary);
            }
        }
        predicates[0][0][0] = controller;
        return new BlockPattern(predicates, STRUCTURE_DIRECTIONS,
                new int[][]{{1, 1}, {1, 1}, {1, 1}}, CENTER_OFFSET);
    }

    private static void warmUpMatcher() {
        for (int index = 0; index < 4; index++) {
            var state = new InMemoryMatchState(new BlockPos(index * (WIDTH + 1), 64, 0));
            if (!DETECTION_PATTERN.checkPatternAt(state, state.controllerPos, Direction.NORTH, true,
                    LargeMultiblockFootprintStressTest::matchesWithDefaultIo)) {
                throw new AssertionError("large detection warmup fixture did not match controller " + index);
            }
            state.discardTransientMatchData();
        }
    }

    private static boolean matchesWithDefaultIo(MultiblockState state, TraceabilityPredicate predicate) {
        state.io = IO.BOTH;
        return true;
    }

    private static void requireStressMachineCount() {
        if (MACHINE_COUNT < REQUIRED_MACHINE_COUNT) {
            throw new IllegalStateException("Large multiblock stress tests require at least "
                    + REQUIRED_MACHINE_COUNT + " definitions; configured " + MACHINE_COUNT);
        }
    }

    private static final class InMemoryMatchState extends MultiblockState {

        private int checkedPositionCount;

        private InMemoryMatchState(BlockPos controllerPos) {
            super(null, controllerPos);
            setCommitSuccessfulMatches(false);
        }

        @Override
        protected boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
            checkedPositionCount++;
            this.predicate = predicate;
            setError(null);
            return true;
        }

        private int getCheckedPositionCount() {
            return checkedPositionCount;
        }

        private void discardTransientMatchData() {
            clean();
        }

        @Override
        public BlockEntity getTileEntity() {
            return null;
        }
    }
}
