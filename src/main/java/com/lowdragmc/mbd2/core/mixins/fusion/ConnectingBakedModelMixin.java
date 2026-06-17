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

/**
 * Extends Fusion's connecting-model predicate for MBD machine renderers.
 *
 * <p>MBD machines can render with generated model paths that represent the same visual block
 * even when their block states differ. This mixin allows those models to connect by comparing
 * model paths, while still honoring faces suppressed by {@link FusionModelDataHelper}.</p>
 */
@Mixin(value = ConnectingBakedModel.class, remap = false)
public class ConnectingBakedModelMixin {

    /**
     * Short-circuits Fusion's connection decision for MBD model-path matches.
     *
     * @param predicate           Fusion connection predicate being evaluated
     * @param blockCache          surrounding block cache for the center model
     * @param face                face currently being rendered
     * @param connectionDirection direction tested by Fusion
     * @param x                   neighbor offset X, in the range Fusion passes for its surrounding cache
     * @param y                   neighbor offset Y, in the range Fusion passes for its surrounding cache
     * @param z                   neighbor offset Z, in the range Fusion passes for its surrounding cache
     * @param mutablePos          reusable mutable position for the tested neighbor
     * @param cir                 callback receiving the forced connection result when MBD handles the case
     */
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
