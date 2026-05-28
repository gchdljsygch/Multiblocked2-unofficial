package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.non_coffee.mbd2thread.util.BuilderMaterialBindings;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MBDGadgetsItem.class, remap = false)
public class MBDGadgetsItemDescriptionIdMixin {
    @Inject(method = "getDescriptionId", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2thread$builderModeDescriptionId(ItemStack stack, CallbackInfoReturnable<String> cir) {
        if (!BuilderMaterialBindings.isBuilder(stack)) return;
        boolean slow = BuilderMaterialBindings.isSlowBuild(stack);
        cir.setReturnValue(slow
                ? "item.mbd2thread.mbd_gadgets.multiblock_builder.slow"
                : "item.mbd2thread.mbd_gadgets.multiblock_builder.instant");
    }
}

