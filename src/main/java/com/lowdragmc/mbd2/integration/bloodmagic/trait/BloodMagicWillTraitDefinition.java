package com.lowdragmc.mbd2.integration.bloodmagic.trait;

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
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.IUIProviderTrait;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWill;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWillRecipeCapability;
import com.lowdragmc.mbd2.utils.FormattingUtil;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.common.item.BloodMagicItems;
import wayoftime.bloodmagic.demonaura.WorldDemonWillHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@LDLRegister(name = "bloodmagic_will_handler", group = "trait", modID = "bloodmagic")
public class BloodMagicWillTraitDefinition extends RecipeCapabilityTraitDefinition implements IUIProviderTrait {
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.bloodmagic_will_handler.max_input",
            tips = "config.definition.trait.bloodmagic_will_handler.max_input.tooltip")
    @NumberRange(range = {0, Double.MAX_VALUE})
    private double maxInput = 100;

    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.bloodmagic_will_handler.max_output",
            tips = "config.definition.trait.bloodmagic_will_handler.max_output.tooltip")
    @NumberRange(range = {0, Double.MAX_VALUE})
    private double maxOutput = 100;

    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.bloodmagic_will_handler.chunk_radius",
            tips = "config.definition.trait.bloodmagic_will_handler.chunk_radius.tooltip")
    @NumberRange(range = {0, 16})
    private int chunkRadius = 0;

    private final List<EnumDemonWillType> willTypes = new ArrayList<>(List.of(EnumDemonWillType.DEFAULT));

    public List<EnumDemonWillType> getWillTypes() {
        if (willTypes.isEmpty()) {
            willTypes.add(EnumDemonWillType.DEFAULT);
        }
        return willTypes;
    }

    public void setWillTypes(List<EnumDemonWillType> types) {
        willTypes.clear();
        if (types != null) {
            for (var type : types) {
                if (type != null && !willTypes.contains(type)) {
                    willTypes.add(type);
                }
            }
        }
        if (willTypes.isEmpty()) {
            willTypes.add(EnumDemonWillType.DEFAULT);
        }
    }

    public boolean acceptsType(EnumDemonWillType type) {
        return getWillTypes().contains(type);
    }

    public String getWillTypesName() {
        return getWillTypes().stream()
                .map(BloodMagicWillRecipeCapability::getTypeName)
                .reduce((left, right) -> left + ", " + right)
                .orElseGet(() -> BloodMagicWillRecipeCapability.getTypeName(EnumDemonWillType.DEFAULT));
    }

    public List<BlockPos> getChunkSamplePositions(BlockPos origin) {
        var radius = Math.max(0, chunkRadius);
        var originChunkX = origin.getX() >> 4;
        var originChunkZ = origin.getZ() >> 4;
        var positions = new ArrayList<BlockPos>();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                positions.add(new BlockPos(((originChunkX + dx) << 4) + 8, origin.getY(), ((originChunkZ + dz) << 4) + 8));
            }
        }
        positions.sort(Comparator.comparingInt(pos ->
                Math.abs((pos.getX() >> 4) - originChunkX) + Math.abs((pos.getZ() >> 4) - originChunkZ)));
        return positions;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        ConfiguratorParser.createConfigurators(father, new HashMap<>(), getClass(), this);
        var willTypeGroup = new ArrayConfiguratorGroup<>("config.definition.trait.bloodmagic_will_handler.types", true,
                () -> new ArrayList<>(getWillTypes()),
                (getter, setter) -> new SelectorConfigurator<>("config.definition.trait.bloodmagic_will_handler.type",
                        getter::get, setter, EnumDemonWillType.DEFAULT, true,
                        Arrays.asList(EnumDemonWillType.values()), BloodMagicWillRecipeCapability::getTypeName),
                true);
        willTypeGroup.setAddDefault(() -> Arrays.stream(EnumDemonWillType.values())
                .filter(type -> !getWillTypes().contains(type))
                .findFirst()
                .orElse(EnumDemonWillType.DEFAULT));
        willTypeGroup.setOnAdd(type -> {
            var updated = new ArrayList<>(getWillTypes());
            if (!updated.contains(type)) {
                updated.add(type);
            }
            setWillTypes(updated);
        });
        willTypeGroup.setOnRemove(type -> {
            var updated = new ArrayList<>(getWillTypes());
            updated.remove(type);
            setWillTypes(updated);
        });
        willTypeGroup.setOnUpdate(this::setWillTypes);
        father.addConfigurators(willTypeGroup);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = super.serializeNBT();
        var types = new ListTag();
        for (var type : getWillTypes()) {
            types.add(StringTag.valueOf(type.name()));
        }
        tag.put("willTypes", types);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        var types = new ArrayList<EnumDemonWillType>();
        if (nbt.contains("willTypes", Tag.TAG_LIST)) {
            var list = nbt.getList("willTypes", Tag.TAG_STRING);
            for (var i = 0; i < list.size(); i++) {
                types.add(BloodMagicWill.parseType(list.getString(i)));
            }
        }
        if (types.isEmpty() && nbt.contains("willType", Tag.TAG_STRING)) {
            types.add(BloodMagicWill.parseType(nbt.getString("willType")));
        }
        setWillTypes(types);
    }

    @Override
    public ITrait createTrait(MBDMachine machine) {
        return new BloodMagicWillTrait(machine, this);
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(BloodMagicItems.DEMON_WILL_GAUGE.get());
    }

    @Override
    public boolean allowMultiple() {
        return false;
    }

    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var text = new TextTextureWidget(0, 0, 100, 10,
                LocalizationUtils.format("recipe.capability.bloodmagic_will.will", 0,
                        BloodMagicWillRecipeCapability.getTypeName(EnumDemonWillType.DEFAULT)))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(uiPrefixName());
        ui.addWidget(text);
    }

    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof BloodMagicWillTrait willTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), TextTextureWidget.class, text -> {
                text.setText(() -> {
                    var world = trait.getMachine().getLevel();
                    var pos = trait.getMachine().getPos();
                    var definition = willTrait.getDefinition();
                    var amount = 0d;
                    for (var chunkPos : definition.getChunkSamplePositions(pos)) {
                        if (!world.hasChunkAt(chunkPos)) continue;
                        for (var type : definition.getWillTypes()) {
                            amount += WorldDemonWillHandler.getCurrentWill(world, chunkPos, type);
                        }
                    }
                    return net.minecraft.network.chat.Component.translatable("recipe.capability.bloodmagic_will.will",
                            FormattingUtil.formatNumbers(amount), definition.getWillTypesName());
                });
            });
        }
    }
}
