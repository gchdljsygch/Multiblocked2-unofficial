package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetSlotItemTransferMixinTest {

    @Test
    void configuredSlotLimitCapsVanillaInsertion() {
        assertEquals(1, WidgetSlotItemTransferLimits.effectiveStackLimit(1, 64));
        assertEquals(16, WidgetSlotItemTransferLimits.effectiveStackLimit(16, 64));
    }

    @Test
    void nativeItemLimitCapsOversizedStorageInteraction() {
        assertEquals(64, WidgetSlotItemTransferLimits.effectiveStackLimit(1_000, 64));
        assertEquals(1, WidgetSlotItemTransferLimits.effectiveStackLimit(1_000, 1));
    }

    @Test
    void invalidNegativeSlotLimitAllowsNoInsertion() {
        assertEquals(0, WidgetSlotItemTransferLimits.effectiveStackLimit(-1, 64));
    }
}
