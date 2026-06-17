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

/**
 * Recipe capability for Blood Magic Life Essence stored in a bound Soul Network.
 *
 * <p>The content value is an integer LP amount. Runtime handling is performed by
 * {@link com.lowdragmc.mbd2.integration.bloodmagic.trait.BloodMagicSoulNetworkTrait}, which
 * requires a bound Blood Orb in the machine UI.</p>
 */
public class BloodMagicSoulNetworkRecipeCapability extends RecipeCapability<Integer> {
    public static final BloodMagicSoulNetworkRecipeCapability CAP = new BloodMagicSoulNetworkRecipeCapability();

    protected BloodMagicSoulNetworkRecipeCapability() {
        super("bloodmagic_soul_network", SerializerInteger.INSTANCE);
    }

    @Override
    public Integer createDefaultContent() {
        return 1000;
    }

    /**
     * Creates the editor/list preview for a Life Essence amount.
     *
     * @param content LP amount to display
     * @return Blood Orb icon with amount overlay
     */
    @Override
    public Widget createPreviewWidget(Integer content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16,
                new ItemStackTexture(BloodMagicItems.WEAK_BLOOD_ORB.get())));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    /**
     * Creates the textual XEI template used for Life Essence recipe rows.
     *
     * @return left-aligned text widget updated by {@link #bindXEIWidget}
     */
    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 100, 10,
                LocalizationUtils.format("recipe.capability.bloodmagic_soul_network.life_essence_unit", 0))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    /**
     * Binds an LP amount into an XEI text widget.
     *
     * @param widget template widget
     * @param content recipe content payload
     * @param ingredientIO input/output role supplied by the recipe category
     */
    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget textTexture) {
            var essence = of(content.content);
            var text = LocalizationUtils.format("recipe.capability.bloodmagic_soul_network.life_essence_unit",
                    FormattingUtil.formatNumbers(essence));
            textTexture.setText(content.perTick ? text + "/t" : text);
        }
    }

    /**
     * Adds the numeric LP content configurator.
     *
     * @param father parent configurator group
     * @param supplier current LP amount supplier
     * @param onUpdate callback receiving values in the range {@code [1, Integer.MAX_VALUE]}
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<Integer> supplier, Consumer<Integer> onUpdate) {
        father.addConfigurators(new NumberConfigurator("recipe.capability.bloodmagic_soul_network.life_essence",
                supplier::get, number -> onUpdate.accept(number.intValue()), 1, true)
                .setRange(1, Integer.MAX_VALUE));
    }

    /**
     * Summarizes unsatisfied LP for recipe error display.
     */
    @Override
    public Component getLeftErrorInfo(List<Integer> left) {
        var amount = left.stream().mapToLong(Integer::longValue).sum();
        return Component.literal(FormattingUtil.formatNumbers(amount) + " LP");
    }
}
