package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MultiblockState.class, remap = false)
public interface MultiblockStateAccessors {
    @Invoker("clean")
    void mbd2thread$clean();

    @Invoker("update")
    boolean mbd2thread$update(BlockPos pos, TraceabilityPredicate predicate);
}

