package com.lowdragmc.mbd2.utils;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.Objects;

public final class PatternAutoBuildPlacement {
    public final BlockPos pos;
    public final ItemStack found;
    public final int foundSlot;
    public final BlockInfo expectedInfo;
    public final Rotation rotation;
    public final boolean rotateExpectedState;
    public final Source source;
    public final int sourceSlot;

    public PatternAutoBuildPlacement(BlockPos pos, ItemStack found, int foundSlot, BlockInfo expectedInfo, Rotation rotation, Source source, int sourceSlot) {
        this(pos, found, foundSlot, expectedInfo, rotation, true, source, sourceSlot);
    }

    public PatternAutoBuildPlacement(BlockPos pos, ItemStack found, int foundSlot, BlockInfo expectedInfo, Rotation rotation, boolean rotateExpectedState, Source source, int sourceSlot) {
        this.pos = Objects.requireNonNull(pos);
        this.found = Objects.requireNonNull(found);
        this.foundSlot = foundSlot;
        this.expectedInfo = expectedInfo;
        this.rotation = rotation == null ? Rotation.NONE : rotation;
        this.rotateExpectedState = rotateExpectedState;
        this.source = Objects.requireNonNull(source);
        this.sourceSlot = sourceSlot;
    }

    public enum Source {
        PLAYER_INVENTORY,
        BOUND_ITEM_HANDLER,
        BOUND_FLUID_HANDLER,
        CREATIVE
    }
}
