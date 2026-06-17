package com.lowdragmc.mbd2.integration.bloodmagic;

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
import net.minecraft.network.chat.Component;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Recipe capability for Blood Magic demon will amounts and will types.
 *
 * <p>Each content value carries a type, amount, and output cap. Runtime handling drains or fills
 * ambient world will through {@link com.lowdragmc.mbd2.integration.bloodmagic.trait.BloodMagicWillTrait}.</p>
 */
public class BloodMagicWillRecipeCapability extends RecipeCapability<BloodMagicWill> {
    public static final BloodMagicWillRecipeCapability CAP = new BloodMagicWillRecipeCapability();

    protected BloodMagicWillRecipeCapability() {
        super("bloodmagic_will", BloodMagicWill.SerializerBloodMagicWill.INSTANCE);
    }

    @Override
    public BloodMagicWill createDefaultContent() {
        return BloodMagicWill.of(EnumDemonWillType.DEFAULT, 100);
    }

    /**
     * Creates the editor/list preview for a demon will requirement.
     *
     * @param content typed will amount
     * @return Demon Will Gauge icon with rounded amount overlay
     */
    @Override
    public Widget createPreviewWidget(BloodMagicWill content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16,
                new ItemStackTexture(BloodMagicItems.DEMON_WILL_GAUGE.get())));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(Math.round(content.amount())));
        return previewGroup;
    }

    /**
     * Creates the XEI text template for demon will rows.
     */
    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 100, 10,
                LocalizationUtils.format("recipe.capability.bloodmagic_will.will", 0, FormattingUtil.toEnglishName(EnumDemonWillType.DEFAULT.name())))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    /**
     * Binds will amount, type, and per-tick suffix into an XEI text widget.
     */
    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget textTexture) {
            var will = of(content.content);
            var text = LocalizationUtils.format("recipe.capability.bloodmagic_will.will",
                    FormattingUtil.formatNumbers(will.amount()), getTypeName(will.type()));
            textTexture.setText(content.perTick ? text + "/t" : text);
        }
    }

    /**
     * Adds type, amount, and max-output configurators for recipe content.
     *
     * @param father parent configurator group
     * @param supplier current will payload supplier
     * @param onUpdate callback receiving updated immutable payloads
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<BloodMagicWill> supplier, Consumer<BloodMagicWill> onUpdate) {
        father.addConfigurators(
                new SelectorConfigurator<>("recipe.capability.bloodmagic_will.type", () -> supplier.get().type(),
                        type -> onUpdate.accept(new BloodMagicWill(type, supplier.get().amount(), supplier.get().maxOutput())),
                        EnumDemonWillType.DEFAULT, true, Arrays.asList(EnumDemonWillType.values()),
                        BloodMagicWillRecipeCapability::getTypeName),
                new NumberConfigurator("recipe.capability.bloodmagic_will.amount", () -> supplier.get().amount(),
                        number -> onUpdate.accept(new BloodMagicWill(supplier.get().type(), number.doubleValue(), supplier.get().maxOutput())),
                        1, true).setRange(0, Double.MAX_VALUE),
                new NumberConfigurator("recipe.capability.bloodmagic_will.max_output", () -> supplier.get().maxOutput(),
                        number -> onUpdate.accept(new BloodMagicWill(supplier.get().type(), supplier.get().amount(), number.doubleValue())),
                        1, true).setRange(0, Double.MAX_VALUE)
        );
    }

    /**
     * Summarizes unsatisfied demon will amounts for recipe error display.
     */
    @Override
    public Component getLeftErrorInfo(List<BloodMagicWill> left) {
        double amount = left.stream().mapToDouble(BloodMagicWill::amount).sum();
        return Component.literal(FormattingUtil.formatNumbers(amount) + " will");
    }

    /**
     * Converts Blood Magic's enum names into readable UI labels.
     *
     * @param type will type
     * @return English display name
     */
    public static String getTypeName(EnumDemonWillType type) {
        return FormattingUtil.toEnglishName(type.name());
    }
}
