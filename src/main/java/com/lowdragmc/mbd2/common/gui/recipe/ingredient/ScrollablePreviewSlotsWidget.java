package com.lowdragmc.mbd2.common.gui.recipe.ingredient;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.common.capability.recipe.FluidRecipeCapability;
import com.lowdragmc.mbd2.common.capability.recipe.ItemRecipeCapability;
import com.lowdragmc.mbd2.utils.WidgetUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Configurable(name = "ldlib.gui.editor.register.widget.container.scrollable_preview_slots", collapse = false)
@LDLRegister(name = "scrollable_preview_slots", group = "widget.container")
public class ScrollablePreviewSlotsWidget extends DraggableScrollableWidgetGroup implements IConfigurableWidget {

    @Configurable(name = "ldlib.gui.editor.name.slotSize")
    protected int slotSize = Integer.MAX_VALUE;
    @Configurable(name = "ldlib.gui.editor.name.slotGap")
    protected int slotGap = 0;
    @Configurable(name = "ldlib.gui.editor.name.columns")
    protected int columns = Integer.MAX_VALUE;
    protected Supplier<List<PreviewSlot>> previewSlotSupplier;
    protected IngredientIO previewSlotIngredientIO = IngredientIO.RENDER_ONLY;
    protected List<PreviewSlot> lastPreviewSlots = Collections.emptyList();

    public ScrollablePreviewSlotsWidget() {
        this(0, 0, 72, 36);
    }

    public ScrollablePreviewSlotsWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        setXScrollBarHeight(0);
        setYScrollBarWidth(4);
    }

    public ScrollablePreviewSlotsWidget setSlotSize(int slotSize) {
        this.slotSize = Math.max(1, slotSize);
        return this;
    }

    public ScrollablePreviewSlotsWidget setSlotGap(int slotGap) {
        this.slotGap = Math.max(0, slotGap);
        return this;
    }

    public ScrollablePreviewSlotsWidget setColumns(int columns) {
        this.columns = Math.max(1, columns);
        return this;
    }

    public void bindXEIContents(Map<RecipeCapability<?>, List<Content>> values, IO io) {
        int scrollYOffset = getScrollYOffset();
        clearAllWidgets();
        List<PreviewSlot> previewSlots = collectPreviewSlots(values, io);
        for (int i = 0; i < previewSlots.size(); i++) {
            PreviewSlot slot = previewSlots.get(i);
            Widget widget = slot.capability.createXEITemplate();
            widget.setSelfPosition(getSlotPosition(i));
            slot.capability.bindXEIWidget(widget, slot.content, ingredientIO(io));
            widget.setId(getSlotId(slot.capability, io, i));
            addWidget(widget);
        }
        computeMax();
        setScrollYOffset(scrollYOffset);
    }

    public void bindXEIContents(Map<RecipeCapability<?>, List<Content>> values, IO io, String capabilityName) {
        bindXEIContents(values, io, Pattern.compile(Pattern.quote(capabilityName)));
    }

    public void bindXEIContents(Map<RecipeCapability<?>, List<Content>> values, IO io, Pattern capabilityName) {
        int scrollYOffset = getScrollYOffset();
        clearAllWidgets();
        List<PreviewSlot> previewSlots = collectPreviewSlots(values, io);
        for (int i = 0, visibleIndex = 0; i < previewSlots.size(); i++) {
            PreviewSlot slot = previewSlots.get(i);
            if (!capabilityName.matcher(slot.capability.name).matches()) {
                continue;
            }
            Widget widget = slot.capability.createXEITemplate();
            widget.setSelfPosition(getSlotPosition(visibleIndex));
            slot.capability.bindXEIWidget(widget, slot.content, ingredientIO(io));
            widget.setId(getSlotId(slot.capability, io, visibleIndex));
            addWidget(widget);
            visibleIndex++;
        }
        computeMax();
        setScrollYOffset(scrollYOffset);
    }

    public void bindXEIContents(List<PreviewSlot> previewSlots, IngredientIO ingredientIO) {
        int scrollYOffset = getScrollYOffset();
        lastPreviewSlots = List.copyOf(previewSlots);
        clearAllWidgets();
        for (int i = 0; i < previewSlots.size(); i++) {
            PreviewSlot slot = previewSlots.get(i);
            Widget widget = slot.capability.createXEITemplate();
            widget.setSelfPosition(getSlotPosition(i));
            slot.capability.bindXEIWidget(widget, slot.content, ingredientIO);
            widget.setId(getSlotId(slot.capability, ingredientIO, i));
            addWidget(widget);
        }
        computeMax();
        setScrollYOffset(scrollYOffset);
    }

    public ScrollablePreviewSlotsWidget setPreviewSlotSupplier(Supplier<List<PreviewSlot>> previewSlotSupplier, IngredientIO ingredientIO) {
        this.previewSlotSupplier = previewSlotSupplier;
        this.previewSlotIngredientIO = ingredientIO == null ? IngredientIO.RENDER_ONLY : ingredientIO;
        this.lastPreviewSlots = Collections.emptyList();
        return this;
    }

    @Override
    public void updateScreen() {
        if (previewSlotSupplier != null) {
            List<PreviewSlot> previewSlots = Objects.requireNonNullElseGet(previewSlotSupplier.get(), Collections::emptyList);
            if (!previewSlots.equals(lastPreviewSlots)) {
                bindXEIContents(previewSlots, previewSlotIngredientIO);
            }
        }
        super.updateScreen();
    }

    @Override
    public void setScrollYOffset(int scrollYOffset) {
        int pitch = getSlotPitch();
        super.setScrollYOffset(Math.max(0, Math.round(scrollYOffset / (float) pitch) * pitch));
    }

    @Override
    public List<Widget> getContainedWidgets(boolean includeHidden) {
        if (includeHidden) {
            return super.getContainedWidgets(true);
        }
        return widgets.stream()
                .filter(Widget::isVisible)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Position getSlotPosition(int index) {
        int pitch = getSlotPitch();
        return new Position((index % columns) * pitch, (index / columns) * pitch);
    }

    public int getSlotPitch() {
        return slotSize + slotGap;
    }

    public static void bindXEIRecipeUI(com.lowdragmc.lowdraglib.gui.widget.WidgetGroup ui, Map<RecipeCapability<?>, List<Content>> values, IO io) {
        WidgetUtils.widgetByIdForEach(ui, "^@scrollable_preview_%s$".formatted(io.name), ScrollablePreviewSlotsWidget.class,
                previewSlots -> previewSlots.bindXEIContents(values, io));
        WidgetUtils.widgetByIdForEach(ui, "^@scrollable_preview_%s_[a-zA-Z0-9_.:-]+$".formatted(io.name), ScrollablePreviewSlotsWidget.class,
                previewSlots -> {
                    String id = previewSlots.getId();
                    String prefix = "@scrollable_preview_%s_".formatted(io.name);
                    previewSlots.bindXEIContents(values, io, Pattern.quote(id.substring(prefix.length())));
                });
    }

    public static List<PreviewSlot> collectPreviewSlots(Map<RecipeCapability<?>, List<Content>> values, IO io) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<PreviewSlot> previewSlots = new ArrayList<>();
        values.forEach((capability, contents) -> {
            if (capability instanceof ItemRecipeCapability || capability instanceof FluidRecipeCapability) {
                for (Content content : contents) {
                    previewSlots.add(new PreviewSlot(capability, content, io));
                }
            }
        });
        return previewSlots;
    }

    private static String getSlotId(RecipeCapability<?> capability, IO io, int index) {
        return "@scrollable_preview_slot_%s_%s_%d".formatted(capability.name, io.name, index);
    }

    private static String getSlotId(RecipeCapability<?> capability, IngredientIO ingredientIO, int index) {
        return "@scrollable_preview_slot_%s_%s_%d".formatted(capability.name, ingredientIO.name().toLowerCase(), index);
    }

    private static IngredientIO ingredientIO(IO io) {
        return switch (io) {
            case IN -> IngredientIO.INPUT;
            case OUT -> IngredientIO.OUTPUT;
            case BOTH -> IngredientIO.BOTH;
            default -> IngredientIO.RENDER_ONLY;
        };
    }

    public record PreviewSlot(RecipeCapability<?> capability, Content content, IO io) {
    }
}
