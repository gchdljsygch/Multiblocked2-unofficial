package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.non_coffee.mbd2thread.client.Mbd2ThreadClientEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = MBDGadgetsItem.class, remap = false)
public class MBDGadgetsItemTooltipMixin {
    @Inject(method = "appendHoverText", at = @At("TAIL"), remap = true)
    private void mbd2thread$replaceModeSwitchTip(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag, CallbackInfo ci) {
        String baseKey = ((Item) (Object) this).getDescriptionId() + ".tooltip";
        for (int i = 0; i < components.size(); i++) {
            Component c = components.get(i);
            if (c.getContents() instanceof TranslatableContents tc && baseKey.equals(tc.getKey())) {
                Component key = Mbd2ThreadClientEvents.OPEN_GADGET_WHEEL.getTranslatedKeyMessage();
                components.set(i, Component.translatable("tooltip.mbd2thread.open_wheel", key).withStyle(ChatFormatting.GREEN));
                break;
            }
        }
    }
}

