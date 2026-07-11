package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.CycleItemStackHandler;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Standard client-side XEI entry backed by data-pack-friendly template data.
 *
 * <p>The template renders a title, optional translated description tooltip, an
 * optional data-pack structure scene, and a scrollable candidate-input area.
 * Java integrations can continue to provide richer scenes through their own
 * {@link WidgetGroup} implementation.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TemplateMultiblockXEI implements IMultiblockXEI {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 160;

    private final TemplateMultiblockXEIData data;

    public TemplateMultiblockXEI(TemplateMultiblockXEIData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    @NotNull
    public net.minecraft.resources.ResourceLocation getId() {
        return data.id();
    }

    @Override
    @NotNull
    public WidgetGroup createWidget() {
        return new TemplateMultiblockPreviewWidget(data);
    }

    @Override
    @NotNull
    public ItemStack getWorkstation() {
        return data.workstation();
    }

    @Override
    @NotNull
    public List<List<ItemStack>> getInputs(WidgetGroup widget) {
        return data.inputs();
    }
}

/**
 * Fixed-size template page used by {@link TemplateMultiblockXEI}.
 */
@OnlyIn(Dist.CLIENT)
final class TemplateMultiblockPreviewWidget extends WidgetGroup {
    private final TemplateIngredientScrollableWidgetGroup inputsGroup;

    TemplateMultiblockPreviewWidget(TemplateMultiblockXEIData data) {
        this(data.title(), data.description(), data.workstation(), data.inputs(), data.structure());
    }

    TemplateMultiblockPreviewWidget(String title, List<String> description, ItemStack workstation,
                                    List<List<ItemStack>> inputs,
                                    List<TemplateMultiblockXEIData.StructureBlock> structure) {
        super(0, 0, TemplateMultiblockXEI.WIDTH, TemplateMultiblockXEI.HEIGHT);
        setClientSideWidget();

        GuiTextureGroup background = new GuiTextureGroup(
                new ColorRectTexture(ColorPattern.T_GRAY.color),
                new ColorBorderTexture(-1, ColorPattern.T_WHITE.color)
        );
        addWidget(new ImageWidget(4, 4, 152, 106, background));
        if (!structure.isEmpty()) {
            TrackedDummyWorld structureLevel = new TrackedDummyWorld();
            List<BlockPos> renderedPositions = new ArrayList<>(structure.size());
            for (TemplateMultiblockXEIData.StructureBlock structureBlock : structure) {
                BlockInfo blockInfo = BlockInfo.fromBlockState(structureBlock.state());
                if (structureBlock.blockEntityTag() != null) {
                    blockInfo.setTag(structureBlock.blockEntityTag());
                }
                structureLevel.addBlock(structureBlock.pos(), blockInfo);
                renderedPositions.add(structureBlock.pos());
            }
            SceneWidget sceneWidget = new SceneWidget(27, 7, 106, 106, structureLevel)
                    .setRenderFacing(false)
                    .setRenderSelect(false);
            sceneWidget.setRenderedCore(renderedPositions, null);
            addWidget(sceneWidget);
        }
        addWidget(new ImageWidget(8, 8, 132, 15,
                new TextTexture(title, ColorPattern.WHITE.color)
                        .setType(TextTexture.TextType.ROLL)
                        .setWidth(132)
                        .setDropShadow(true)));

        if (description != null && !description.isEmpty()) {
            ImageWidget descriptionWidget = new ImageWidget(142, 8, 16, 16,
                    new ResourceTexture("mbd2:textures/gui/information.png"));
            descriptionWidget.setHoverTooltips(description.stream()
                    .filter(line -> line != null && !line.isBlank())
                    .map(line -> (Component) Component.translatable(line))
                    .toList());
            addWidget(descriptionWidget);
        }

        if (workstation != null && !workstation.isEmpty()) {
            addWidget(new SlotWidget(new CycleItemStackHandler(List.of(List.of(workstation))), 0,
                    136, 80, false, false)
                    .setIngredientIO(IngredientIO.CATALYST)
                    .setBackgroundTexture(background));
        }

        addWidget(new ImageWidget(4, 114, 152, 42, ResourceBorderTexture.BORDERED_BACKGROUND_INVERSE));
        addWidget(inputsGroup = new TemplateIngredientScrollableWidgetGroup(6, 117, 148, 36));
        inputsGroup.setYScrollBarWidth(4)
                .setYBarStyle(null, ColorPattern.T_WHITE.rectTexture().setRadius(2).transform(-0.5f, 0));
        inputsGroup.setStacks(inputs);
    }

    /**
     * Returns the data shown by the template input area.
     *
     * @return copied candidate groups
     */
    @NotNull
    public List<List<ItemStack>> getInputs() {
        return inputsGroup.getStacks();
    }
}

/**
 * Scrollable input grid with proxy slots exposed to recipe viewers.
 */
@OnlyIn(Dist.CLIENT)
final class TemplateIngredientScrollableWidgetGroup extends DraggableScrollableWidgetGroup {
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 7;
    private static final int VISIBLE_SLOTS = 14;

    private final List<List<ItemStack>> displayStacks = new ArrayList<>(VISIBLE_SLOTS);
    private final CycleItemStackHandler displayHandler;
    private final List<Widget> xeiSlots = new ArrayList<>(VISIBLE_SLOTS);
    private List<List<ItemStack>> stacks = Collections.emptyList();

    TemplateIngredientScrollableWidgetGroup(int x, int y, int width, int height) {
        super(x, y, width, height);
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            displayStacks.add(Collections.emptyList());
        }
        displayHandler = new CycleItemStackHandler(displayStacks);

        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            int slotX = (i % COLUMNS) * SLOT_SIZE;
            int slotY = (i / COLUMNS) * SLOT_SIZE;
            addWidget(new SlotWidget(displayHandler, i, slotX, slotY, false, false)
                    .setIngredientIO(IngredientIO.INPUT));

            ScrollerProxySlotWidget proxySlot = new ScrollerProxySlotWidget(
                    displayHandler, i, x + slotX, y + slotY);
            proxySlot.attachTo(this);
            proxySlot.setIngredientIO(IngredientIO.INPUT)
                    .setDrawHoverOverlay(false)
                    .setDrawHoverTips(false)
                    .setBackgroundTexture(null);
            xeiSlots.add(proxySlot);
        }
    }

    void setStacks(List<List<ItemStack>> stacks) {
        this.stacks = copyInputs(stacks);
        this.scrollYOffset = Math.min(this.scrollYOffset, getMaxScrollYOffset());
        updateDisplayStacks();
    }

    @NotNull
    List<List<ItemStack>> getStacks() {
        return copyInputs(stacks);
    }

    @Override
    public List<Widget> getContainedWidgets(boolean includeHidden) {
        return xeiSlots;
    }

    @Override
    public void setScrollYOffset(int scrollYOffset) {
        int snapped = Math.max(0, Math.round(scrollYOffset / (float) SLOT_SIZE) * SLOT_SIZE);
        this.scrollYOffset = Math.min(snapped, getMaxScrollYOffset());
        updateDisplayStacks();
    }

    @Override
    public void computeMax() {
        this.scrollYOffset = Math.min(this.scrollYOffset, getMaxScrollYOffset());
        updateDisplayStacks();
    }

    @Override
    public int getWidgetBottomHeight() {
        return getTotalRows() * SLOT_SIZE;
    }

    @Override
    protected int getMaxHeight() {
        return Math.max(getSize().height, getWidgetBottomHeight() + xBarHeight);
    }

    private int getVisibleRows() {
        return VISIBLE_SLOTS / COLUMNS;
    }

    private int getTotalRows() {
        return Math.max(getVisibleRows(), (stacks.size() + COLUMNS - 1) / COLUMNS);
    }

    private int getMaxScrollYOffset() {
        return Math.max(0, (getTotalRows() - getVisibleRows()) * SLOT_SIZE);
    }

    private void updateDisplayStacks() {
        int firstSlot = (getScrollYOffset() / SLOT_SIZE) * COLUMNS;
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            int stackIndex = firstSlot + i;
            displayStacks.set(i, stackIndex >= 0 && stackIndex < stacks.size()
                    ? stacks.get(stackIndex)
                    : Collections.emptyList());
        }
        displayHandler.updateStacks(displayStacks);
    }

    private static List<List<ItemStack>> copyInputs(List<List<ItemStack>> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<ItemStack>> copied = new ArrayList<>();
        for (List<ItemStack> group : inputs) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            List<ItemStack> copiedGroup = group.stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            if (!copiedGroup.isEmpty()) {
                copied.add(copiedGroup);
            }
        }
        return copied.stream().map(List::copyOf).toList();
    }

    private static final class ScrollerProxySlotWidget extends SlotWidget {
        private ScrollerProxySlotWidget(CycleItemStackHandler itemHandler, int slotIndex,
                                        int xPosition, int yPosition) {
            super(itemHandler, slotIndex, xPosition, yPosition, false, false);
        }

        private void attachTo(WidgetGroup parent) {
            setParent(parent);
        }
    }
}
