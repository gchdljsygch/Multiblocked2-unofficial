package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.gui.graphprocessor.widget.NodePanelWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Replaces LDLib's hard-coded graph-node search label with an MBD2 translation key.
 *
 * <p>The mixin modifies only the constructor argument for the label text in the node panel. The
 * surrounding widget layout, search behavior, and filtering logic remain owned by LDLib.</p>
 */
@Mixin(value = NodePanelWidget.class, remap = false)
public class LDLibNodePanelSearchI18nMixin {
    /**
     * Substitutes the fixed {@code Search:} label with a localizable key.
     *
     * @param text original label constructor argument
     * @return translation key for the known search label, otherwise the unchanged argument
     */
    @ModifyArg(
            method = "loadWidgets",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/widget/LabelWidget;<init>(IILjava/lang/String;)V"),
            index = 2,
            remap = false
    )
    private String mbd2$translateSearchLabel(String text) {
        if ("Search:".equals(text)) {
            return "mbd2.gp.search";
        }
        return text;
    }
}
