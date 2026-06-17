package com.lowdragmc.mbd2.common.gui.recipe;

import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * Small overlay label that renders a compact numeric value in the lower-right corner of a
 * recipe slot.
 *
 * <p>The value is formatted through {@link TextFormattingUtil#formatLongToCompactString}
 * with three significant characters and an optional unit suffix. This widget only draws on
 * the client and does not perform network synchronization by itself.</p>
 */
@Accessors(chain = true)
public class CornerNumberWidget extends Widget {
    @Getter
    @Setter
    public long value;
    @Getter
    @Setter
    public String unit = "";

    /**
     * Creates a compact corner-number widget.
     *
     * @param x      left position relative to the parent widget
     * @param y      top position relative to the parent widget
     * @param width  widget width in pixels
     * @param height widget height in pixels
     */
    public CornerNumberWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var pos = getPosition();
        var size = getSize();
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1);
        String s = TextFormattingUtil.formatLongToCompactString(value, 3) + unit;
        Font fontRenderer = Minecraft.getInstance().font;
        graphics.drawString(fontRenderer, s, (int) ((pos.x + (size.width / 3f)) * 2 - fontRenderer.width(s) + 21), (int) ((pos.y + (size.height / 3f) + 6) * 2), 0xFFFFFF, true);
        graphics.pose().popPose();
    }
}
