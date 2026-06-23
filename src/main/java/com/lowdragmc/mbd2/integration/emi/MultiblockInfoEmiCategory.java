package com.lowdragmc.mbd2.integration.emi;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.pattern.PatternPreviewWidget;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
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
     * EMI display wrapper for a single multiblock machine definition preview.
     */
    public static class MultiblockInfoEmiRecipe extends ModularEmiRecipe<WidgetGroup> {

        public final MultiblockMachineDefinition definition;

        public MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition) {
            this(definition, new PatternWidgetSupplier(definition));
        }

        private MultiblockInfoEmiRecipe(MultiblockMachineDefinition definition, PatternWidgetSupplier widgetSupplier) {
            super(widgetSupplier);
            this.definition = definition;
            inputs.clear();
            inputs.addAll(createPatternInputs(widgetSupplier.initialWidget));
            outputs.clear();
            outputs.add(EmiStack.of(definition.asStack()));
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return MultiblockInfoEmiCategory.CATEGORY;
        }

        @Override
        public @Nullable ResourceLocation getId() {
            return definition.id();
        }

        @Override
        public void clearSlotWidgetHandler(SlotWidget slotW, int slotIndex) {
            super.clearSlotWidgetHandler(slotW, slotIndex);
        }

        private static List<EmiIngredient> createPatternInputs(PatternPreviewWidget widget) {
            return widget.getCurrentPatternParts().stream()
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

            private final MultiblockMachineDefinition definition;
            private PatternPreviewWidget initialWidget;

            private PatternWidgetSupplier(MultiblockMachineDefinition definition) {
                this.definition = definition;
            }

            @Override
            public WidgetGroup get() {
                var widget = PatternPreviewWidget.getPatternWidget(definition);
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
        MBDRegistries.MACHINE_DEFINITIONS.values().stream()
                .filter(MultiblockMachineDefinition.class::isInstance)
                .map(MultiblockMachineDefinition.class::cast)
                .map(MultiblockInfoEmiRecipe::new)
                .forEach(registry::addRecipe);
    }

    public static void registerWorkStations(EmiRegistry registry) {
        for (var definition : MBDRegistries.MACHINE_DEFINITIONS.values()) {
            if (definition instanceof MultiblockMachineDefinition multiblockDefinition) {
                registry.addWorkstation(CATEGORY, EmiStack.of(multiblockDefinition.asStack()));
            }
        }
    }

    @Override
    public Component getName() {
        return Component.translatable("mbd2.jei.multiblock_info");
    }
}
