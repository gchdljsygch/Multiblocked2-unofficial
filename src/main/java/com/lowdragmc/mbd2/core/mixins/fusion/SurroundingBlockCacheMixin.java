package com.lowdragmc.mbd2.core.mixins.fusion;

import com.lowdragmc.mbd2.client.renderer.FusionModelDataHelper;
import com.supermartijn642.fusion.model.types.connecting.SurroundingBlockCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurroundingBlockCache.class, remap = false)
public class SurroundingBlockCacheMixin {

    @Shadow
    @Final
    private BlockAndTintGetter level;

    @Shadow
    @Final
    private BlockPos pos;

    @Shadow
    @Final
    private BlockState[] states;

    @Inject(method = "getState", at = @At("HEAD"), cancellable = true)
    private void mbd2$getStateByMbdModelPath(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }
        var index = x + 1 + (y + 1) * 3 + (z + 1) * 9;
        var otherPos = pos.offset(x, y, z);
        if (FusionModelDataHelper.shouldConnectByModelPath(level, pos, otherPos)) {
            FusionModelDataHelper.debugOnce("cache-model-path-hit-" + pos + "-" + otherPos,
                    "SurroundingBlockCache using center state for same model path, center={}, other={}",
                    pos, otherPos);
            cir.setReturnValue(states[13]);
        } else if (states[index] == states[13] && !level.getBlockState(otherPos).equals(states[13])) {
            states[index] = level.getBlockState(otherPos);
        }
    }
}
