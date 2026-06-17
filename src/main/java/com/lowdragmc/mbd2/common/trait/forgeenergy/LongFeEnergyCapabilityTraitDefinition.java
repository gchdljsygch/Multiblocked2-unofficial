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

/**
 * Editable definition for long-precision Forge Energy traits.
 *
 * <p>The business goal is to configure energy buffers whose capacity and
 * transfer limits may exceed Forge Energy's {@code int} range, while still
 * providing the same editor UI and automatic IO controls as ordinary Forge
 * Energy storage. Values are clamped to non-negative {@code long} values during
 * editor updates. Definition instances are mutable editor state and should be
 * treated as read-only by runtime {@link LongFeEnergyCapabilityTrait}
 * instances.</p>
 */
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

    /**
     * Returns maximum long FE capacity.
     *
     * @return configured capacity in FE, from {@code 0} to
     * {@link Long#MAX_VALUE}
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Returns maximum input transfer per automatic IO or capability call.
     *
     * @return configured FE receive limit per tick/call, non-negative
     */
    public long getMaxReceivePerTick() {
        return maxReceivePerTick;
    }

    /**
     * Returns maximum output transfer per automatic IO or capability call.
     *
     * @return configured FE extract limit per tick/call, non-negative
     */
    public long getMaxExtractPerTick() {
        return maxExtractPerTick;
    }

    /**
     * Returns side-based automatic IO configuration.
     *
     * @return mutable auto IO settings owned by this definition
     */
    public ToggleAutoIO getAutoIO() {
        return autoIO;
    }

    /**
     * Builds editor configurators for long energy values and auto IO.
     *
     * <p>Side effects: appends configurators to {@code father}. Long values are
     * edited with {@link SILongConfigurator} so they do not lose precision through
     * integer-only editor controls.</p>
     *
     * @param father parent configurator group receiving controls
     */
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

    /**
     * Clamps editor input to the supported non-negative range.
     *
     * @param v candidate value
     * @return {@code max(0, v)}
     */
    private static long clampNonNegative(long v) {
        return Math.max(0L, v);
    }

    /**
     * Creates the runtime long FE trait for a machine.
     *
     * @param machine machine that will own the energy container
     * @return new long FE capability trait
     */
    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new LongFeEnergyCapabilityTrait(machine, this);
    }

    /**
     * Returns the editor icon for long FE traits.
     *
     * @return FE resource texture
     */
    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("mbd2:textures/gui/forge_energy.png");
    }

    /**
     * Creates the default ModularUI long FE energy bar template.
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
     * Binds runtime long FE storage to template energy widgets.
     *
     * <p>Side effects: mutates matching widgets by assigning progress suppliers,
     * dynamic hover text, transfer-limit tooltip text, and dynamic stored/capacity
     * text.</p>
     *
     * @param trait runtime trait instance
     * @param group UI group containing template energy widgets
     */
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
