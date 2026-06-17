package com.lowdragmc.mbd2.common.gui.widget;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Configurable synced label for recipe-thread status text.
 *
 * <p>The supplier value is synchronized from server to client when it changes. In
 * client-side templates the supplier is sampled locally. The rendered value is localized
 * when possible and falls back to the raw string, which allows callers to pass either
 * translation keys or already formatted status text.</p>
 */
@Configurable(name = "ldlib.gui.editor.register.widget.mbd2.thread_status_label", collapse = false)
@LDLRegister(name = "thread_status_label", group = "widget.mbd2")
public class ThreadStatusLabelWidget extends com.lowdragmc.lowdraglib.gui.widget.Widget implements IConfigurableWidget {
    private Supplier<String> statusSupplier = () -> "";

    @Configurable(name = "ldlib.gui.editor.name.text")
    @Nonnull
    private String lastStatusValue = "";

    @Configurable(name = "ldlib.gui.editor.name.color")
    @NumberColor
    private int color = -1;
    @Configurable(name = "ldlib.gui.editor.name.isShadow")
    private boolean dropShadow = true;

    /**
     * Creates a default status label for editor templates.
     */
    public ThreadStatusLabelWidget() {
        this(0, 0, 10, 10);
    }

    /**
     * Creates a status label.
     *
     * @param x      left position relative to the parent widget
     * @param y      top position relative to the parent widget
     * @param width  widget width in pixels
     * @param height widget height in pixels
     */
    public ThreadStatusLabelWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
    }

    /**
     * Sets the status text supplier.
     *
     * @param statusSupplier supplier returning a translation key or raw text; {@code null}
     *                       is treated as an empty supplier
     */
    public void setStatusSupplier(Supplier<String> statusSupplier) {
        this.statusSupplier = statusSupplier == null ? () -> "" : statusSupplier;
    }

    /**
     * Sets the text color.
     *
     * @param color ARGB/RGB color accepted by Minecraft font rendering
     * @return this widget for chaining
     */
    public ThreadStatusLabelWidget setTextColor(int color) {
        this.color = color;
        return this;
    }

    /**
     * Controls text shadow rendering.
     *
     * @param dropShadow whether the font should draw a shadow
     * @return this widget for chaining
     */
    public ThreadStatusLabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    /**
     * Returns the localized status text currently rendered by this widget.
     *
     * @return localized status text, raw text on localization failure, or an empty string
     */
    public String getRenderedStatusText() {
        return safeFormatOrRaw(safeString(lastStatusValue));
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (!isClientSideWidget) {
            this.lastStatusValue = safeString(statusSupplier.get());
            buffer.writeUtf(this.lastStatusValue);
        } else {
            buffer.writeUtf(safeString(lastStatusValue));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.lastStatusValue = safeString(buffer.readUtf());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!isClientSideWidget) {
            String latest = safeString(statusSupplier.get());
            if (!latest.equals(lastStatusValue)) {
                this.lastStatusValue = latest;
                writeUpdateInfo(0, b -> b.writeUtf(this.lastStatusValue));
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            this.lastStatusValue = safeString(buffer.readUtf());
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget) {
            String latest = safeString(statusSupplier.get());
            if (!latest.equals(lastStatusValue)) {
                this.lastStatusValue = latest;
            }
        }
    }

    @Override
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        String statusText = getRenderedStatusText();
        if (statusText.isEmpty()) return;
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        Position pos = getPosition();
        graphics.drawString(font, statusText, pos.x, pos.y, color, dropShadow);
    }

    @Nonnull
    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    @Nonnull
    private static String safeFormatOrRaw(String keyOrText) {
        if (keyOrText == null || keyOrText.isEmpty()) return "";
        try {
            String formatted = LocalizationUtils.format(keyOrText);
            return formatted == null ? "" : formatted;
        } catch (Exception e) {
            return keyOrText;
        }
    }
}
