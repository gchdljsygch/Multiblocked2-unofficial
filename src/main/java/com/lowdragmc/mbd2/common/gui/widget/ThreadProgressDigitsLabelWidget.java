package com.lowdragmc.mbd2.common.gui.widget;

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

/**
 * Configurable synced label for recipe-thread progress digits.
 *
 * <p>The value is synchronized from server supplier to client only when it changes, or read
 * locally for client-side templates. If the text does not already end with {@code %}, the
 * widget appends the percent sign while drawing. It can optionally anchor itself beside a
 * {@link ThreadStatusLabelWidget} identified by id, which keeps the progress text aligned
 * after localized status text changes width.</p>
 */
@Configurable(name = "ldlib.gui.editor.register.widget.mbd2.thread_progress_digits_label", collapse = false)
@LDLRegister(name = "thread_progress_digits_label", group = "widget.mbd2")
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

    @Configurable(name = "mbd2.gui.editor.anchor_to_status")
    private boolean anchorToStatus = true;

    @Configurable(name = "mbd2.gui.editor.anchor_status_widget_id")
    private String anchorStatusWidgetId = "";

    /**
     * Creates a default progress label for editor templates.
     */
    public ThreadProgressDigitsLabelWidget() {
        this(0, 0, 10, 10);
    }

    /**
     * Creates a progress label.
     *
     * @param x      left position relative to the parent widget
     * @param y      top position relative to the parent widget
     * @param width  widget width in pixels
     * @param height widget height in pixels
     */
    public ThreadProgressDigitsLabelWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
    }

    /**
     * Sets the progress digits supplier.
     *
     * @param progressDigitsSupplier supplier returning digits with or without a trailing
     *                               percent sign; {@code null} is treated as empty
     */
    public void setProgressDigitsSupplier(Supplier<String> progressDigitsSupplier) {
        this.progressDigitsSupplier = progressDigitsSupplier == null ? () -> "" : progressDigitsSupplier;
    }

    /**
     * Sets the text color.
     *
     * @param color ARGB/RGB color accepted by Minecraft font rendering
     * @return this widget for chaining
     */
    public ThreadProgressDigitsLabelWidget setTextColor(int color) {
        this.color = color;
        return this;
    }

    /**
     * Controls text shadow rendering.
     *
     * @param dropShadow whether the font should draw a shadow
     * @return this widget for chaining
     */
    public ThreadProgressDigitsLabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    /**
     * Sets the id of the status widget used for horizontal anchoring.
     *
     * @param statusWidgetId sibling widget id; {@code null} disables id lookup
     */
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
