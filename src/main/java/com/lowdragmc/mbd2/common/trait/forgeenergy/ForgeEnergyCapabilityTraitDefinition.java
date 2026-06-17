package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.ToggleAutoIO;
import com.lowdragmc.mbd2.utils.EnergyFormattingUtil;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;

import static com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability.ENERGY_BAR;
import static com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability.ENERGY_BASE;

/**
 * Editable definition for Forge Energy storage traits.
 *
 * <p>The business goal is to configure an integer FE buffer, transfer limits,
 * GUI energy bar, automatic neighboring energy transfer, and optional renderer.
 * Instances are mutable editor state and should be treated as read-only by
 * runtime {@link ForgeEnergyCapabilityTrait} instances.</p>
 */
@LDLRegister(name = "forge_energy_storage", group = "trait", priority = -100)
public class ForgeEnergyCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.forge_energy_storage.capacity")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int capacity = 5000;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.forge_energy_storage.max_receive", tips = "config.definition.trait.forge_energy_storage.max_receive.tooltip")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int maxReceive = 5000;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.forge_energy_storage.max_extract", tips = "config.definition.trait.forge_energy_storage.max_extract.tooltip")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int maxExtract = 5000;
    @Getter
    @Configurable(name = "config.definition.trait.auto_io", subConfigurable = true, tips = "config.definition.trait.forge_energy_storage.auto_io.tooltip")
    private final ToggleAutoIO autoIO = new ToggleAutoIO();
    @Configurable(name = "config.definition.trait.forge_energy_storage.fancy_renderer", subConfigurable = true,
            tips = "config.definition.trait.forge_energy_storage.fancy_renderer.tooltip")
    private final ForgeEnergyFancyRendererSettings fancyRendererSettings = new ForgeEnergyFancyRendererSettings(this);

    /**
     * Creates the runtime Forge Energy trait for a machine.
     *
     * @param machine machine that will own the FE buffer
     * @return new Forge Energy capability trait
     */
    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new ForgeEnergyCapabilityTrait(machine, this);
    }

    /**
     * Returns the editor icon for Forge Energy traits.
     *
     * @return FE resource texture
     */
    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("mbd2:textures/gui/forge_energy.png");
    }

    /**
     * Returns the optional block-entity energy renderer configured by this
     * definition.
     *
     * @param machine machine whose renderer is being requested
     * @return renderer from {@link ForgeEnergyFancyRendererSettings}
     */
    @Override
    public IRenderer getBESRenderer(IMachine machine) {
        return fancyRendererSettings.getFancyRenderer(machine);
    }

    /**
     * Creates the default ModularUI energy bar template.
     *
     * <p>Side effects: adds a progress bar and a text widget to {@code ui}. Widget
     * ids use this definition's UI prefix so
     * {@link #initTraitUI(ITrait, WidgetGroup)} can bind runtime storage later.</p>
     *
     * @param ui mutable UI group receiving the energy widgets
     */
    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var prefix = uiPrefixName();
        var energyBar = new ProgressWidget(ProgressWidget.JEIProgress, 0, 0, 100, 14, new ProgressTexture(
                IGuiTexture.EMPTY, ENERGY_BAR
        ));
        energyBar.setBackground(ENERGY_BASE);
        energyBar.setId(prefix);
        var energyBarText = new TextTextureWidget(5, 2, 90, 10)
                .setText("0/0 FE")
                .textureStyle(textTexture -> textTexture.setDropShadow(true));
        energyBarText.setId(prefix + "_text");
        ui.addWidget(energyBar);
        ui.addWidget(energyBarText);
    }

    /**
     * Binds runtime FE storage to template energy widgets.
     *
     * <p>Side effects: mutates matching widgets by assigning progress suppliers,
     * dynamic hover text, and dynamic compact stored/capacity text.</p>
     *
     * @param trait runtime trait instance
     * @param group UI group containing template energy widgets
     */
    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof ForgeEnergyCapabilityTrait forgeEnergyTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), ProgressWidget.class, energyBar -> {
                energyBar.setProgressSupplier(() -> forgeEnergyTrait.storage.getEnergyStored() * 1d / forgeEnergyTrait.storage.getMaxEnergyStored());
                energyBar.setDynamicHoverTips(value -> {
                    var stored = EnergyFormattingUtil.formatExtended(Math.round(forgeEnergyTrait.storage.getMaxEnergyStored() * value));
                    var maxStored = EnergyFormattingUtil.formatExtended(forgeEnergyTrait.storage.getMaxEnergyStored());
                    return LocalizationUtils.format("config.definition.trait.forge_energy_storage.ui_container_hover", stored, maxStored);
                });
            });
            WidgetUtils.widgetByIdForEach(group, "^%s_text$".formatted(prefix), TextTextureWidget.class, energyBarText -> {
                energyBarText.setText(() -> {
                    var stored = EnergyFormattingUtil.formatCompact(forgeEnergyTrait.storage.getEnergyStored()) + "FE";
                    var maxStored = EnergyFormattingUtil.formatCompact(forgeEnergyTrait.storage.getMaxEnergyStored()) + "FE";
                    return Component.literal(stored + "/" + maxStored);
                });
            });
        }
    }
}
