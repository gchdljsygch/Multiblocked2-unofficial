package com.lowdragmc.mbd2.common.capability.recipe;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerLong;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import com.lowdragmc.mbd2.utils.EnergyFormatUtil;
import com.lowdragmc.mbd2.common.gui.editor.SILongConfigurator;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LongFeRecipeCapability extends RecipeCapability<Long> {
    public static final LongFeRecipeCapability CAP = new LongFeRecipeCapability();

    public static final ResourceTexture ENERGY_BAR = new ResourceTexture("mbd2:textures/gui/energy_bar_base.png");
    public static final ResourceBorderTexture ENERGY_BASE = new ResourceBorderTexture("mbd2:textures/gui/energy_bar_background.png", 42, 14, 1, 1);

    private LongFeRecipeCapability() {
        super("long_fe", SerializerLong.INSTANCE);
    }

    @Override
    public Long createDefaultContent() {
        return 128L;
    }

    @Override
    public Widget createPreviewWidget(Long content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.setBackground(new ResourceTexture("mbd2:textures/gui/forge_energy.png"));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    @Override
    public Widget createXEITemplate() {
        var energyBar = new ProgressWidget(ProgressWidget.JEIProgress, 0, 0, 50, 14, new ProgressTexture(
                IGuiTexture.EMPTY, ENERGY_BAR
        ));
        energyBar.setBackground(ENERGY_BASE);
        energyBar.setOverlay(new TextTexture("0 FE"));
        return energyBar;
    }

    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof ProgressWidget energyBar) {
            long energy = of(content.content);
            if (energyBar.getOverlay() instanceof TextTexture textTexture) {
                String v = EnergyFormatUtil.formatEnergy(energy);
                if (content.perTick) {
                    textTexture.updateText(v + " FE/t");
                } else {
                    textTexture.updateText(v + " FE");
                }
            }
        }
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<Long> supplier, Consumer<Long> onUpdate) {
        father.addConfigurators(new SILongConfigurator(
                "recipe.capability.long_fe.energy",
                supplier::get,
                onUpdate,
                0L,
                Long.MAX_VALUE,
                1L,
                true,
                "recipe.capability.long_fe.energy.tooltip"
        ));
    }

    @Override
    public Component getLeftErrorInfo(List<Long> left) {
        long sum = left.stream().mapToLong(Long::longValue).sum();
        return Component.literal(EnergyFormatUtil.formatEnergy(sum) + " FE");
    }
}
