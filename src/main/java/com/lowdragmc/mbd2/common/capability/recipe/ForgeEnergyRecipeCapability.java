package com.lowdragmc.mbd2.common.capability.recipe;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerInteger;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import com.lowdragmc.mbd2.utils.EnergyFormattingUtil;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Recipe capability descriptor for Forge Energy amounts in Forge's integer range.
 *
 * <p>This capability is kept for standard FE integrations where all recipe amounts fit in {@code int}. Use
 * {@link LongFeRecipeCapability} for recipes that may exceed {@link Integer#MAX_VALUE}. The descriptor owns FE
 * preview/configuration/error UI; storage mutation is performed by energy traits.</p>
 */
public class ForgeEnergyRecipeCapability extends RecipeCapability<Integer> {
    public final static ForgeEnergyRecipeCapability CAP = new ForgeEnergyRecipeCapability();
    public final static ResourceTexture ENERGY_BAR = new ResourceTexture("mbd2:textures/gui/energy_bar_base.png");
    public final static ResourceBorderTexture ENERGY_BASE = new ResourceBorderTexture("mbd2:textures/gui/energy_bar_background.png", 42, 14, 1, 1);

    /**
     * Creates the singleton Forge Energy recipe capability.
     */
    protected ForgeEnergyRecipeCapability() {
        super("forge_energy", SerializerInteger.INSTANCE);
    }


    /**
     * Returns a representative FE amount for editor initialization.
     *
     * @return {@code 512} FE
     */
    @Override
    public Integer createDefaultContent() {
        return 512;
    }

    /**
     * Creates an editor preview widget with an FE icon and amount overlay.
     *
     * @param content FE amount to preview
     * @return newly created 18x18 preview widget
     */
    @Override
    public Widget createPreviewWidget(Integer content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.setBackground(new ResourceTexture("mbd2:textures/gui/forge_energy.png"));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content));
        return previewGroup;
    }

    /**
     * Creates the recipe-viewer FE bar template.
     *
     * @return unbound progress widget
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
     * Binds a formatted FE amount to the recipe-viewer bar.
     *
     * @param widget       progress widget created by {@link #createXEITemplate()}
     * @param content      recipe content wrapper
     * @param ingredientIO viewer role; currently not used by this capability
     */
    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof ProgressWidget energyBar) {
            var energy = EnergyFormattingUtil.formatExtended(of(content.content));
            if (energyBar.getOverlay() instanceof TextTexture textTexture) {
                if (content.perTick) {
                    textTexture.updateText(energy + "FE/t");
                } else {
                    textTexture.updateText(energy + "FE");
                }
            }
        }
    }

    /**
     * Creates an integer FE amount configurator.
     *
     * @param father   parent configurator group
     * @param supplier current FE amount supplier
     * @param onUpdate update callback
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<Integer> supplier, Consumer<Integer> onUpdate) {
        father.addConfigurators(new NumberConfigurator("recipe.capability.forge_energy.energy", supplier::get,
                number -> onUpdate.accept(number.intValue()), 1, true).setRange(1, Integer.MAX_VALUE));
    }

    /**
     * Builds an error component from the total missing FE.
     *
     * @param left unmatched FE amounts
     * @return literal FE amount
     */
    @Override
    public Component getLeftErrorInfo(List<Integer> left) {
        return Component.literal(left.stream().mapToInt(Integer::intValue).sum() + " fe");
    }
}
