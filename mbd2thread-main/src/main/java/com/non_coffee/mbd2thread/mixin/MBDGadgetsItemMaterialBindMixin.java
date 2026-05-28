package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.non_coffee.mbd2thread.util.BuilderMaterialBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MBDGadgetsItem.class, remap = false)
public class MBDGadgetsItemMaterialBindMixin {

    @Inject(method = "onItemUseFirst", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2thread$bindMaterialsOnSneakUse(ItemStack stack, UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        if (!player.isCrouching()) return;
        if (!BuilderMaterialBindings.isBuilder(stack)) return;

        Level level = player.level();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            player.displayClientMessage(Component.translatable("mbd2thread.builder.bind.failure.no_capability"), true);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        boolean boundAny = false;
        if (BuilderMaterialBindings.hasItemHandler(be)) {
            BuilderMaterialBindings.bindItemPos(stack, level, pos);
            player.displayClientMessage(Component.translatable("mbd2thread.builder.bind.item.success", pos.getX(), pos.getY(), pos.getZ()), true);
            boundAny = true;
        }
        if (BuilderMaterialBindings.hasFluidHandler(be)) {
            BuilderMaterialBindings.bindFluidPos(stack, level, pos);
            player.displayClientMessage(Component.translatable("mbd2thread.builder.bind.fluid.success", pos.getX(), pos.getY(), pos.getZ()), true);
            boundAny = true;
        }

        if (!boundAny) {
            player.displayClientMessage(Component.translatable("mbd2thread.builder.bind.failure.no_capability"), true);
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"), remap = false)
    private void mbd2thread$appendBindTips(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag isAdvanced, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!BuilderMaterialBindings.isBuilder(stack)) return;

        var item = BuilderMaterialBindings.readBoundItemPos(stack);
        if (item != null) {
            var p = item.pos();
            components.add(Component.translatable("mbd2thread.builder.bind.item.tooltip", p.getX(), p.getY(), p.getZ()));
        }

        var fluid = BuilderMaterialBindings.readBoundFluidPos(stack);
        if (fluid != null) {
            var p = fluid.pos();
            components.add(Component.translatable("mbd2thread.builder.bind.fluid.tooltip", p.getX(), p.getY(), p.getZ()));
        }
    }
}
