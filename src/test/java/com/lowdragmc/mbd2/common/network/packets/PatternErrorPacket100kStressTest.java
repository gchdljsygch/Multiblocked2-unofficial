package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.mbd2.performance.StressTestSupport;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the concrete error-position packet codec used by multiblock sync.
 */
@Tag("performance")
class PatternErrorPacket100kStressTest {

    @Test
    void roundTripsOneHundredThousandPatternErrorPackets() {
        StressTestSupport.requireStressScale();
        var buffer = new FriendlyByteBuf(Unpooled.buffer(Long.BYTES));
        try {
            var inbound = new SPatternErrorPosPacket();
            StressTestSupport.measure("network-pattern-error-packet-codec", 2,
                    (long) StressTestSupport.MACHINE_COUNT * 2, () -> {
                        for (int index = 0; index < StressTestSupport.MACHINE_COUNT; index++) {
                            var position = new BlockPos(index & 0x3ff, 48 + (index & 15), index >>> 10);
                            var outbound = new SPatternErrorPosPacket();
                            outbound.pos = position;
                            buffer.clear();
                            outbound.encode(buffer);
                            inbound.decode(buffer);
                            if (!position.equals(inbound.pos)) {
                                throw new AssertionError("packet decode changed position at index " + index);
                            }
                        }
                    });
            assertEquals(0, buffer.readableBytes(), "each codec round trip must consume the complete packet payload");
        } finally {
            buffer.release();
        }
    }
}
