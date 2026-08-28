package com.lowdragmc.mbd2.api.pattern;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockStateStructurePositionsTest {

    @Test
    void formedSnapshotsCopyAllStructurePositions() {
        MultiblockState state = new MultiblockState(null, BlockPos.ZERO);
        state.clean();
        LongOpenHashSet positions = state.getMatchContext().getOrCreate("structurePositions", LongOpenHashSet::new);
        positions.add(new BlockPos(1, 2, 3).asLong());

        MultiblockState.MatchSnapshot snapshot = state.createMatchSnapshot();
        positions.add(new BlockPos(4, 5, 6).asLong());
        state.restoreMatchSnapshot(snapshot);

        LongOpenHashSet restored = state.getMatchContext().get("structurePositions");
        assertEquals(1, restored.size());
        assertTrue(restored.contains(new BlockPos(1, 2, 3).asLong()));
    }
}
