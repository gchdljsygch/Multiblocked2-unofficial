package com.lowdragmc.mbd2.common.gui.widget;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Invisible configurable widget that shows a server-synced hover tooltip.
 *
 * <p>The tooltip supplier is sampled on the server and synchronized only when the string
 * changes. In client-side templates the last configured value is used directly. The widget
 * deliberately returns no hover element so it can overlay existing UI without taking normal
 * hover ownership, then manually sets the ModularUI tooltip when the mouse is inside its
 * bounds.</p>
 */
@Configurable(name = "ldlib.gui.editor.register.widget.mbd2.synced_hover_tooltip", collapse = false)
@LDLRegister(name = "synced_hover_tooltip", group = "widget.mbd2")
public class SyncedHoverTooltipWidget extends com.lowdragmc.lowdraglib.gui.widget.Widget implements IConfigurableWidget {

    private Supplier<String> tooltipSupplier = () -> "";

    @javax.annotation.Nonnull
    private String lastTooltipValue = "";

    /**
     * Creates a default 10x10 tooltip area for editor templates.
     */
    public SyncedHoverTooltipWidget() {
        this(0, 0, 10, 10);
    }

    /**
     * Creates a tooltip area.
     *
     * @param x      left position relative to the parent widget
     * @param y      top position relative to the parent widget
     * @param width  widget width in pixels
     * @param height widget height in pixels
     */
    public SyncedHoverTooltipWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
        setDrawBackgroundWhenHover(false);
    }

    /**
     * Sets the tooltip text supplier.
     *
     * @param tooltipSupplier supplier returning raw tooltip text; {@code null} is treated
     *                        as an empty supplier
     */
    public void setTooltipSupplier(Supplier<String> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier == null ? () -> "" : tooltipSupplier;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (!isClientSideWidget) {
            this.lastTooltipValue = safeString(tooltipSupplier.get());
            buffer.writeUtf(this.lastTooltipValue);
        } else {
            buffer.writeUtf(safeString(lastTooltipValue));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.lastTooltipValue = safeString(buffer.readUtf());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!isClientSideWidget) {
            String latest = safeString(tooltipSupplier.get());
            if (!latest.equals(lastTooltipValue)) {
                this.lastTooltipValue = latest;
                writeUpdateInfo(0, b -> b.writeUtf(this.lastTooltipValue));
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            this.lastTooltipValue = safeString(buffer.readUtf());
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tooltipTexts.clear();
        String tooltip = safeString(lastTooltipValue);
        if (!tooltip.isEmpty()) {
            tooltipTexts.add(Component.literal(tooltip));
        }
    }

    @Override
    public @Nullable com.lowdragmc.lowdraglib.gui.widget.Widget getHoverElement(double mouseX, double mouseY) {
        return null;
    }

    @Override
    public void drawInForeground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (tooltipTexts.size() > 0 && isMouseOverElement(mouseX, mouseY) && gui != null && gui.getModularUIGui() != null) {
            gui.getModularUIGui().setHoverTooltip(tooltipTexts, net.minecraft.world.item.ItemStack.EMPTY, null, null);
        }
    }

    @javax.annotation.Nonnull
    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}

