package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.non_coffee.mbd2thread.duck.BlockPatternBaseFacingAccess;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BlockPattern.class, remap = false)
public class BlockPatternBaseFacingMixin implements BlockPatternBaseFacingAccess {
    @Unique
    private Direction mbd2thread$baseFacing = Direction.NORTH;

    @Override
    public Direction mbd2thread$getBaseFacing() {
        return mbd2thread$baseFacing;
    }

    @Override
    public void mbd2thread$setBaseFacing(Direction facing) {
        if (facing == null) {
            this.mbd2thread$baseFacing = Direction.NORTH;
            return;
        }
        this.mbd2thread$baseFacing = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
    }
}
