package com.lowdragmc.mbd2.common.trait.forgeenergy;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.runtime.ConfiguratorParser;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.ToggleAutoIO;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import com.lowdragmc.mbd2.utils.EnergyFormatUtil;
import com.lowdragmc.mbd2.common.gui.editor.SILongConfigurator;
import net.minecraft.network.chat.Component;

import java.util.HashMap;

import static com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability.ENERGY_BAR;
import static com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability.ENERGY_BASE;

@LDLRegister(name = "long_fe_container", group = "trait")
public class LongFeEnergyCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {
    @Persisted
    private long capacity = 5000;
    @Persisted
    private long maxReceivePerTick = 512;
    @Persisted
    private long maxExtractPerTick = 512;

    @Configurable(
            name = "config.definition.trait.auto_io",
            subConfigurable = true,
            tips = "config.definition.trait.long_fe_container.auto_io.tooltip"
    )
    private final ToggleAutoIO autoIO = new ToggleAutoIO();

    public long getCapacity() {
        return capacity;
    }

    public long getMaxReceivePerTick() {
        return maxReceivePerTick;
    }

    public long getMaxExtractPerTick() {
        return maxExtractPerTick;
    }

    public ToggleAutoIO getAutoIO() {
        return autoIO;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurators(new SILongConfigurator(
                "config.definition.trait.long_fe_container.capacity",
                () -> capacity,
                v -> capacity = clampNonNegative(v),
                0L,
                Long.MAX_VALUE,
                5000L,
                true,
                "config.definition.trait.long_fe_container.capacity.tooltip"
        ));
        father.addConfigurators(new SILongConfigurator(
                "config.definition.trait.long_fe_container.max_receive",
                () -> maxReceivePerTick,
                v -> maxReceivePerTick = clampNonNegative(v),
                0L,
                Long.MAX_VALUE,
                512L,
                true,
                "config.definition.trait.long_fe_container.max_receive.tooltip"
        ));
        father.addConfigurators(new SILongConfigurator(
                "config.definition.trait.long_fe_container.max_extract",
                () -> maxExtractPerTick,
                v -> maxExtractPerTick = clampNonNegative(v),
                0L,
                Long.MAX_VALUE,
                512L,
                true,
                "config.definition.trait.long_fe_container.max_extract.tooltip"
        ));

        ConfiguratorParser.createConfigurators(father, new HashMap<>(), getClass(), this);
    }

    private static long clampNonNegative(long v) {
        return Math.max(0L, v);
    }

    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new LongFeEnergyCapabilityTrait(machine, this);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("mbd2:textures/gui/forge_energy.png");
    }

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

    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (!(trait instanceof LongFeEnergyCapabilityTrait energyTrait)) return;
        var prefix = uiPrefixName();
        WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), ProgressWidget.class, energyBar -> {
            energyBar.setHoverTooltips(Component.translatable(
                    "config.definition.trait.long_fe_container.ui_limits",
                    EnergyFormatUtil.formatEnergy(energyTrait.container.getMaxReceivePerTick()),
                    EnergyFormatUtil.formatEnergy(energyTrait.container.getMaxExtractPerTick())
            ));
            energyBar.setProgressSupplier(() -> {
                long cap = energyTrait.container.getEnergyCapacity();
                if (cap <= 0) return 0d;
                return energyTrait.container.getEnergyStored() * 1d / cap;
            });
            energyBar.setDynamicHoverTips(value -> LocalizationUtils.format(
                    "config.definition.trait.long_fe_container.ui_container_hover",
                    EnergyFormatUtil.formatEnergy(Math.round(energyTrait.container.getEnergyCapacity() * value)),
                    EnergyFormatUtil.formatEnergy(energyTrait.container.getEnergyCapacity())
            ));
        });
        WidgetUtils.widgetByIdForEach(group, "^%s_text$".formatted(prefix), TextTextureWidget.class, energyBarText -> {
            energyBarText.setText(() -> Component.literal(
                    EnergyFormatUtil.formatEnergy(energyTrait.container.getEnergyStored()) + "FE/" +
                            EnergyFormatUtil.formatEnergy(energyTrait.container.getEnergyCapacity()) + "FE"
            ));
        });
    }
}
