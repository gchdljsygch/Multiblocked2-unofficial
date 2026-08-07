package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.performance.StressTestSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Exercises formed-structure position indexing independently from a live server
 * level. Each state keeps two cached positions, matching a minimal controller
 * and part layout.
 */
@Tag("performance")
class MultiblockWorldSavedData100kStressTest {

    private static final int CACHED_POSITIONS_PER_MACHINE = 2;

    @Test
    void indexesLooksUpAndRemovesOneHundredThousandFormedStructures() throws ReflectiveOperationException {
        StressTestSupport.requireStressScale();
        var savedData = createDetachedSavedData();
        var states = createStates();

        StressTestSupport.measure("multiblock-index-add", 1,
                (long) states.length * CACHED_POSITIONS_PER_MACHINE, () -> {
                    for (MultiblockState state : states) {
                        savedData.addMapping(state);
                    }
                });
        assertEquals(states.length, savedData.mapping.size(), "every controller must be indexed");
        assertEquals(states.length * CACHED_POSITIONS_PER_MACHINE, savedData.structureCachePosMapping.size(),
                "each cached controller position must have one index bucket");

        StressTestSupport.measure("multiblock-index-lookup", 1, states.length, () -> {
            for (int index = 0; index < states.length; index++) {
                var controllers = savedData.getControllerInPos(cachedPos(index, 0));
                if (controllers.length != 1 || controllers[0] != states[index]) {
                    throw new AssertionError("lookup did not return the mapped controller at index " + index);
                }
            }
        });
        assertSame(states[0], savedData.getControllerInPos(cachedPos(0, 0))[0]);

        StressTestSupport.measure("multiblock-index-remove", 1,
                (long) states.length * CACHED_POSITIONS_PER_MACHINE, () -> {
                    for (MultiblockState state : states) {
                        savedData.removeMapping(state);
                    }
                });
        assertEquals(0, savedData.mapping.size(), "controller mappings must be released during lifecycle removal");
        assertEquals(0, savedData.structureCachePosMapping.size(), "position mappings must be released during lifecycle removal");
    }

    private static MultiblockState[] createStates() {
        var states = new MultiblockState[StressTestSupport.MACHINE_COUNT];
        for (int index = 0; index < states.length; index++) {
            var state = new MultiblockState(null, controllerPos(index));
            state.cache = new LongOpenHashSet(CACHED_POSITIONS_PER_MACHINE);
            for (int cacheIndex = 0; cacheIndex < CACHED_POSITIONS_PER_MACHINE; cacheIndex++) {
                state.cache.add(cachedPos(index, cacheIndex).asLong());
            }
            states[index] = state;
        }
        return states;
    }

    private static BlockPos controllerPos(int index) {
        return new BlockPos(index & 0x3ff, 80, index >>> 10);
    }

    private static BlockPos cachedPos(int index, int cacheIndex) {
        return new BlockPos(index & 0x3ff, 64 + cacheIndex, index >>> 10);
    }

    private static MultiblockWorldSavedData createDetachedSavedData() throws ReflectiveOperationException {
        Constructor<MultiblockWorldSavedData> constructor = MultiblockWorldSavedData.class
                .getDeclaredConstructor(ServerLevel.class);
        constructor.setAccessible(true);
        return constructor.newInstance((ServerLevel) null);
    }
}
