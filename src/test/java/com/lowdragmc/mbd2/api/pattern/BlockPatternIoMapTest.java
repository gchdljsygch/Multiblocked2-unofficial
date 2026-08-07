package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPatternIoMapTest {

    @Test
    void storesOnlyNonDefaultIoOverrides() {
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        BlockPattern pattern = new BlockPattern(new TraceabilityPredicate[][][]{{{controller}}},
                new RelativeDirection[]{RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT},
                new int[][]{{1, 1}}, new int[]{0, 0, 0, 0, 0});
        InMemoryMatchState state = new InMemoryMatchState();

        assertTrue(pattern.checkPatternAt(state, BlockPos.ZERO, Direction.NORTH, true,
                (worldState, predicate) -> {
                    worldState.io = IO.BOTH;
                    return true;
                }));
        assertNull(state.getMatchContext().get("ioMap"),
                "default IO is supplied by consumers when no override is stored");

        assertTrue(pattern.checkPatternAt(state, BlockPos.ZERO, Direction.NORTH, true,
                (worldState, predicate) -> {
                    worldState.io = IO.IN;
                    return true;
                }));
        Long2ObjectMap<IO> ioMap = state.getMatchContext().get("ioMap");
        assertEquals(1, ioMap.size());
        assertEquals(IO.IN, ioMap.get(BlockPos.ZERO.asLong()));
    }

    @Test
    void storesAndCopiesPredicateCacheWithPackedPositions() {
        TraceabilityPredicate controller = new TraceabilityPredicate().setController();
        BlockPattern pattern = new BlockPattern(new TraceabilityPredicate[][][]{{{controller}}},
                new RelativeDirection[]{RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT},
                new int[][]{{1, 1}}, new int[]{0, 0, 0, 0, 0});
        InMemoryMatchState state = new InMemoryMatchState();

        assertTrue(pattern.checkPatternAt(state, BlockPos.ZERO, Direction.NORTH, true,
                (worldState, predicate) -> {
                    worldState.io = IO.BOTH;
                    return true;
                }));
        Long2ObjectMap<TraceabilityPredicate> predicates = state.getMatchContext().get("predicates");
        assertEquals(1, predicates.size());
        assertSame(controller, predicates.get(BlockPos.ZERO.asLong()));

        state.commitCache();
        Long2ObjectMap<TraceabilityPredicate> formedPredicates = state.getFormedMatchContext().get("predicates");
        assertEquals(1, formedPredicates.size());
        assertSame(controller, formedPredicates.get(BlockPos.ZERO.asLong()));
    }

    private static final class InMemoryMatchState extends MultiblockState {

        private InMemoryMatchState() {
            super(null, BlockPos.ZERO);
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
