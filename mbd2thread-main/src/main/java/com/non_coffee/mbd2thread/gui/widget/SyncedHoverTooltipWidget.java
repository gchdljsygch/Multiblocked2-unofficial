package com.non_coffee.mbd2thread.gui.widget;

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

@Configurable(name = "ldlib.gui.editor.register.widget.mbd2thread.synced_hover_tooltip", collapse = false)
@LDLRegister(name = "synced_hover_tooltip", group = "widget.mbd2thread")
public class SyncedHoverTooltipWidget extends com.lowdragmc.lowdraglib.gui.widget.Widget implements IConfigurableWidget {

    private Supplier<String> tooltipSupplier = () -> "";

    @javax.annotation.Nonnull
    private String lastTooltipValue = "";

    public SyncedHoverTooltipWidget() {
        this(0, 0, 10, 10);
    }

    public SyncedHoverTooltipWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
        setDrawBackgroundWhenHover(false);
    }

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

