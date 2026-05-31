package com.lowdragmc.mbd2.core.mixins.fusion;

import com.lowdragmc.mbd2.client.renderer.FusionModelDataHelper;
import com.supermartijn642.fusion.api.predicate.ConnectionDirection;
import com.supermartijn642.fusion.api.predicate.ConnectionPredicate;
import com.supermartijn642.fusion.model.types.connecting.ConnectingBakedModel;
import com.supermartijn642.fusion.model.types.connecting.SurroundingBlockCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ConnectingBakedModel.class, remap = false)
public class ConnectingBakedModelMixin {

    @Inject(method = "shouldConnect", at = @At("HEAD"), cancellable = true)
    private static void mbd2$connectMbdMachinesByModelPath(ConnectionPredicate predicate,
                                                            SurroundingBlockCache blockCache,
                                                           Direction face,
                                                           ConnectionDirection connectionDirection,
                                                           int x,
                                                           int y,
                                                           int z,
                                                           BlockPos.MutableBlockPos mutablePos,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (FusionModelDataHelper.isSuppressedFace(face)) {
            cir.setReturnValue(false);
            return;
        }
        var level = blockCache.getLevel();
        var centerPos = blockCache.getRealPos();
        mutablePos.set(centerPos.getX() + x, centerPos.getY() + y, centerPos.getZ() + z);
        if (FusionModelDataHelper.shouldConnectByModelPath(level, centerPos, mutablePos)) {
            FusionModelDataHelper.debugOnce("fusion-should-connect-hit-" + centerPos + "-" + mutablePos.immutable() + "-" + face,
                    "ConnectingBakedModel shouldConnect by model path, center={}, other={}, face={}, direction={}",
                    centerPos, mutablePos, face, connectionDirection);
            cir.setReturnValue(true);
        }
    }
}
