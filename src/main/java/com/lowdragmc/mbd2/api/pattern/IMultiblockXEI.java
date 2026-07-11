package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client-side description of a multiblock page shown by an XEI recipe viewer.
 *
 * <p>External mods can implement this interface when their multiblock is not
 * represented by an {@link MultiblockMachineDefinition}. The preview widget is
 * created lazily because XEI registration happens after the client level is
 * available. The same entry is consumed by JEI, REI, and EMI.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface IMultiblockXEI {

    /**
     * Stable id used by recipe viewers, including EMI's recipe id.
     *
     * @return non-null entry id
     */
    @NotNull
    ResourceLocation getId();

    /**
     * Creates a fresh preview widget for a recipe-viewer page.
     *
     * @return non-null widget group
     */
    @NotNull
    WidgetGroup createWidget();

    /**
     * Returns the item used to open the page as a workstation/catalyst.
     *
     * @return non-null item stack, or {@link ItemStack#EMPTY} when the page has
     * no workstation item
     */
    @NotNull
    default ItemStack getWorkstation() {
        return ItemStack.EMPTY;
    }

    /**
     * Supplies logical input groups for viewers that need a recipe-level input
     * list, such as EMI. JEI and REI can discover slots directly from the
     * returned widget.
     *
     * <p>The default implementation exposes the currently selected pattern
     * parts when the widget is a {@link PatternPreviewWidget}. Custom widgets
     * can override this method to expose their own input groups.</p>
     *
     * @param widget widget returned by {@link #createWidget()}
     * @return non-null list of candidate stack groups
     */
    @NotNull
    default List<List<ItemStack>> getInputs(WidgetGroup widget) {
        if (widget instanceof PatternPreviewWidget patternPreview) {
            return patternPreview.getCurrentPatternParts();
        }
        return List.of();
    }

    /**
     * Creates an XEI entry for an MBD multiblock definition.
     *
     * @param definition definition to preview
     * @return lazily-created entry backed by the standard pattern widget
     */
    static IMultiblockXEI of(MultiblockMachineDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return of(definition.id(),
                () -> PatternPreviewWidget.getPatternWidget(definition),
                definition::asStack);
    }

    /**
     * Creates an entry backed by a custom widget supplier.
     *
     * @param id               stable recipe-viewer id
     * @param widgetSupplier   lazy supplier for a fresh preview widget
     * @param workstation      workstation/catalyst stack supplier
     * @return external XEI entry
     */
    static IMultiblockXEI of(ResourceLocation id, Supplier<? extends WidgetGroup> widgetSupplier,
                             Supplier<? extends ItemStack> workstation) {
        return of(id, widgetSupplier, workstation, null);
    }

    /**
     * Creates an entry backed by a custom widget and custom EMI input provider.
     *
     * @param id               stable recipe-viewer id
     * @param widgetSupplier   lazy supplier for a fresh preview widget
     * @param workstation      workstation/catalyst stack supplier
     * @param inputProvider    maps the created widget to logical input groups;
     *                         {@code null} uses {@link #getInputs(WidgetGroup)}
     * @return external XEI entry
     */
    static IMultiblockXEI of(ResourceLocation id, Supplier<? extends WidgetGroup> widgetSupplier,
                             Supplier<? extends ItemStack> workstation,
                             Function<WidgetGroup, List<List<ItemStack>>> inputProvider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(widgetSupplier, "widgetSupplier");
        Supplier<? extends ItemStack> workstationSupplier = workstation == null ? () -> ItemStack.EMPTY : workstation;
        return new BasicMultiblockXEI(id, widgetSupplier, workstationSupplier, inputProvider);
    }

    /**
     * Small immutable implementation used by the factory methods above.
     */
    final class BasicMultiblockXEI implements IMultiblockXEI {
        private final ResourceLocation id;
        private final Supplier<? extends WidgetGroup> widgetSupplier;
        private final Supplier<? extends ItemStack> workstationSupplier;
        private final Function<WidgetGroup, List<List<ItemStack>>> inputProvider;

        private BasicMultiblockXEI(ResourceLocation id, Supplier<? extends WidgetGroup> widgetSupplier,
                                   Supplier<? extends ItemStack> workstationSupplier,
                                   Function<WidgetGroup, List<List<ItemStack>>> inputProvider) {
            this.id = id;
            this.widgetSupplier = widgetSupplier;
            this.workstationSupplier = workstationSupplier;
            this.inputProvider = inputProvider;
        }

        @Override
        @NotNull
        public ResourceLocation getId() {
            return id;
        }

        @Override
        @NotNull
        public WidgetGroup createWidget() {
            return Objects.requireNonNull(widgetSupplier.get(), "widgetSupplier returned null");
        }

        @Override
        @NotNull
        public ItemStack getWorkstation() {
            ItemStack workstation = workstationSupplier.get();
            return workstation == null ? ItemStack.EMPTY : workstation.copy();
        }

        @Override
        @NotNull
        public List<List<ItemStack>> getInputs(WidgetGroup widget) {
            if (inputProvider == null) {
                return IMultiblockXEI.super.getInputs(widget);
            }
            List<List<ItemStack>> inputs = inputProvider.apply(widget);
            return inputs == null ? List.of() : inputs;
        }
    }
}
