package com.lowdragmc.mbd2.core.mixins.jei;

import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IRecipeSlotBuilderAccessor;
import com.lowdragmc.mbd2.api.pattern.PatternPreviewWidget;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves LDLib slot labels into JEI's single recipe-slot tooltip.
 *
 * <p>JEI changed {@code build}'s internal {@code Pair} package during the
 * 15.x line. Targeting the overload name keeps this mixin compatible with
 * both descriptors, while the guard below prevents duplicate callbacks.</p>
 */
@Mixin(value = RecipeSlotBuilder.class, remap = false)
public abstract class RecipeSlotBuilderTooltipMixin {

    @Unique
    private boolean mbd2$tooltipCallbackAdded;

    @Inject(
            method = "build",
            at = @At("HEAD"),
            remap = false
    )
    @SuppressWarnings("removal")
    private void mbd2$appendSlotLabelsToJeiTooltip(CallbackInfoReturnable<?> cir) {
        if (mbd2$tooltipCallbackAdded) {
            return;
        }

        if (!((Object) this instanceof IRecipeSlotBuilderAccessor accessor)) {
            return;
        }
        IRecipeIngredientSlot slot = accessor.lowDragLib$getRecipeIngredientSlot();
        if (slot == null || !mbd2$isPatternPreviewSlot(slot)) {
            return;
        }

        mbd2$tooltipCallbackAdded = true;
        IRecipeSlotBuilder builder = (IRecipeSlotBuilder) (Object) this;
        builder.addRichTooltipCallback((view, tooltip) -> {
            var labels = slot.self().getTooltipTexts();
            if (labels.isEmpty()) {
                return;
            }
            tooltip.removeAll(labels);
            tooltip.addAll(labels);
        });
    }

    private static boolean mbd2$isPatternPreviewSlot(IRecipeIngredientSlot slot) {
        for (Widget widget = slot.self(); widget != null; widget = widget.getParent()) {
            if (widget instanceof PatternPreviewWidget) {
                return true;
            }
        }
        return false;
    }
}
