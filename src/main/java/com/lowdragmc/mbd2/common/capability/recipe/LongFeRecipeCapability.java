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

/**
 * Recipe capability descriptor for long-range Forge Energy amounts.
 *
 * <p>This capability mirrors the regular FE recipe capability but stores amounts as {@code long}, allowing recipes
 * above Forge's {@code int} energy limit. UI formatting uses compact FE units and appends {@code /t} when content is
 * marked as per-tick.</p>
 */
public class LongFeRecipeCapability extends RecipeCapability<Long> {
    public static final LongFeRecipeCapability CAP = new LongFeRecipeCapability();

    public static final ResourceTexture ENERGY_BAR = new ResourceTexture("mbd2:textures/gui/energy_bar_base.png");
    public static final ResourceBorderTexture ENERGY_BASE = new ResourceBorderTexture("mbd2:textures/gui/energy_bar_background.png", 42, 14, 1, 1);

    /**
     * Creates the singleton long-FE recipe capability.
     */
    private LongFeRecipeCapability() {
        super("long_fe", SerializerLong.INSTANCE);
    }

    /**
     * Returns a representative energy amount for editor initialization.
     *
     * @return {@code 128} FE
     */
    @Override
    public Long createDefaultContent() {
        return 128L;
    }

    /**
     * Creates an editor preview widget with an energy icon and amount overlay.
     *
     * @param content FE amount to preview
     * @return newly created 18x18 preview widget
     */
    @Override
    public Widget createPreviewWidget(Long content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.setBackground(new ResourceTexture("mbd2:textures/gui/forge_energy.png"));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    /**
     * Creates the recipe-viewer energy bar template.
     *
     * @return unbound XEI widget template
     */
    @Override
    public Widget createXEITemplate() {
        var energyBar = new ProgressWidget(ProgressWidget.JEIProgress, 0, 0, 50, 14, new ProgressTexture(
                IGuiTexture.EMPTY, ENERGY_BAR
        ));
        energyBar.setBackground(ENERGY_BASE);
        energyBar.setOverlay(new TextTexture("0 FE"));
        return energyBar;
    }

    /**
     * Binds a formatted FE amount to the recipe-viewer energy bar.
     *
     * @param widget       widget created by {@link #createXEITemplate()}
     * @param content      recipe content wrapper
     * @param ingredientIO viewer role; currently not used by this capability
     */
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

    /**
     * Creates a long-value energy configurator.
     *
     * @param father   parent configurator group
     * @param supplier current FE amount supplier
     * @param onUpdate update callback
     */
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

    /**
     * Builds an error component from the total missing FE.
     *
     * @param left unmatched FE amounts
     * @return literal formatted FE amount
     */
    @Override
    public Component getLeftErrorInfo(List<Long> left) {
        long sum = left.stream().mapToLong(Long::longValue).sum();
        return Component.literal(EnergyFormatUtil.formatEnergy(sum) + " FE");
    }
}
