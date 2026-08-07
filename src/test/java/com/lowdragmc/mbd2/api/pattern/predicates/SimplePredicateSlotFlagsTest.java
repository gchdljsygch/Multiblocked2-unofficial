package com.lowdragmc.mbd2.api.pattern.predicates;

import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplePredicateSlotFlagsTest {

    @Test
    void slotNamedPredicateStillAppliesRenderAndOpenUiMasks() throws ReflectiveOperationException {
        BlockPos position = new BlockPos(3, 7, -2);
        MultiblockState state = new MultiblockState(null, BlockPos.ZERO);
        setCurrentPosition(state, position);

        SimplePredicate predicate = new SimplePredicate(ignored -> true, null);
        predicate.slotName = "input";
        predicate.disableRenderFormed = true;
        predicate.allowOpenUI = true;

        assertTrue(predicate.test(state));

        Map<Long, Set<String>> slots = state.getMatchContext().get("slots");
        LongOpenHashSet renderMask = state.getMatchContext().get("renderMask");
        LongOpenHashSet openUiMask = state.getMatchContext().get("openUIMask");
        assertEquals(Set.of("input"), slots.get(position.asLong()));
        assertTrue(renderMask.contains(position.asLong()));
        assertTrue(openUiMask.contains(position.asLong()));
    }

    private static void setCurrentPosition(MultiblockState state, BlockPos position) throws ReflectiveOperationException {
        Field field = MultiblockState.class.getDeclaredField("pos");
        field.setAccessible(true);
        field.set(state, position);
    }
}
