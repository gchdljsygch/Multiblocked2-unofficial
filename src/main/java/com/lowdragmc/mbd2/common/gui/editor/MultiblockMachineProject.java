package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateBlocks;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.BlockPlaceholder;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockAreaPanel;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockPatternPanel;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.*;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import com.mojang.datafixers.util.Either;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.*;

@Getter
@LDLRegister(name = "mb", group = "editor.machine")
@NoArgsConstructor
public class MultiblockMachineProject extends MachineProject {
    protected BlockPlaceholder[][][] blockPlaceholders;
    protected Direction.Axis layerAxis = Direction.Axis.Y;
    protected int[][] aisleRepetitions;
    protected PredicateResource predicateResource;
    protected List<MultiblockShapeInfo> multiblockShapeInfos = new ArrayList<>();
    protected List<PatternInfo> multiblockPatterns = new ArrayList<>();
    protected int selectedPatternIndex;

    protected record PatternInfo(BlockPlaceholder[][][] blockPlaceholders,
                                 Direction.Axis layerAxis,
                                 int[][] aisleRepetitions,
                                 List<MultiblockShapeInfo> shapeInfos) {
    }

    public MultiblockMachineProject(Resources resources, MultiblockMachineDefinition definition, WidgetGroup ui) {
        super(resources, definition, ui);
        this.blockPlaceholders = new BlockPlaceholder[1][1][1];
        if (resources.resources.get(PredicateResource.RESOURCE_NAME) instanceof PredicateResource resource) {
            this.predicateResource = resource;
            this.blockPlaceholders[0][0][0] = BlockPlaceholder.controller(predicateResource);
            setBlockPlaceholders(blockPlaceholders);
        }
    }

    @Override
    protected Map<String, Resource<?>> createResources() {
        var resources = super.createResources();
        // predicate
        var predicate = new PredicateResource();
        resources.put(PredicateResource.RESOURCE_NAME, predicate);
        return resources;
    }

    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) super.getDefinition();
    }

    protected MultiblockMachineDefinition createDefinition() {
        // use vanilla furnace model as an example
        var builder = MultiblockMachineDefinition.builder();
        builder.id(MBD2.id("new_machine"))
                .rootState(StateMachine.createMultiblockDefault(MachineState::builder, FURNACE_RENDERER));
        return builder.build();
    }

    public void setLayerAxis(Direction.Axis layerAxis) {
        this.layerAxis = layerAxis;
        var aisleLength = switch (layerAxis) {
            case X -> blockPlaceholders.length;
            case Y -> blockPlaceholders[0].length;
            case Z -> blockPlaceholders[0][0].length;
        };
        aisleRepetitions = new int[aisleLength][2];
        for (int[] aisleRepetition : aisleRepetitions) {
            aisleRepetition[0] = 1;
            aisleRepetition[1] = 1;
        }
        syncCurrentPattern();
    }

    public void setBlockPlaceholders(BlockPlaceholder[][][] blockPlaceholders) {
        this.blockPlaceholders = blockPlaceholders;
        setLayerAxis(this.layerAxis);
    }

    public int getPatternCount() {
        syncCurrentPattern();
        return multiblockPatterns.size();
    }

    public int getSelectedPatternIndex() {
        syncCurrentPattern();
        return selectedPatternIndex;
    }

    public void selectPattern(int index) {
        syncCurrentPattern();
        if (multiblockPatterns.isEmpty()) return;
        selectedPatternIndex = Math.max(0, Math.min(index, multiblockPatterns.size() - 1));
        applyPattern(multiblockPatterns.get(selectedPatternIndex));
    }

    public void addPattern(boolean copyCurrent) {
        syncCurrentPattern();
        PatternInfo pattern = copyCurrent && !multiblockPatterns.isEmpty() ?
                copyPattern(multiblockPatterns.get(selectedPatternIndex)) :
                createDefaultPattern();
        multiblockPatterns.add(pattern);
        selectedPatternIndex = multiblockPatterns.size() - 1;
        applyPattern(pattern);
    }

    public void removeCurrentPattern() {
        syncCurrentPattern();
        if (multiblockPatterns.size() <= 1) return;
        multiblockPatterns.remove(selectedPatternIndex);
        selectedPatternIndex = Math.min(selectedPatternIndex, multiblockPatterns.size() - 1);
        applyPattern(multiblockPatterns.get(selectedPatternIndex));
    }

    private void syncCurrentPattern() {
        if (blockPlaceholders == null || aisleRepetitions == null || multiblockShapeInfos == null) return;
        if (multiblockPatterns.isEmpty()) {
            selectedPatternIndex = 0;
            multiblockPatterns.add(new PatternInfo(blockPlaceholders, layerAxis, aisleRepetitions, multiblockShapeInfos));
            return;
        }
        selectedPatternIndex = Math.max(0, Math.min(selectedPatternIndex, multiblockPatterns.size() - 1));
        multiblockPatterns.set(selectedPatternIndex, new PatternInfo(blockPlaceholders, layerAxis, aisleRepetitions, multiblockShapeInfos));
    }

    private void applyPattern(PatternInfo pattern) {
        this.blockPlaceholders = pattern.blockPlaceholders();
        this.layerAxis = pattern.layerAxis();
        this.aisleRepetitions = pattern.aisleRepetitions();
        this.multiblockShapeInfos = pattern.shapeInfos();
    }

    private PatternInfo createDefaultPattern() {
        var placeholders = new BlockPlaceholder[1][1][1];
        placeholders[0][0][0] = BlockPlaceholder.controller(predicateResource);
        return new PatternInfo(placeholders, Direction.Axis.Y, new int[][]{{1, 1}}, new ArrayList<>());
    }

    private PatternInfo copyPattern(PatternInfo pattern) {
        return new PatternInfo(
                deserializeBlockPlaceholders(serializeBlockPlaceholders(pattern.blockPlaceholders()), predicateResource),
                pattern.layerAxis(),
                Arrays.stream(pattern.aisleRepetitions()).map(int[]::clone).toArray(int[][]::new),
                pattern.shapeInfos().stream().map(shapeInfo -> MultiblockShapeInfo.loadFromTag(shapeInfo.serializeNBT())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    public static BlockPattern createBlockPattern(BlockPlaceholder[][][] blockPlaceholders,
                                                  Direction.Axis layerAxis,
                                                  int[][] aisleRepetitions,
                                                  MultiblockMachineDefinition definition) {
        return createBlockPattern(blockPlaceholders, layerAxis, aisleRepetitions, definition, false);
    }

    /**
     * Create a block pattern from block placeholders.
     * @param blockPlaceholders the block placeholders
     * @param layerAxis the layer axis
     * @param aisleRepetitions the aisle repetitions
     * @param definition the machine definition
     * @param shapeInfo whether to create shape info with controller predicate
     * @return the block pattern
     */
    public static BlockPattern createBlockPattern(BlockPlaceholder[][][] blockPlaceholders,
                                                  Direction.Axis layerAxis,
                                                  int[][] aisleRepetitions,
                                                  MultiblockMachineDefinition definition,
                                                  boolean shapeInfo) {
        var aisleLength = switch (layerAxis) {
            case X -> blockPlaceholders.length;
            case Y -> blockPlaceholders[0].length;
            case Z -> blockPlaceholders[0][0].length;
        };
        var aisleHeight = switch (layerAxis) {
            case X -> blockPlaceholders[0].length;
            case Y -> blockPlaceholders.length;
            case Z -> blockPlaceholders.length;
        };
        var rowWidth = switch (layerAxis) {
            case X -> blockPlaceholders[0][0].length;
            case Y -> blockPlaceholders[0][0].length;
            case Z -> blockPlaceholders[0].length;
        };
        var predicate = new TraceabilityPredicate[aisleLength][aisleHeight][rowWidth];
        BlockPlaceholder controller = null;
        var x = 0;
        var min = 0;
        var max = 0;
        var centerOffset = new int[5];
        for (BlockPlaceholder[][] xSlice : blockPlaceholders) {
            var y = 0;
            for (BlockPlaceholder[] ySlice : xSlice) {
                var z = 0;
                for (BlockPlaceholder placeholder : ySlice) {
                    var traceabilityPredicate = placeholder.getPredicates().stream()
                            .map(placeholder.getPredicateResource()::getResource)
                            .filter(Objects::nonNull)
                            .map(TraceabilityPredicate::new)
                            .reduce(TraceabilityPredicate::or)
                            .orElse(new TraceabilityPredicate());
                    if (placeholder.isController())  {
                        controller = placeholder;
                        if (Direction.Axis.X == layerAxis) {
                            centerOffset = new int[]{z, y, x, min, max};
                        } else if (Direction.Axis.Y == layerAxis) {
                            centerOffset = new int[]{z, x, y, min, max};
                        } else {
                            centerOffset = new int[]{y, x, z, min, max};
                        }
                        if (shapeInfo) {
                            traceabilityPredicate = new TraceabilityPredicate(new SimplePredicate(state ->
                                    state.getBlockState().getBlock() == MBDRegistries.FAKE_MACHINE().block(), () -> new BlockInfo[]{new ControllerBlockInfo()}));
                        } else {
                            if (definition.multiblockSettings().catalyst().isEnable() && definition.multiblockSettings().catalyst().getCandidates().isEnable()) {
                                for (var block : definition.multiblockSettings().catalyst().getCandidates().getValue()) {
                                    traceabilityPredicate = new TraceabilityPredicate(new PredicateBlocks(block)).or(traceabilityPredicate);
                                }
                            }
                            traceabilityPredicate = new TraceabilityPredicate(new PredicateBlocks(definition.block())).or(traceabilityPredicate);
                        }
                    }
                    if (Direction.Axis.X == layerAxis) {
                        predicate[x][y][z] = traceabilityPredicate;
                    } else if (Direction.Axis.Y == layerAxis) {
                        predicate[y][x][z] = traceabilityPredicate;
                    } else {
                        predicate[z][x][y] = traceabilityPredicate;
                    }
                    if (layerAxis == Direction.Axis.Z) {
                        min += aisleRepetitions[z][0];
                        max += aisleRepetitions[z][1];
                    }
                    z++;
                }
                if (layerAxis == Direction.Axis.Y) {
                    min += aisleRepetitions[y][0];
                    max += aisleRepetitions[y][1];
                } else if (layerAxis == Direction.Axis.Z) {
                    min = 0;
                    max = 0;
                }
                y++;
            }
            if (layerAxis == Direction.Axis.X) {
                min += aisleRepetitions[x][0];
                max += aisleRepetitions[x][1];
            } else if (layerAxis == Direction.Axis.Y){
                min = 0;
                max = 0;
            }
            x++;
        }
        var controllerFace = controller.getFacing().getAxis() == Direction.Axis.Y ? Direction.NORTH : controller.getFacing();
        var structureDir = new RelativeDirection[3];
        structureDir[0] = RelativeDirection.getSliceYDirection(layerAxis, controllerFace);
        structureDir[1] = RelativeDirection.getSliceXDirection(layerAxis, controllerFace);
        structureDir[2] = RelativeDirection.getAisleDirection(layerAxis, controllerFace);
        var pattern = new BlockPattern(predicate, structureDir, aisleRepetitions, centerOffset);
        pattern.mbd2$setBaseFacing(controllerFace);
        return pattern;
    }

    @Override
    public MultiblockMachineProject newEmptyProject() {
        return new MultiblockMachineProject(new Resources(createResources()), createDefinition(), createDefaultUI());
    }

    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "multiblock");
    }

    public CompoundTag serializeNBT() {
        var tag = super.serializeNBT();
        syncCurrentPattern();
        if (!multiblockPatterns.isEmpty()) {
            serializePattern(tag, multiblockPatterns.get(selectedPatternIndex));
            var patternList = new ListTag();
            for (var pattern : multiblockPatterns) {
                var patternTag = new CompoundTag();
                serializePattern(patternTag, pattern);
                patternList.add(patternTag);
            }
            tag.put("patterns", patternList);
            tag.putInt("selected_pattern", selectedPatternIndex);
        }
        return tag;
    }

    private void serializePattern(CompoundTag tag, PatternInfo pattern) {
        tag.put("placeholders", serializeBlockPlaceholders(pattern.blockPlaceholders()));
        tag.putString("layer_axis", pattern.layerAxis().name());
        tag.putIntArray("aisle_repetitions", Arrays.stream(pattern.aisleRepetitions()).flatMapToInt(Arrays::stream).toArray());
        var shapeInfoList = new ListTag();
        for (var shapeInfo : pattern.shapeInfos()) {
            shapeInfoList.add(shapeInfo.serializeNBT());
        }
        tag.put("shape_infos", shapeInfoList);
    }

    public static CompoundTag serializeBlockPlaceholders(BlockPlaceholder[][][] blockPlaceholders){
        var placeholders = new ArrayList<BlockPlaceholder>();
        var placeHolderMap = new HashMap<BlockPlaceholder, Integer>();
        var placeHolderIndex = new ArrayList<Integer>();
        for (BlockPlaceholder[][] blockPlaceholder : blockPlaceholders) {
            for (BlockPlaceholder[] value : blockPlaceholder) {
                for (BlockPlaceholder holder : value) {
                    if (holder != null) {
                        if (!placeHolderMap.containsKey(holder)) {
                            placeHolderMap.put(holder, placeholders.size());
                            placeholders.add(holder);
                        }
                        placeHolderIndex.add(placeHolderMap.get(holder));
                    } else {
                        placeHolderIndex.add(-1);
                    }
                }
            }
        }
        var placeHoldersTag = new CompoundTag();
        var placeHoldersListTag = new ListTag();
        for (BlockPlaceholder placeholder : placeholders) {
            placeHoldersListTag.add(placeholder.serializeNBT());
        }
        placeHoldersTag.put("holders", placeHoldersListTag);
        placeHoldersTag.putInt("x", blockPlaceholders.length);
        placeHoldersTag.putInt("y", blockPlaceholders[0].length);
        placeHoldersTag.putInt("z", blockPlaceholders[0][0].length);
        placeHoldersTag.putIntArray("pattern", placeHolderIndex.stream().mapToInt(i -> i).toArray());
        return placeHoldersTag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag);
        if (resources.resources.get(PredicateResource.RESOURCE_NAME) instanceof PredicateResource resource) {
            this.predicateResource = resource;
        }
        this.multiblockPatterns.clear();
        if (tag.contains("patterns", Tag.TAG_LIST)) {
            var patterns = tag.getList("patterns", Tag.TAG_COMPOUND);
            this.multiblockPatterns.addAll(patterns.stream()
                    .map(CompoundTag.class::cast)
                    .map(this::deserializePattern)
                    .toList());
        }
        if (this.multiblockPatterns.isEmpty()) {
            this.multiblockPatterns.add(deserializePattern(tag));
        }
        this.selectedPatternIndex = Math.max(0, Math.min(tag.getInt("selected_pattern"), this.multiblockPatterns.size() - 1));
        applyPattern(this.multiblockPatterns.get(selectedPatternIndex));
    }

    private PatternInfo deserializePattern(CompoundTag tag) {
        var placeHoldersTag = tag.getCompound("placeholders");
        var x = placeHoldersTag.getInt("x");
        var y = placeHoldersTag.getInt("y");
        var z = placeHoldersTag.getInt("z");
        var blockPlaceholders = deserializeBlockPlaceholders(placeHoldersTag, predicateResource);
        var layerAxis = Direction.Axis.valueOf(tag.getString("layer_axis"));
        var aisleLength = switch (layerAxis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        var aisleRepetitions = new int[aisleLength][2];
        var repetitions = tag.getIntArray("aisle_repetitions");
        for (int i = 0; i < aisleLength; i++) {
            aisleRepetitions[i][0] = repetitions[i * 2];
            aisleRepetitions[i][1] = repetitions[i * 2 + 1];
        }
        var shapeInfos = new ArrayList<MultiblockShapeInfo>();
        tag.getList("shape_infos", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast)
                .map(MultiblockShapeInfo::loadFromTag)
                .forEach(shapeInfos::add);
        return new PatternInfo(blockPlaceholders, layerAxis, aisleRepetitions, shapeInfos);
    }

    public static BlockPlaceholder[][][] deserializeBlockPlaceholders(CompoundTag placeHoldersTag, PredicateResource predicateResource) {
        var placeHoldersListTag = placeHoldersTag.getList("holders", Tag.TAG_COMPOUND);
        var x = placeHoldersTag.getInt("x");
        var y = placeHoldersTag.getInt("y");
        var z = placeHoldersTag.getInt("z");
        var pattern = placeHoldersTag.getIntArray("pattern");
        var blockPlaceholders = new BlockPlaceholder[x][y][z];
        for (int i = 0; i < pattern.length; i++) {
            var index = pattern[i];
            var holder = index == -1 ? BlockPlaceholder.create(predicateResource, Either.left("any")) : BlockPlaceholder.fromTag(predicateResource, placeHoldersListTag.getCompound(index));
            blockPlaceholders[i / (y * z)][(i / z) % y][i % z] = holder;
        }
        return blockPlaceholders;
    }

    @Override
    public void onLoad(Editor editor) {
        if (editor instanceof MachineEditor machineEditor) {
            super.onLoad(editor);
            var tabContainer = machineEditor.getTabPages();
            var multiblockPatternPanel = createMultiblockPatternPanel(machineEditor);
            var multiblockAreaPanel = createMultiblockAreaPanel(multiblockPatternPanel);
            tabContainer.addTab("editor.machine.multiblock_area", multiblockAreaPanel, multiblockAreaPanel::onPanelSelected, multiblockAreaPanel:: onPanelDeselected);
            tabContainer.addTab("editor.machine.multiblock_pattern", multiblockPatternPanel, multiblockPatternPanel::onPanelSelected, multiblockPatternPanel::onPanelDeselected);
        }
    }

    public MultiblockPatternPanel createMultiblockPatternPanel(MachineEditor editor) {
        return new MultiblockPatternPanel(editor, this);
    }

    public MultiblockAreaPanel createMultiblockAreaPanel(MultiblockPatternPanel multiblockPatternPanel) {
        return new MultiblockAreaPanel(this, multiblockPatternPanel);
    }

}
