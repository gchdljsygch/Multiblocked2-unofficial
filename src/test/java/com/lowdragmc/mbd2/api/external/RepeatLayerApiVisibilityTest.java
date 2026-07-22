package com.lowdragmc.mbd2.api.external;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatLayerApiVisibilityTest {

    @Test
    void publicApiIsConsumableOutsidePatternAndMachinePackages() {
        BlockPattern pattern = new BlockPattern(new TraceabilityPredicate[][][]{
                {{new TraceabilityPredicate()}},
                {{new TraceabilityPredicate().setController()}}
        }, new RelativeDirection[]{RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT},
                new int[][]{{2, 3}, {1, 1}}, new int[]{0, 0, 1, 2, 3});

        List<BlockPattern.AisleRepeat> configured = pattern.getRepeatableAisles();
        assertEquals(List.of(new BlockPattern.AisleRepeat(0, 2, 3)), configured);
        assertArrayEquals(new int[]{2, 3}, pattern.getAisleRepetitionRange(0));

        MultiblockState unmatchedState = new MultiblockState(null, BlockPos.ZERO);
        assertArrayEquals(new int[0], unmatchedState.getMatchedAisleRepetitions());
        assertTrue(unmatchedState.getMatchedAisleRepetition(0).isEmpty());
        assertEquals(List.of(), unmatchedState.getMatchedRepeatableAisles());

        Function<IMultiController, int[]> allCounts = IMultiController::getMatchedAisleRepetitions;
        BiFunction<IMultiController, Integer, OptionalInt> oneCount = IMultiController::getMatchedAisleRepetition;
        Function<IMultiController, List<BlockPattern.MatchedAisleRepeat>> repeatedAisles =
                IMultiController::getMatchedRepeatableAisles;
        assertNotNull(allCounts);
        assertNotNull(oneCount);
        assertNotNull(repeatedAisles);
    }
}
