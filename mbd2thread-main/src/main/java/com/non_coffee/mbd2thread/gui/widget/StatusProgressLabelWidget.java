package com.non_coffee.mbd2thread.gui.widget;

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

public class StatusProgressLabelWidget extends com.lowdragmc.lowdraglib.gui.widget.Widget {

    private Supplier<String> statusSupplier = () -> "";

    private Supplier<String> progressDigitsSupplier = () -> "";

    @Nonnull
    private String lastStatusValue = "";
    @Nonnull
    private String lastProgressDigitsValue = "";

    private int color = -1;
    private boolean dropShadow = true;

    public StatusProgressLabelWidget(int x, int y, int width, int height) {
        super(new Position(x, y), new Size(width, height));
    }

    public void setStatusSupplier(Supplier<String> statusSupplier) {
        this.statusSupplier = statusSupplier == null ? () -> "" : statusSupplier;
    }

    public void setProgressDigitsSupplier(Supplier<String> progressDigitsSupplier) {
        this.progressDigitsSupplier = progressDigitsSupplier == null ? () -> "" : progressDigitsSupplier;
    }

    public StatusProgressLabelWidget setTextColor(int color) {
        this.color = color;
        return this;
    }

    public StatusProgressLabelWidget setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (!isClientSideWidget) {
            this.lastStatusValue = safeString(statusSupplier.get());
            this.lastProgressDigitsValue = safeString(progressDigitsSupplier.get());
            buffer.writeUtf(this.lastStatusValue);
            buffer.writeUtf(this.lastProgressDigitsValue);
        } else {
            buffer.writeUtf(safeString(lastStatusValue));
            buffer.writeUtf(safeString(lastProgressDigitsValue));
        }
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.lastStatusValue = safeString(buffer.readUtf());
        this.lastProgressDigitsValue = safeString(buffer.readUtf());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!isClientSideWidget) {
            String latestStatus = safeString(statusSupplier.get());
            if (!latestStatus.equals(lastStatusValue)) {
                this.lastStatusValue = latestStatus;
                writeUpdateInfo(0, b -> b.writeUtf(this.lastStatusValue));
            }
            String latestProgress = safeString(progressDigitsSupplier.get());
            if (!latestProgress.equals(lastProgressDigitsValue)) {
                this.lastProgressDigitsValue = latestProgress;
                writeUpdateInfo(1, b -> b.writeUtf(this.lastProgressDigitsValue));
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            this.lastStatusValue = safeString(buffer.readUtf());
        } else if (id == 1) {
            this.lastProgressDigitsValue = safeString(buffer.readUtf());
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget) {
            String latestStatus = safeString(statusSupplier.get());
            String latestProgress = safeString(progressDigitsSupplier.get());
            if (!latestStatus.equals(lastStatusValue) || !latestProgress.equals(lastProgressDigitsValue)) {
                this.lastStatusValue = latestStatus;
                this.lastProgressDigitsValue = latestProgress;
            }
        }
    }

    @Override
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        Position pos = getPosition();

        String statusText = safeFormatOrRaw(safeString(lastStatusValue));
        graphics.drawString(font, statusText, pos.x, pos.y, color, dropShadow);

        String progressDigits = safeString(lastProgressDigitsValue);
        if (!progressDigits.isEmpty()) {
            int x = pos.x + font.width(statusText) + font.width("   ");
            if (!progressDigits.endsWith("%")) {
                progressDigits = progressDigits + "%";
            }
            graphics.drawString(font, progressDigits, x, pos.y, color, dropShadow);
        }
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

