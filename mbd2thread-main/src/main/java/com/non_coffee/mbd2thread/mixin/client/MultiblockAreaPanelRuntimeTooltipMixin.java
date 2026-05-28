package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockAreaPanel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = MultiblockAreaPanel.Runtime.class, remap = false)
public class MultiblockAreaPanelRuntimeTooltipMixin {
    @ModifyArg(
            method = "buildConfigurator",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;addWidget(Lcom/lowdragmc/lowdraglib/gui/widget/Widget;)Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;", ordinal = 0),
            index = 0,
            remap = false
    )
    private Widget mbd2thread$tooltipGeneratePattern(Widget widget) {
        return widget.setHoverTooltips(Component.literal("请将包含方块状态的多方块结构中，机器控制器朝向N（北NORTH）方向进行整体机器搭建后再点击本按钮，以获得最佳的结构视觉体验"));
    }

    @ModifyArg(
            method = "buildConfigurator",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;addWidget(Lcom/lowdragmc/lowdraglib/gui/widget/Widget;)Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;", ordinal = 1),
            index = 0,
            remap = false
    )
    private Widget mbd2thread$tooltipGenerateShapeInfo(Widget widget) {
        return widget.setHoverTooltips(Component.literal("如果遇到机器样式中方块状态异常，请手动点击该控件以获得较为舒适的正确状态"));
    }
}
