package com.lowdragmc.mbd2.performance;

import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real pattern matcher with one isolated state per controller.
 */
@Tag("performance")
class BlockPattern100kMachineStressTest {

    private static final BlockPattern PATTERN = createPattern();

    @Test
    void checksOneHundredThousandIndependentMultiblocks() {
        StressTestSupport.requireStressScale();
        var states = new InMemoryMatchState[StressTestSupport.MACHINE_COUNT];
        for (int index = 0; index < states.length; index++) {
            states[index] = new InMemoryMatchState(new BlockPos(index & 0x3ff, 64, index >>> 10));
        }

        warmUp(states);
        int[] matches = {0};
        StressTestSupport.measure("multiblock-pattern-check", 1, states.length, () -> {
            for (InMemoryMatchState state : states) {
                if (PATTERN.checkPatternAt(state, state.controllerPos, Direction.NORTH, true,
                        (worldState, predicate) -> true)) {
                    matches[0]++;
                }
            }
        });

        assertEquals(states.length, matches[0], "every synthetic controller must match the shared pattern");
    }

    private static void warmUp(InMemoryMatchState[] states) {
        int warmupCount = Math.min(4_096, states.length);
        for (int index = 0; index < warmupCount; index++) {
            PATTERN.checkPatternAt(states[index], states[index].controllerPos, Direction.NORTH, true,
                    (worldState, predicate) -> true);
        }
    }

    private static BlockPattern createPattern() {
        TraceabilityPredicate ordinary = new TraceabilityPredicate();
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        TraceabilityPredicate[][][] predicates = {
                {{ordinary, ordinary}, {ordinary, ordinary}},
                {{ordinary, ordinary}, {ordinary, ordinary}},
                {{ordinary, ordinary}, {ordinary, controller}}
        };
        return new BlockPattern(predicates,
                new RelativeDirection[]{RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT},
                new int[][]{{1, 1}, {1, 1}, {1, 1}}, new int[]{1, 1, 2, 2, 2});
    }

    private static final class InMemoryMatchState extends MultiblockState {

        private InMemoryMatchState(BlockPos controllerPos) {
            super(null, controllerPos);
            setCommitSuccessfulMatches(false);
        }

        @Override
        protected boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
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
