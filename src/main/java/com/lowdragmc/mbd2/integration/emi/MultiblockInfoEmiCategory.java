package com.lowdragmc.mbd2.integration.emi;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.pattern.IMultiblockXEI;
import com.lowdragmc.mbd2.api.registry.MultiblockXEIRegistry;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * EMI category that shows preview pages for registered multiblock machine structures.
 */
public class MultiblockInfoEmiCategory extends EmiRecipeCategory {

    /**
     * EMI display wrapper for a single multiblock XEI entry.
     */
    public static class MultiblockInfoEmiRecipe extends ModularEmiRecipe<WidgetGroup> {

        public final IMultiblockXEI entry;
        @Nullable
        public final MultiblockMachineDefinition definition;

        public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
            this(IMultiblockXEI.of(definition), definition);
        }

        public MultiblockInfoEmiRecipe(IMultiblockXEI entry) {
            this(entry, null);
        }

        private MultiblockInfoEmiRecipe(IMultiblockXEI entry, @Nullable MultiblockMachineDefinition definition) {
            this(entry, definition, new PatternWidgetSupplier(entry));
        }

        private MultiblockInfoEmiRecipe(IMultiblockXEI entry, @Nullable MultiblockMachineDefinition definition,
                                        PatternWidgetSupplier widgetSupplier) {
            super(widgetSupplier);
            this.entry = entry;
            this.definition = definition;
            inputs.clear();
            inputs.addAll(createPatternInputs(entry, widgetSupplier.initialWidget));
            outputs.clear();
            var workstation = entry.getWorkstation();
            if (!workstation.isEmpty()) {
                outputs.add(EmiStack.of(workstation));
            }
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return MultiblockInfoEmiCategory.CATEGORY;
        }

        @Override
        public @Nullable ResourceLocation getId() {
            return entry.getId();
        }

        @Override
        public void clearSlotWidgetHandler(SlotWidget slotW, int slotIndex) {
            super.clearSlotWidgetHandler(slotW, slotIndex);
        }

        private static List<EmiIngredient> createPatternInputs(IMultiblockXEI entry, WidgetGroup widget) {
            var inputs = entry.getInputs(widget);
            return inputs.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(MultiblockInfoEmiRecipe::createInput)
                    .filter(input -> !input.isEmpty())
                    .toList();
        }

        private static EmiIngredient createInput(List<ItemStack> candidates) {
            var stacks = candidates.stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .map(stack -> EmiStack.of(stack.copy(), stack.getCount()))
                    .toList();
            if (stacks.isEmpty()) {
                return EmiStack.EMPTY;
            }
            if (stacks.size() == 1) {
                return stacks.get(0);
            }
            return EmiIngredient.of(stacks);
        }

        private static final class PatternWidgetSupplier implements Supplier<WidgetGroup> {

            private final IMultiblockXEI entry;
            private WidgetGroup initialWidget;

            private PatternWidgetSupplier(IMultiblockXEI entry) {
                this.entry = entry;
            }

            @Override
            public WidgetGroup get() {
                var widget = entry.createWidget();
                if (initialWidget == null) {
                    initialWidget = widget;
                }
                return widget;
            }
        }
    }

    public static final MultiblockInfoEmiCategory CATEGORY = new MultiblockInfoEmiCategory();

    private MultiblockInfoEmiCategory() {
        super(MBD2.id("multiblock_info"), new EmiTexture(MBD2.id("textures/gui/multiblock_info_page.png"), 0, 0, 16, 16, 16, 16, 16, 16));
    }

    public static void registerDisplays(EmiRegistry registry) {
        MultiblockXEIRegistry.entries().stream()
                .map(MultiblockInfoEmiRecipe::new)
                .forEach(registry::addRecipe);
    }

    public static void registerWorkStations(EmiRegistry registry) {
        for (var entry : MultiblockXEIRegistry.entries()) {
            var workstation = entry.getWorkstation();
            if (!workstation.isEmpty()) {
                registry.addWorkstation(CATEGORY, EmiStack.of(workstation));
            }
        }
    }

    @Override
    public Component getName() {
        return Component.translatable("mbd2.jei.multiblock_info");
    }
}
