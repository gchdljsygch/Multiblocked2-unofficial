package com.lowdragmc.mbd2.integration.manaandartifice.trait;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.SelectorConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.runtime.ConfiguratorParser;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.manaandartifice.ManaAndArtificeEldrinRecipeCapability;
import com.lowdragmc.mbd2.utils.FormattingUtil;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import com.mna.api.affinity.Affinity;
import com.mna.items.ItemInit;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Configurable trait definition for Mana and Artifice Eldrin power storage.
 */
@LDLRegister(name = "mana_and_artifice_eldrin_storage", group = "trait", modID = "mna")
public class ManaAndArtificeEldrinCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.capacity")
    @NumberRange(range = {1, Float.MAX_VALUE})
    private float capacity = 1000;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.charge_rate",
            tips = "config.definition.trait.mana_and_artifice_eldrin_storage.charge_rate.tooltip")
    @NumberRange(range = {0, Float.MAX_VALUE})
    private float chargeRate = 1;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.transfer_rate",
            tips = "config.definition.trait.mana_and_artifice_eldrin_storage.transfer_rate.tooltip")
    @NumberRange(range = {-1, Float.MAX_VALUE})
    private float transferRate = -1;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.public")
    private boolean isPublic = true;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.team_share")
    private boolean shareWithTeam;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.mana_and_artifice_eldrin_storage.faction_share")
    private boolean shareWithFaction;

    private final List<Affinity> affinities = new ArrayList<>(List.of(Affinity.CoreSix()));

    public List<Affinity> getAffinities() {
        if (affinities.isEmpty()) {
            affinities.add(Affinity.ARCANE);
        }
        return affinities;
    }

    public void setAffinities(List<Affinity> values) {
        affinities.clear();
        if (values != null) {
            for (var affinity : values) {
                if (affinity != null && affinity != Affinity.UNKNOWN && !affinities.contains(affinity)) {
                    affinities.add(affinity);
                }
            }
        }
        if (affinities.isEmpty()) {
            affinities.add(Affinity.ARCANE);
        }
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        ConfiguratorParser.createConfigurators(father, new HashMap<>(), getClass(), this);
        var affinityGroup = new ArrayConfiguratorGroup<>("config.definition.trait.mana_and_artifice_eldrin_storage.affinities", true,
                () -> new ArrayList<>(getAffinities()),
                (getter, setter) -> new SelectorConfigurator<>("config.definition.trait.mana_and_artifice_eldrin_storage.affinity",
                        getter::get, setter, Affinity.ARCANE, true,
                        Arrays.asList(Affinity.CoreSix()), ManaAndArtificeEldrinRecipeCapability::getAffinityName),
                true);
        affinityGroup.setAddDefault(() -> Arrays.stream(Affinity.CoreSix())
                .filter(affinity -> !getAffinities().contains(affinity))
                .findFirst()
                .orElse(Affinity.ARCANE));
        affinityGroup.setOnAdd(affinity -> {
            var updated = new ArrayList<>(getAffinities());
            if (!updated.contains(affinity)) {
                updated.add(affinity);
            }
            setAffinities(updated);
        });
        affinityGroup.setOnRemove(affinity -> {
            var updated = new ArrayList<>(getAffinities());
            updated.remove(affinity);
            setAffinities(updated);
        });
        affinityGroup.setOnUpdate(this::setAffinities);
        father.addConfigurators(affinityGroup);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = super.serializeNBT();
        var list = new ListTag();
        for (var affinity : getAffinities()) {
            list.add(StringTag.valueOf(affinity.name()));
        }
        tag.put("affinities", list);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        var values = new ArrayList<Affinity>();
        if (nbt.contains("affinities", Tag.TAG_LIST)) {
            var list = nbt.getList("affinities", Tag.TAG_STRING);
            for (var i = 0; i < list.size(); i++) {
                values.add(com.lowdragmc.mbd2.integration.manaandartifice.EldrinPower.parseAffinity(list.getString(i)));
            }
        }
        setAffinities(values);
    }

    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new ManaAndArtificeEldrinCapabilityTrait(machine, this);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(ItemInit.ELDRIN_BRACELET.get());
    }

    @Override
    public boolean allowMultiple() {
        return false;
    }

    @Override
    public IRenderer getBESRenderer(IMachine machine) {
        return IRenderer.EMPTY;
    }

    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var text = new TextTextureWidget(0, 0, 120, 10,
                LocalizationUtils.format("config.definition.trait.mana_and_artifice_eldrin_storage.ui_container",
                        0, 0))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(uiPrefixName());
        ui.addWidget(text);
    }

    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof ManaAndArtificeEldrinCapabilityTrait eldrinTrait) {
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(uiPrefixName()), TextTextureWidget.class, text -> {
                text.setText(() -> net.minecraft.network.chat.Component.translatable(
                        "config.definition.trait.mana_and_artifice_eldrin_storage.ui_container",
                        FormattingUtil.formatNumbers(eldrinTrait.storage.getTotalCharge()),
                        FormattingUtil.formatNumbers(eldrinTrait.storage.getTotalCapacity())));
            });
        }
    }
}
