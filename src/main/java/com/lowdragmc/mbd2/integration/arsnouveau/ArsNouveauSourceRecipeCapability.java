package com.lowdragmc.mbd2.integration.arsnouveau;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerInteger;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ArsNouveauSourceRecipeCapability extends RecipeCapability<Integer> {
    public static final ArsNouveauSourceRecipeCapability CAP = new ArsNouveauSourceRecipeCapability();
    public static final ResourceTexture HUD_BACKGROUND = new ResourceTexture("mbd2:textures/gui/mana_hud.png").getSubTexture(0, 0, 1, 0.5);
    public static final ResourceTexture HUD_BAR = new ResourceTexture("mbd2:textures/gui/mana_hud.png").getSubTexture(0, 0.5, 1, 0.5);

    protected ArsNouveauSourceRecipeCapability() {
        super("ars_nouveau_source", SerializerInteger.INSTANCE);
    }

    @Override
    public Integer createDefaultContent() {
        return 1000;
    }

    @Override
    public Widget createPreviewWidget(Integer content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16, new ItemStackTexture(BlockRegistry.SOURCE_JAR.asItem())));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    @Override
    public Widget createXEITemplate() {
        var sourceBar = new ProgressWidget(ProgressWidget.JEIProgress, 0, 0, 100, 5, new ProgressTexture(
                IGuiTexture.EMPTY, HUD_BAR.copy().setColor(ColorPattern.PURPLE.color)
        ));
        sourceBar.setBackground(HUD_BACKGROUND);
        sourceBar.setOverlay(new TextTexture("0 source"));
        return sourceBar;
    }

    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof ProgressWidget sourceBar) {
            var source = of(content.content);
            if (sourceBar.getOverlay() instanceof TextTexture textTexture) {
                if (content.perTick) {
                    textTexture.updateText(source + " source/t");
                } else {
                    textTexture.updateText(source + " source");
                }
            }
        }
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<Integer> supplier, Consumer<Integer> onUpdate) {
        father.addConfigurators(new NumberConfigurator("recipe.capability.ars_nouveau_source.source", supplier::get,
                number -> onUpdate.accept(number.intValue()), 1, true).setRange(1, Integer.MAX_VALUE));
    }

    @Override
    public Component getLeftErrorInfo(List<Integer> left) {
        return Component.literal(left.stream().mapToInt(Integer::intValue).sum() + " source");
    }
}
