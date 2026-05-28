package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.BlockPlaceholder;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.non_coffee.mbd2thread.duck.BlockPatternBaseFacingAccess;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiblockMachineProject.class, remap = false)
public class MultiblockMachineProjectCreateBlockPatternMixin {
    @Inject(
            method = "createBlockPattern([[[Lcom/lowdragmc/mbd2/common/gui/editor/multiblock/BlockPlaceholder;Lnet/minecraft/core/Direction$Axis;[[ILcom/lowdragmc/mbd2/common/machine/definition/MultiblockMachineDefinition;)Lcom/lowdragmc/mbd2/api/pattern/BlockPattern;",
            at = @At("RETURN"),
            remap = false
    )
    private static void mbd2thread$attachBaseFacingA(BlockPlaceholder[][][] blockPlaceholders,
                                                     Direction.Axis layerAxis,
                                                     int[][] aisleRepetitions,
                                                     MultiblockMachineDefinition definition,
                                                     CallbackInfoReturnable<BlockPattern> cir) {
        attach(blockPlaceholders, cir.getReturnValue());
    }

    @Inject(
            method = "createBlockPattern([[[Lcom/lowdragmc/mbd2/common/gui/editor/multiblock/BlockPlaceholder;Lnet/minecraft/core/Direction$Axis;[[ILcom/lowdragmc/mbd2/common/machine/definition/MultiblockMachineDefinition;Z)Lcom/lowdragmc/mbd2/api/pattern/BlockPattern;",
            at = @At("RETURN"),
            remap = false
    )
    private static void mbd2thread$attachBaseFacingB(BlockPlaceholder[][][] blockPlaceholders,
                                                     Direction.Axis layerAxis,
                                                     int[][] aisleRepetitions,
                                                     MultiblockMachineDefinition definition,
                                                     boolean shapeInfo,
                                                     CallbackInfoReturnable<BlockPattern> cir) {
        attach(blockPlaceholders, cir.getReturnValue());
    }

    private static void attach(BlockPlaceholder[][][] placeholders, BlockPattern pattern) {
        if (pattern == null) return;
        if (!(pattern instanceof BlockPatternBaseFacingAccess access)) return;
        Direction base = findControllerFacing(placeholders);
        access.mbd2thread$setBaseFacing(base);
    }

    private static Direction findControllerFacing(BlockPlaceholder[][][] placeholders) {
        if (placeholders == null) return Direction.NORTH;
        for (var xSlice : placeholders) {
            if (xSlice == null) continue;
            for (var ySlice : xSlice) {
                if (ySlice == null) continue;
                for (var holder : ySlice) {
                    if (holder == null) continue;
                    if (!holder.isController()) continue;
                    Direction facing = holder.getFacing();
                    if (facing == null || facing.getAxis() == Direction.Axis.Y) return Direction.NORTH;
                    return facing;
                }
            }
        }
        return Direction.NORTH;
    }
}
