package com.lowdragmc.mbd2.common.machine.definition;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.CombinedBlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.PredicateResource;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.*;
import com.lowdragmc.mbd2.common.trait.IUIProviderTrait;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject.createBlockPattern;

/**
 * Multiblock machine definition.
 * <br>
 * This is used to define a mbd machine's {@link MBDMultiblockMachine#getDefinition()} behaviours.
 */
@Getter
@Accessors(fluent = true)
@LDLRegister(name = "multiblock", group = "machine_definition")
public class MultiblockMachineDefinition extends MBDMachineDefinition {
    public static final Map<Block, Set<MultiblockMachineDefinition>> CATALYST_CANDIDATES = Collections.synchronizedMap(new HashMap<>());
    @FunctionalInterface
    public interface ConfigMultiblockSettingsFactory extends Supplier<ConfigMultiblockSettings> { }

    @Configurable(name = "config.definition.multiblock_settings", subConfigurable = true, tips = "config.definition.multiblock_settings.tooltip", collapse = false)
    protected ConfigMultiblockSettings multiblockSettings;

    // runtime
    protected ConfigMultiblockSettingsFactory multiblockSettingsFactory;

    private Function<MBDMultiblockMachine, BlockPattern> blockPatternFactory;
    private Function<MBDMultiblockMachine, BlockPattern[]> blockPatternsFactory;
    @Setter
    private Function<MultiblockMachineDefinition, MultiblockShapeInfo[]> shapeInfoFactory;

    public MultiblockMachineDefinition(ResourceLocation id,
                                       @Nullable MachineState rootState,
                                       @Nullable ConfigBlockProperties blockProperties,
                                       @Nullable ConfigItemProperties itemProperties,
                                       @Nullable ConfigMachineSettingsFactory machineSettingsFactory,
                                       @Nullable ConfigRecipeLogicSettings recipeLogicSettings,
                                       @Nullable ConfigMultiblockSettingsFactory multiblockSettingsFactory) {
        super(id, rootState, blockProperties, itemProperties, machineSettingsFactory, recipeLogicSettings, null);
        this.multiblockSettingsFactory = multiblockSettingsFactory == null ? () -> ConfigMultiblockSettings.builder().build() : multiblockSettingsFactory;
    }

    @Override
    public boolean allowPartSettings() {
        return false;
    }

    public static MultiblockMachineDefinition createDefault() {
        return new MultiblockMachineDefinition(
                MBD2.id("dummy"),
                StateMachine.createDefault(MachineState::builder),
                ConfigBlockProperties.builder().build(),
                ConfigItemProperties.builder().build(),
                () -> ConfigMachineSettings.builder().build(),
                ConfigRecipeLogicSettings.builder().build(),
                () -> ConfigMultiblockSettings.builder().build());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ConfigMachineEvents createMachineEvents() {
        return super.createMachineEvents().registerEventGroup("MachineEvent.Multiblock");
    }

    @Override
    public void loadFactory() {
        super.loadFactory();
        multiblockSettings = multiblockSettingsFactory.get();
    }

    @Override
    public MBDMultiblockMachine createMachine(IMachineBlockEntity blockEntity) {
        return new MBDMultiblockMachine(blockEntity, this);
    }

    @Override
    public MultiblockMachineDefinition loadProductiveTag(@Nullable File file, CompoundTag projectTag, Deque<Runnable> postTask) {
        super.loadProductiveTag(file, projectTag, postTask);
        postTask.add(() -> {
            // load multiblock settings
            multiblockSettings.deserializeNBT(projectTag.getCompound("definition").getCompound("multiblockSettings"));
            // setup catalyst candidates
            if (multiblockSettings.catalyst().isEnable() && multiblockSettings.catalyst().getCandidates().isEnable()) {
                for (var block : multiblockSettings.catalyst().getCandidates().getValue()) {
                    CATALYST_CANDIDATES.computeIfAbsent(block, b -> new HashSet<>()).add(this);
                }
            }
            // setup block patterns
            var predicateResource = new PredicateResource();
            predicateResource.deserializeNBT(projectTag.getCompound("resources").getCompound(PredicateResource.RESOURCE_NAME));
            var patternEntries = loadPatternEntries(projectTag);
            var loadedPatterns = new ArrayList<LoadedPattern>();
            for (var patternTag : patternEntries) {
                loadedPatterns.add(loadPattern(patternTag, predicateResource));
            }
            var blockPatterns = loadedPatterns.stream().map(LoadedPattern::pattern).toArray(BlockPattern[]::new);
            blockPatternsFactory(controller -> blockPatterns);
            // setup shape info
            shapeInfoFactory(Util.memoize(definition -> {
                var shapeInfos = new ArrayList<MultiblockShapeInfo>();
                for (var pattern : loadedPatterns) {
                    appendPatternShapeInfos(shapeInfos, pattern.pattern());
                }
                return shapeInfos.toArray(new MultiblockShapeInfo[0]);
            }));
        });
        return this;
    }

    @Override
    protected void bindMachineUI(MBDMachine machine, WidgetGroup ui) {
        super.bindMachineUI(machine, ui);
        // proxy part ui
        if (machine instanceof MBDMultiblockMachine multiblock) {
            var prefix = "part:";
            var midTag = "@ui:";
            for (Widget widget : ui.getWidgetsById("part:.*?@ui:")) {
                var id = widget.getId();
                if (id.startsWith(prefix)) {
                    int atIndex = id.indexOf(midTag);
                    if (atIndex != -1) {
                        var traitName = id.substring(prefix.length(), atIndex);
                        var uiName = "ui:" + id.substring(atIndex + midTag.length());
                        for (IMultiPart part : multiblock.getParts()) {
                            if (part instanceof MBDMachine mbdMachine) {
                                var trait = mbdMachine.getTraitByName(traitName);
                                if (trait != null && trait.getDefinition() instanceof IUIProviderTrait provider && uiName.startsWith(provider.uiPrefixName())) {
                                    widget.setId(uiName);
                                    provider.initTraitUI(trait, ui);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public BlockPattern getPattern(MBDMultiblockMachine controller) {
        var patterns = getPatterns(controller);
        if (patterns.length == 0) return null;
        if (patterns.length == 1) return patterns[0];
        return new CombinedBlockPattern(patterns);
    }

    public BlockPattern[] getPatterns(MBDMultiblockMachine controller) {
        if (blockPatternsFactory != null) {
            var patterns = blockPatternsFactory.apply(controller);
            if (patterns != null) {
                return flattenPatterns(patterns);
            }
        }
        if (blockPatternFactory != null) {
            var pattern = blockPatternFactory.apply(controller);
            return flattenPatterns(pattern);
        }
        return new BlockPattern[0];
    }

    public MultiblockShapeInfo[] getPatternShapeInfos(@Nullable MBDMultiblockMachine controller) {
        var shapeInfos = new ArrayList<MultiblockShapeInfo>();
        for (var pattern : getPatterns(controller)) {
            appendPatternShapeInfos(shapeInfos, pattern);
        }
        return shapeInfos.toArray(new MultiblockShapeInfo[0]);
    }

    public MultiblockShapeInfo[] getPatternShapeInfos(@Nullable MBDMultiblockMachine controller, int patternIndex) {
        var patterns = getPatterns(controller);
        if (patternIndex < 0 || patternIndex >= patterns.length) return new MultiblockShapeInfo[0];
        var shapeInfos = new ArrayList<MultiblockShapeInfo>();
        appendPatternShapeInfos(shapeInfos, patterns[patternIndex]);
        return shapeInfos.toArray(new MultiblockShapeInfo[0]);
    }

    public int getFirstPatternShapeInfoIndex(@Nullable MBDMultiblockMachine controller, int patternIndex) {
        if (patternIndex <= 0) return 0;
        var patterns = getPatterns(controller);
        int index = 0;
        for (int i = 0; i < patternIndex && i < patterns.length; i++) {
            index += getShapeInfoCount(patterns[i]);
        }
        return index;
    }

    public void blockPatternFactory(Function<MBDMultiblockMachine, BlockPattern> blockPatternFactory) {
        this.blockPatternFactory = blockPatternFactory;
        this.blockPatternsFactory = controller -> {
            var pattern = blockPatternFactory.apply(controller);
            return pattern == null ? new BlockPattern[0] : new BlockPattern[]{pattern};
        };
    }

    public void blockPatternsFactory(Function<MBDMultiblockMachine, BlockPattern[]> blockPatternsFactory) {
        this.blockPatternsFactory = blockPatternsFactory;
        this.blockPatternFactory = controller -> {
            var patterns = blockPatternsFactory.apply(controller);
            if (patterns == null || patterns.length == 0) return null;
            if (patterns.length == 1) return patterns[0];
            return new CombinedBlockPattern(patterns);
        };
    }

    public void sortParts(List<IMultiPart> parts) {
    }

    private List<CompoundTag> loadPatternEntries(CompoundTag projectTag) {
        var entries = new ArrayList<CompoundTag>();
        if (projectTag.contains("patterns", Tag.TAG_LIST)) {
            ListTag list = projectTag.getList("patterns", Tag.TAG_COMPOUND);
            entries.addAll(list.stream().map(CompoundTag.class::cast).toList());
        }
        if (entries.isEmpty()) {
            entries.add(projectTag);
        }
        return entries;
    }

    private BlockPattern[] flattenPatterns(BlockPattern... patterns) {
        var result = new ArrayList<BlockPattern>();
        for (var pattern : patterns) {
            if (pattern instanceof CombinedBlockPattern combinedPattern) {
                Collections.addAll(result, combinedPattern.getPatterns());
            } else if (pattern != null) {
                result.add(pattern);
            }
        }
        return result.toArray(BlockPattern[]::new);
    }

    private LoadedPattern loadPattern(CompoundTag patternTag, PredicateResource predicateResource) {
        var placeholders = MultiblockMachineProject.deserializeBlockPlaceholders(patternTag.getCompound("placeholders"), predicateResource);
        var layerAxis = Direction.Axis.valueOf(patternTag.getString("layer_axis"));
        var aisleLength = switch (layerAxis) {
            case X -> placeholders.length;
            case Y -> placeholders[0].length;
            case Z -> placeholders[0][0].length;
        };
        var aisleRepetitions = new int[aisleLength][2];
        var repetitions = patternTag.getIntArray("aisle_repetitions");
        for (int i = 0; i < aisleLength; i++) {
            aisleRepetitions[i][0] = repetitions[i * 2];
            aisleRepetitions[i][1] = repetitions[i * 2 + 1];
        }
        return new LoadedPattern(patternTag, createBlockPattern(placeholders, layerAxis, aisleRepetitions, this), aisleRepetitions);
    }

    private void appendPatternShapeInfos(List<MultiblockShapeInfo> shapeInfos, BlockPattern pattern) {
        var repetition = Arrays.stream(pattern.aisleRepetitions).mapToInt(range -> range[0]).toArray();
        shapeInfos.add(new MultiblockShapeInfo(pattern.getPreview(repetition)));
        for (int layer = 0; layer < pattern.aisleRepetitions.length; layer++) {
            var range = pattern.aisleRepetitions[layer];
            for (int i = range[0] + 1; i <= range[1]; i++) {
                repetition[layer] = i;
                shapeInfos.add(new MultiblockShapeInfo(pattern.getPreview(repetition)));
                repetition[layer] = range[0];
            }
        }
    }

    private int getShapeInfoCount(BlockPattern pattern) {
        int count = 1;
        for (var range : pattern.aisleRepetitions) {
            count += Math.max(0, range[1] - range[0]);
        }
        return count;
    }

    private record LoadedPattern(CompoundTag tag, BlockPattern pattern, int[][] aisleRepetitions) {
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    public static class Builder extends MBDMachineDefinition.Builder {
        protected ConfigMultiblockSettingsFactory multiblockSettings;

        protected Builder() {
        }

        @Override
        public Builder setMaxParallel(int maxParallel) {
            super.setMaxParallel(maxParallel);
            return this;
        }

        @Override
        public Builder maxParallel(int maxParallel) {
            super.maxParallel(maxParallel);
            return this;
        }

        public MultiblockMachineDefinition build() {
            return new MultiblockMachineDefinition(id, rootState, blockProperties, itemProperties, machineSettings, recipeLogicSettings, multiblockSettings);
        }
    }
}
