package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.mbd2.performance.StressTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the thread-local render context updates that surround model rendering.
 * GPU draws and chunk rebuilds require an integrated client and are deliberately
 * outside this JVM-only workload.
 */
@Tag("performance")
class FusionModelData100kStressTest {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("mbd2", "block/stress_machine");

    @Test
    void pushesAndRestoresOneHundredThousandModelContexts() {
        StressTestSupport.requireStressScale();
        StressTestSupport.measure("render-model-context-update", 2,
                (long) StressTestSupport.MACHINE_COUNT * 2, () -> {
                    for (int index = 0; index < StressTestSupport.MACHINE_COUNT; index++) {
                        var position = new BlockPos(index & 0x3ff, 64, index >>> 10);
                        try (var modelContext = FusionModelDataHelper.pushModelContext(position, MODEL, null);
                             var faceContext = FusionModelDataHelper.suppressFace(Direction.NORTH)) {
                            if (!MODEL.equals(FusionModelDataHelper.getCurrentModelLocation()) ||
                                    !FusionModelDataHelper.isSuppressedFace(Direction.NORTH)) {
                                throw new AssertionError("render context was not visible at index " + index);
                            }
                        }
                    }
                });
        assertNull(FusionModelDataHelper.getCurrentModelLocation(), "model context must be restored after rendering");
        assertNull(FusionModelDataHelper.getSuppressedFace(), "face context must be restored after rendering");
    }
}
