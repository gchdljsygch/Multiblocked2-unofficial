package com.lowdragmc.mbd2.core.mixins.lowdraglib;

final class WidgetSlotItemTransferLimits {

    private WidgetSlotItemTransferLimits() {
    }

    static int effectiveStackLimit(int slotLimit, int itemLimit) {
        return Math.min(Math.max(slotLimit, 0), itemLimit);
    }
}
