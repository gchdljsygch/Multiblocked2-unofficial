package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MBDGadgetsItem.class, remap = false)
public class MBDGadgetsItemModeSwitchMixin {
    @Shadow
    private boolean isUsed;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true)
    private void mbd2thread$disableCrouchModeCycle(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (isUsed) return;
        if (!player.isCrouching()) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;
        cir.setReturnValue(InteractionResultHolder.pass(stack));
    }
}

