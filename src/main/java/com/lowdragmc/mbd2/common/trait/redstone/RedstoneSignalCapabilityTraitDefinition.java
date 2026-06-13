package com.lowdragmc.mbd2.common.trait.redstone;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTraitDefinition;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

@LDLRegister(name = "redstone_signal", group = "trait", priority = -100)
public class RedstoneSignalCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {

    public RedstoneSignalCapabilityTraitDefinition() {
        setRecipeHandlerIO(IO.BOTH);
    }

    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new RedstoneSignalCapabilityTrait(machine, this);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.REDSTONE_TORCH);
    }

    @Override
    public boolean allowMultiple() {
        return false;
    }

    @Override
    public IRenderer getBESRenderer(IMachine machine) {
        return IRenderer.EMPTY;
    }

    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var text = new TextTextureWidget(0, 0, 120, 10,
                LocalizationUtils.format("config.definition.trait.redstone_signal.ui", 0, 0, 0))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(uiPrefixName());
        ui.addWidget(text);
    }

    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof RedstoneSignalCapabilityTrait redstoneTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), TextTextureWidget.class, text -> {
                text.setText(() -> Component.translatable("config.definition.trait.redstone_signal.ui",
                        redstoneTrait.getInputSignal(),
                        redstoneTrait.getStrongestOutputSignal(),
                        redstoneTrait.getMaxRemainingTicks()));
            });
        }
    }
}
