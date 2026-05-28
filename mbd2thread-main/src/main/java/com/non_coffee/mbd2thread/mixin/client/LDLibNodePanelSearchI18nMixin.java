package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.lowdraglib.gui.graphprocessor.widget.NodePanelWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = NodePanelWidget.class, remap = false)
public class LDLibNodePanelSearchI18nMixin {
    @ModifyArg(
            method = "loadWidgets",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/widget/LabelWidget;<init>(IILjava/lang/String;)V"),
            index = 2,
            remap = false
    )
    private String mbd2thread$translateSearchLabel(String text) {
        if ("Search:".equals(text)) {
            return "mbd2thread.gp.search";
        }
        return text;
    }
}
