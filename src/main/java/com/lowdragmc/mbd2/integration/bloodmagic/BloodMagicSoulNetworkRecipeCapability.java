package com.lowdragmc.mbd2.integration.bloodmagic;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator;
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
import com.lowdragmc.mbd2.api.recipe.content.SerializerInteger;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import com.lowdragmc.mbd2.utils.FormattingUtil;
import net.minecraft.network.chat.Component;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BloodMagicSoulNetworkRecipeCapability extends RecipeCapability<Integer> {
    public static final BloodMagicSoulNetworkRecipeCapability CAP = new BloodMagicSoulNetworkRecipeCapability();

    protected BloodMagicSoulNetworkRecipeCapability() {
        super("bloodmagic_soul_network", SerializerInteger.INSTANCE);
    }

    @Override
    public Integer createDefaultContent() {
        return 1000;
    }

    @Override
    public Widget createPreviewWidget(Integer content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16,
                new ItemStackTexture(BloodMagicItems.WEAK_BLOOD_ORB.get())));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 100, 10,
                LocalizationUtils.format("recipe.capability.bloodmagic_soul_network.life_essence_unit", 0))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget textTexture) {
            var essence = of(content.content);
            var text = LocalizationUtils.format("recipe.capability.bloodmagic_soul_network.life_essence_unit",
                    FormattingUtil.formatNumbers(essence));
            textTexture.setText(content.perTick ? text + "/t" : text);
        }
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<Integer> supplier, Consumer<Integer> onUpdate) {
        father.addConfigurators(new NumberConfigurator("recipe.capability.bloodmagic_soul_network.life_essence",
                supplier::get, number -> onUpdate.accept(number.intValue()), 1, true)
                .setRange(1, Integer.MAX_VALUE));
    }

    @Override
    public Component getLeftErrorInfo(List<Integer> left) {
        var amount = left.stream().mapToLong(Integer::longValue).sum();
        return Component.literal(FormattingUtil.formatNumbers(amount) + " LP");
    }
}
