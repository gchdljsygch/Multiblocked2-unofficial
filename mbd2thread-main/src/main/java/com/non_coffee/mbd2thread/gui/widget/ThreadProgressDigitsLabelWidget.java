package com.non_coffee.mbd2thread.gui.widget;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;

@Configurable(name = "ldlib.gui.editor.register.widget.mbd2thread.thread_progress_digits_label", collapse = false)
@LDLRegister(name = "thread_progress_digits_label", group = "widget.mbd2thread")
public class ThreadProgressDigitsLabelWidget extends com.lowdragmc.lowdraglib.gui.widget.Widget implements IConfigurableWidget {

    private Supplier<String> progressDigitsSupplier = () -> "";

    @Configurable(name = "ldlib.gui.editor.name.text")
    @Nonnull
    private String lastProgressDigitsValue = "";

    @Configurable(name = "ldlib.gui.editor.name.color")
    @NumberColor
    private int color = -1;
    @Configurable(name = "ldlib.gui.editor.name.isShadow")
    private boolean dropShadow = true;

    @Configurable(name = "mbd2thread.gui.editor.anchor_to_status")
    private boolean anchorToStatus = true;

    @Configurable(name = "mbd2thread.gui.editor.anchor_status_widget_id")
    private String anchorStatusWidgetId = "";

    public ThreadProgressDigitsLabelWidget() {
        this(0, 0, 10, 10);
    }

    public ThreadProgressDigitsLabelWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
    }

    public void setProgressDigitsSupplier(Supplier<String> progressDigitsSupplier) {
        this.progressDigitsSupplier = progressDigitsSupplier == null ? () -> "" : progressDigitsSupplier;
    }

    public ThreadProgressDigitsLabelWidget setTextColor(int color) {
        this.color = color;
        return this;
    }

    public ThreadProgressDigitsLabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    public void setAnchorStatusWidgetId(String statusWidgetId) {
        this.anchorStatusWidgetId = statusWidgetId == null ? "" : statusWidgetId;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (!isClientSideWidget) {
            this.lastProgressDigitsValue = safeString(progressDigitsSupplier.get());
            buffer.writeUtf(this.lastProgressDigitsValue);
        } else {
            buffer.writeUtf(safeString(lastProgressDigitsValue));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.lastProgressDigitsValue = safeString(buffer.readUtf());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!isClientSideWidget) {
            String latest = safeString(progressDigitsSupplier.get());
            if (!latest.equals(lastProgressDigitsValue)) {
                this.lastProgressDigitsValue = latest;
                writeUpdateInfo(0, b -> b.writeUtf(this.lastProgressDigitsValue));
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            this.lastProgressDigitsValue = safeString(buffer.readUtf());
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget) {
            String latest = safeString(progressDigitsSupplier.get());
            if (!latest.equals(lastProgressDigitsValue)) {
                this.lastProgressDigitsValue = latest;
            }
        }
    }

    @Override
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        String progressDigits = safeString(lastProgressDigitsValue);
        if (progressDigits.isEmpty()) return;
        if (!progressDigits.endsWith("%")) {
            progressDigits = progressDigits + "%";
        }

        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        Position pos = getPosition();
        int x = pos.x;
        int y = pos.y;

        if (anchorToStatus && getParent() != null && anchorStatusWidgetId != null && !anchorStatusWidgetId.isEmpty()) {
            var anchor = WidgetUtils.getFirstWidgetById(getParent(), "^" + java.util.regex.Pattern.quote(anchorStatusWidgetId) + "$");
            if (anchor instanceof ThreadStatusLabelWidget statusWidget) {
                String statusText = statusWidget.getRenderedStatusText();
                Position statusPos = statusWidget.getPosition();
                String safeStatusText = statusText == null ? "" : statusText;
                x = statusPos.x + font.width(safeStatusText) + font.width("   ");
                y = statusPos.y;
            }
        }

        graphics.drawString(font, progressDigits, x, y, color, dropShadow);
    }

    @Nonnull
    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
