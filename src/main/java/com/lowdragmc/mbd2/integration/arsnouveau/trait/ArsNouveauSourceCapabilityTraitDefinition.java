package com.lowdragmc.mbd2.integration.arsnouveau.trait;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
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
import com.lowdragmc.mbd2.integration.arsnouveau.ArsNouveauSourceRecipeCapability;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;

/**
 * Trait definition for configurable Ars Nouveau Source storage.
 *
 * <p>The definition owns capacity, per-transfer auto IO rate, optional auto IO settings, and a
 * client-side fancy renderer. Only one Source storage trait is allowed per machine because relays
 * and UI widgets address the trait by definition.</p>
 */
@LDLRegister(name = "ars_nouveau_source_storage", group = "trait", modID = "ars_nouveau")
public class ArsNouveauSourceCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.ars_nouveau_source_storage.capacity")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int capacity = 10000;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.ars_nouveau_source_storage.transfer_rate",
            tips = "config.definition.trait.ars_nouveau_source_storage.transfer_rate.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int transferRate = 10000;
    @Getter
    @Configurable(name = "config.definition.trait.auto_io", subConfigurable = true, tips = "config.definition.trait.ars_nouveau_source_storage.auto_io.tooltip")
    private final ToggleAutoIO autoIO = new ToggleAutoIO();
    @Configurable(name = "config.definition.trait.ars_nouveau_source_storage.fancy_renderer", subConfigurable = true,
            tips = "config.definition.trait.ars_nouveau_source_storage.fancy_renderer.tooltip")
    private final ArsNouveauSourceFancyRendererSettings fancyRendererSettings = new ArsNouveauSourceFancyRendererSettings(this);

    /**
     * Creates the runtime Source storage trait for a machine instance.
     *
     * @param machine owning machine
     * @return configured Source trait
     */
    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new ArsNouveauSourceCapabilityTrait(machine, this);
    }

    /**
     * Returns the Source Jar icon used in trait lists.
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(BlockRegistry.SOURCE_JAR.asItem());
    }

    /**
     * Source relay and UI bindings assume a single Source storage definition per machine.
     */
    @Override
    public boolean allowMultiple() {
        return false;
    }

    /**
     * Creates the block-entity renderer configured by {@link ArsNouveauSourceFancyRendererSettings}.
     */
    @Override
    public IRenderer getBESRenderer(IMachine machine) {
        return fancyRendererSettings.getFancyRenderer(machine);
    }

    /**
     * Builds the progress bar and text widgets used by machine UI templates.
     *
     * @param ui template root to receive Source widgets
     */
    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var prefix = uiPrefixName();
        var sourceBar = new ProgressWidget(ProgressWidget.JEIProgress, 0, 5, 100, 5, new ProgressTexture(
                IGuiTexture.EMPTY, ArsNouveauSourceRecipeCapability.HUD_BAR.copy().setColor(ColorPattern.PURPLE.color)
        ));
        sourceBar.setBackground(ArsNouveauSourceRecipeCapability.HUD_BACKGROUND);
        sourceBar.setId(prefix);
        var sourceBarText = new TextTextureWidget(5, 3, 90, 10)
                .setText("0/0 source")
                .textureStyle(textTexture -> textTexture.setDropShadow(true));
        sourceBarText.setId(prefix + "_text");
        ui.addWidget(sourceBar);
        ui.addWidget(sourceBarText);
    }

    /**
     * Binds live Source storage values to the UI widgets created by {@link #createTraitUITemplate}.
     *
     * @param trait runtime trait instance
     * @param group concrete UI group containing the template widgets
     */
    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof ArsNouveauSourceCapabilityTrait sourceTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), ProgressWidget.class, sourceBar -> {
                sourceBar.setProgressSupplier(() -> sourceTrait.storage.getSource() * 1d / sourceTrait.storage.getMaxSource());
                sourceBar.setDynamicHoverTips(progress -> LocalizationUtils.format(
                        "config.definition.trait.ars_nouveau_source_storage.ui_container_hover",
                        Math.round(sourceTrait.storage.getMaxSource() * progress), sourceTrait.storage.getMaxSource()));
            });
            WidgetUtils.widgetByIdForEach(group, "^%s_text$".formatted(prefix), TextTextureWidget.class, sourceBarText -> {
                sourceBarText.setText(() -> Component.literal(sourceTrait.storage.getSource() + "/" + sourceTrait.storage.getMaxSource() + " source"));
            });
        }
    }
}
