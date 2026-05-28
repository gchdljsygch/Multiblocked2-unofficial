package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(DrawerHelper.class)
public abstract class DrawerHelperMixin {
    /**
     * @author pingsu
     * @reason Render compact K/M/G suffixes for oversized item counts in LDLib item slots.
     */
    @Overwrite(remap = false)
    public static void drawItemStack(@Nonnull GuiGraphics graphics, ItemStack itemStack, int x, int y, int color, @Nullable String altTxt) {
        var a = com.lowdragmc.lowdraglib.utils.ColorUtils.alpha(color);
        var r = com.lowdragmc.lowdraglib.utils.ColorUtils.red(color);
        var g = com.lowdragmc.lowdraglib.utils.ColorUtils.green(color);
        var b = com.lowdragmc.lowdraglib.utils.ColorUtils.blue(color);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a);

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);

        Minecraft mc = Minecraft.getInstance();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 232);
        graphics.renderItem(itemStack, x, y);

        String countText = altTxt;
        if (countText == null && !itemStack.isEmpty() && itemStack.getCount() > 1) {
            countText = itemStack.getCount() >= 1000
                    ? TextFormattingUtil.formatLongToCompactString(itemStack.getCount(), 3)
                    : Integer.toString(itemStack.getCount());
        }

        graphics.renderItemDecorations(mc.font, itemStack, x, y, countText);
        graphics.pose().popPose();

        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }
}
