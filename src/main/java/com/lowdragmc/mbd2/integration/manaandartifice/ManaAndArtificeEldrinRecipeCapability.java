package com.lowdragmc.mbd2.integration.manaandartifice;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.SelectorConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import com.lowdragmc.mbd2.utils.FormattingUtil;
import com.mna.api.affinity.Affinity;
import com.mna.items.ItemInit;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Recipe capability for Mana and Artifice Eldrin power requirements and outputs.
 */
public class ManaAndArtificeEldrinRecipeCapability extends RecipeCapability<EldrinPower> {
    public static final ManaAndArtificeEldrinRecipeCapability CAP = new ManaAndArtificeEldrinRecipeCapability();

    protected ManaAndArtificeEldrinRecipeCapability() {
        super("mana_and_artifice_eldrin", EldrinPower.SerializerEldrinPower.INSTANCE);
    }

    @Override
    public EldrinPower createDefaultContent() {
        return new EldrinPower(Affinity.ARCANE, 100);
    }

    @Override
    public Widget createPreviewWidget(EldrinPower content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16, new ItemStackTexture(ItemInit.ELDRIN_BRACELET.get())));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(Math.round(content.amount())));
        return previewGroup;
    }

    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 110, 10,
                LocalizationUtils.format("recipe.capability.mana_and_artifice_eldrin.eldrin", 0, getAffinityName(Affinity.ARCANE)))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget textTexture) {
            var power = of(content.content);
            var text = LocalizationUtils.format("recipe.capability.mana_and_artifice_eldrin.eldrin",
                    FormattingUtil.formatNumbers(power.amount()), getAffinityName(power.affinity()));
            textTexture.setText(content.perTick ? text + "/t" : text);
        }
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<EldrinPower> supplier, Consumer<EldrinPower> onUpdate) {
        father.addConfigurators(
                new SelectorConfigurator<>("recipe.capability.mana_and_artifice_eldrin.affinity",
                        () -> supplier.get().affinity(),
                        affinity -> onUpdate.accept(new EldrinPower(affinity, supplier.get().amount())),
                        Affinity.ARCANE, true, Arrays.asList(Affinity.CoreSix()), ManaAndArtificeEldrinRecipeCapability::getAffinityName),
                new NumberConfigurator("recipe.capability.mana_and_artifice_eldrin.amount",
                        () -> supplier.get().amount(),
                        number -> onUpdate.accept(new EldrinPower(supplier.get().affinity(), number.floatValue())),
                        1, true).setRange(0, Float.MAX_VALUE)
        );
    }

    @Override
    public Component getLeftErrorInfo(List<EldrinPower> left) {
        var amount = left.stream().map(EldrinPower::amount).reduce(0f, Float::sum);
        return Component.literal(FormattingUtil.formatNumbers(amount) + " Eldrin");
    }

    public static String getAffinityName(Affinity affinity) {
        return FormattingUtil.toEnglishName(affinity.name());
    }
}
