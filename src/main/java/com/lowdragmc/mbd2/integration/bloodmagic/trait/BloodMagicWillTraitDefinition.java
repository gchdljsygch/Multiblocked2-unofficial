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

/**
 * Trait definition for draining and filling ambient Blood Magic demon will.
 *
 * <p>Configuration controls accepted will types, the sampled chunk radius, and input/output
 * throttles. A radius of zero samples the machine's own chunk; larger radii sample chunk centers
 * sorted from nearest to farthest.</p>
 */
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

    /**
     * Returns the accepted will type list, always containing at least default will.
     */
    public List<EnumDemonWillType> getWillTypes() {
        if (willTypes.isEmpty()) {
            willTypes.add(EnumDemonWillType.DEFAULT);
        }
        return willTypes;
    }

    /**
     * Replaces the accepted will type list, removing nulls and duplicates.
     *
     * @param types new accepted types
     */
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

    /**
     * Checks whether this trait may handle a recipe payload of the given will type.
     */
    public boolean acceptsType(EnumDemonWillType type) {
        return getWillTypes().contains(type);
    }

    /**
     * Returns the accepted will types as a comma-separated display string.
     */
    public String getWillTypesName() {
        return getWillTypes().stream()
                .map(BloodMagicWillRecipeCapability::getTypeName)
                .reduce((left, right) -> left + ", " + right)
                .orElseGet(() -> BloodMagicWillRecipeCapability.getTypeName(EnumDemonWillType.DEFAULT));
    }

    /**
     * Computes chunk-center positions sampled for ambient demon will operations.
     *
     * @param origin machine position
     * @return loaded-chunk candidates are checked later; positions are sorted nearest first
     */
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

    /**
     * Builds base numeric configurators plus the accepted will-type array editor.
     */
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

    /**
     * Serializes accepted will types alongside base trait configuration.
     */
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

    /**
     * Deserializes accepted will types, including the legacy single {@code willType} field.
     */
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

    /**
     * Creates the runtime ambient will handler trait.
     */
    @Override
    public ITrait createTrait(MBDMachine machine) {
        return new BloodMagicWillTrait(machine, this);
    }

    /**
     * Returns the Demon Will Gauge icon used in trait lists.
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(BloodMagicItems.DEMON_WILL_GAUGE.get());
    }

    /**
     * Ambient will handler configuration is intended to be unique per machine.
     */
    @Override
    public boolean allowMultiple() {
        return false;
    }

    /**
     * Builds the live ambient will summary text widget.
     */
    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var text = new TextTextureWidget(0, 0, 100, 10,
                LocalizationUtils.format("recipe.capability.bloodmagic_will.will", 0,
                        BloodMagicWillRecipeCapability.getTypeName(EnumDemonWillType.DEFAULT)))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(uiPrefixName());
        ui.addWidget(text);
    }

    /**
     * Binds the live summed ambient will value to the UI text widget.
     */
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
