package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.non_coffee.mbd2thread.client.MultiblockDebuggerClient;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MBDGadgetsItem.class, remap = false)
public class MBDGadgetsItemMultiblockDebuggerMixin {
    @Inject(method = "onItemUseFirst", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2thread$multiblockDebuggerShowAll(ItemStack stack, UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        var player = context.getPlayer();
        if (player == null) return;
        if (!((MBDGadgetsItem) (Object) this).isMultiblockDebugger(stack)) return;
        if (!player.isCrouching()) return;

        Level level = context.getLevel();
        var controllerPos = context.getClickedPos();
        IMultiController controller = IMultiController.ofController(level, controllerPos).orElse(null);
        int durationTicks = ConfigHolder.multiblockPreviewDuration * 20;
        if (level.isClientSide && controller instanceof MBDMultiblockMachine multiblock) {
            MultiblockDebuggerClient.showPreviewWithOccupiedMismatch(multiblock, controllerPos, durationTicks);
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
