package com.lowdragmc.mbd2.common.gui.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
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
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Getter
@LDLRegister(name = "mb", group = "editor.machine")
@NoArgsConstructor
public class MultiblockMachineProject extends MachineProject {
    private static final Gson PATTERN_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PATTERN_FILE_KEY = "file";
    private static final String PATTERN_FILES_KEY = "pattern_files";

    private transient File loadingFile;
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
        if (controller == null) {
            controller = findControllerFallback(blockPlaceholders);
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

    @Override
    public void saveProject(File file) {
        try {
            writeProjectFile(file, serializeNBT());
        } catch (IOException e) {
            MBD2.LOGGER.error("Failed to save multiblock project {}", file, e);
        }
    }

    @Nullable
    @Override
    public IProject loadProject(File file) {
        try {
            var tag = readProjectFile(file);
            if (tag != null) {
                var proj = new MultiblockMachineProject();
                proj.loadingFile = file;
                proj.deserializeNBT(tag);
                proj.loadingFile = null;
                return proj;
            }
        } catch (IOException e) {
            MBD2.LOGGER.error("Failed to load multiblock project {}", file, e);
        }
        return null;
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

    public static void writeProjectFile(File file, CompoundTag projectTag) throws IOException {
        File manifestFile = projectManifestFile(file);
        var splitTag = splitPatterns(manifestFile, projectTag);
        File parent = manifestFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Failed to create project directory: " + parent);
        }
        NbtIo.write(splitTag, manifestFile);
        if (isFlatProjectFile(file) && !file.equals(manifestFile) && file.isFile()) {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Nullable
    public static CompoundTag readProjectFile(File file) throws IOException {
        var tag = NbtIo.read(existingProjectFile(file));
        if (tag != null) {
            expandPatternReferences(existingProjectFile(file), tag);
        }
        return tag;
    }

    public static File existingProjectFile(File file) {
        File manifestFile = projectManifestFile(file);
        if (manifestFile.isFile()) {
            return manifestFile;
        }
        return file;
    }

    public static File projectManifestFile(File file) {
        if (isManifestPath(file)) {
            return file;
        }
        File directory = projectDirectory(file);
        return new File(directory, directory.getName() + ".mb");
    }

    private static File projectDirectory(File file) {
        if (file.isDirectory()) {
            return file;
        }
        File parent = file.getParentFile();
        String name = stripExtension(file.getName());
        return parent == null ? new File(name) : new File(parent, name);
    }

    private static boolean isManifestPath(File file) {
        File parent = file.getParentFile();
        return parent != null && file.getName().endsWith(".mb") && stripExtension(file.getName()).equals(parent.getName());
    }

    private static boolean isFlatProjectFile(File file) {
        return file.getName().endsWith(".mb") && !isManifestPath(file);
    }

    public static int appendPattern(CompoundTag projectTag, CompoundTag newPattern, boolean existingFile, File projectFile) throws IOException {
        ListTag patterns = new ListTag();
        if (projectTag.contains("patterns", Tag.TAG_LIST)) {
            patterns.addAll(projectTag.getList("patterns", Tag.TAG_COMPOUND));
        } else if (existingFile && projectTag.contains("placeholders", Tag.TAG_COMPOUND)) {
            patterns.add(copyTopLevelPattern(projectTag));
        }
        patterns.add(newPattern);
        projectTag.put("patterns", patterns);
        int index = patterns.size() - 1;
        projectTag.putInt("selected_pattern", index);
        mirrorPatternToTopLevel(projectTag, newPattern);
        return index;
    }

    private static CompoundTag splitPatterns(File projectFile, CompoundTag projectTag) throws IOException {
        var tag = projectTag.copy();
        ListTag patternRefs = new ListTag();
        if (tag.contains("patterns", Tag.TAG_LIST)) {
            var patterns = tag.getList("patterns", Tag.TAG_COMPOUND);
            for (int i = 0; i < patterns.size(); i++) {
                var pattern = patterns.getCompound(i);
                if (isPatternReference(pattern)) {
                    patternRefs.add(pattern.copy());
                    continue;
                }
                var fileName = patternFileName(projectFile, i, patterns.size());
                writePatternFile(patternFile(projectFile, fileName), pattern);
                var ref = new CompoundTag();
                ref.putString(PATTERN_FILE_KEY, fileName);
                patternRefs.add(ref);
            }
        } else if (tag.contains("placeholders", Tag.TAG_COMPOUND)) {
            var pattern = copyTopLevelPattern(tag);
            var fileName = patternFileName(projectFile, 0, 1);
            writePatternFile(patternFile(projectFile, fileName), pattern);
            var ref = new CompoundTag();
            ref.putString(PATTERN_FILE_KEY, fileName);
            patternRefs.add(ref);
        }
        if (!patternRefs.isEmpty()) {
            tag.put("patterns", patternRefs);
            tag.remove(PATTERN_FILES_KEY);
            removePatternValues(tag);
        }
        return tag;
    }

    public static void expandPatternReferences(File projectFile, CompoundTag projectTag) throws IOException {
        String key = getPatternReferenceKey(projectTag);
        if (key == null) {
            return;
        }
        if (!projectTag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        var patterns = projectTag.getList(key, Tag.TAG_COMPOUND);
        var expandedPatterns = new ListTag();
        boolean expanded = false;
        for (int i = 0; i < patterns.size(); i++) {
            var pattern = patterns.getCompound(i);
            if (isPatternReference(pattern)) {
                expandedPatterns.add(readPatternFile(patternFile(projectFile, pattern.getString(PATTERN_FILE_KEY))));
                expanded = true;
            } else {
                expandedPatterns.add(pattern.copy());
            }
        }
        if (expanded) {
            projectTag.put("patterns", expandedPatterns);
            if (!expandedPatterns.isEmpty()) {
                var selected = Math.max(0, Math.min(projectTag.getInt("selected_pattern"), expandedPatterns.size() - 1));
                mirrorPatternToTopLevel(projectTag, expandedPatterns.getCompound(selected));
            }
        }
    }

    public static void expandPatternReferences(Class<?> source, String projectFile, CompoundTag projectTag) throws IOException {
        String key = getPatternReferenceKey(projectTag);
        if (key == null) {
            return;
        }
        if (!projectTag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        var patterns = projectTag.getList(key, Tag.TAG_COMPOUND);
        var expandedPatterns = new ListTag();
        boolean expanded = false;
        for (int i = 0; i < patterns.size(); i++) {
            var pattern = patterns.getCompound(i);
            if (isPatternReference(pattern)) {
                expandedPatterns.add(readPatternResource(source, projectFile, pattern.getString(PATTERN_FILE_KEY)));
                expanded = true;
            } else {
                expandedPatterns.add(pattern.copy());
            }
        }
        if (expanded) {
            projectTag.put("patterns", expandedPatterns);
            if (!expandedPatterns.isEmpty()) {
                var selected = Math.max(0, Math.min(projectTag.getInt("selected_pattern"), expandedPatterns.size() - 1));
                mirrorPatternToTopLevel(projectTag, expandedPatterns.getCompound(selected));
            }
        }
    }

    @Nullable
    private static String getPatternReferenceKey(CompoundTag projectTag) {
        if (projectTag.contains(PATTERN_FILES_KEY, Tag.TAG_LIST)) {
            return PATTERN_FILES_KEY;
        }
        if (projectTag.contains("patterns", Tag.TAG_LIST)) {
            var patterns = projectTag.getList("patterns", Tag.TAG_COMPOUND);
            if (patterns.isEmpty()) {
                return null;
            }
            return patterns.stream().allMatch(CompoundTag.class::isInstance) &&
                    patterns.stream().map(CompoundTag.class::cast).allMatch(MultiblockMachineProject::isPatternReference) ?
                    "patterns" : null;
        }
        return projectTag.contains(PATTERN_FILES_KEY, Tag.TAG_LIST) ? PATTERN_FILES_KEY : null;
    }

    private static boolean isPatternReference(CompoundTag tag) {
        return tag.contains(PATTERN_FILE_KEY, Tag.TAG_STRING) && !tag.contains("placeholders", Tag.TAG_COMPOUND);
    }

    private static void writePatternFile(File file, CompoundTag patternTag) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Failed to create pattern directory: " + parent);
        }
        try (var writer = new FileWriter(file)) {
            JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, patternTag);
            compressPatternRows(json);
            PATTERN_GSON.toJson(json, writer);
        }
    }

    private static CompoundTag readPatternFile(File file) throws IOException {
        try (var reader = new FileReader(file)) {
            return readPatternJson(reader);
        }
    }

    private static CompoundTag readPatternResource(Class<?> source, String projectFile, String fileName) throws IOException {
        String resourcePath = resourcePatternPath(projectFile, fileName);
        try (InputStream inputStream = source.getResourceAsStream("/assets/" + resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing multiblock pattern json resource: " + resourcePath);
            }
            try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return readPatternJson(reader);
            }
        }
    }

    private static CompoundTag readPatternJson(java.io.Reader reader) throws IOException {
        JsonElement json = JsonParser.parseReader(reader);
        expandCompressedPatternRows(json);
        Tag tag = jsonToTag(json);
        if (!(tag instanceof CompoundTag compoundTag)) {
            throw new IOException("Multiblock pattern json root must be an object");
        }
        return compoundTag;
    }

    private static void compressPatternRows(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject placeholders = json.getAsJsonObject().getAsJsonObject("placeholders");
        if (placeholders == null || !placeholders.has("pattern")) {
            return;
        }
        JsonArray pattern = placeholders.getAsJsonArray("pattern");
        for (JsonElement xElement : pattern) {
            if (!xElement.isJsonArray()) {
                continue;
            }
            JsonArray xTag = xElement.getAsJsonArray();
            for (int y = 0; y < xTag.size(); y++) {
                JsonElement rowElement = xTag.get(y);
                if (!rowElement.isJsonArray()) {
                    continue;
                }
                JsonArray row = rowElement.getAsJsonArray();
                if (row.isEmpty() || !row.asList().stream().allMatch(MultiblockMachineProject::isJsonInteger)) {
                    continue;
                }
                int value = row.get(0).getAsInt();
                boolean same = true;
                for (int z = 1; z < row.size(); z++) {
                    if (row.get(z).getAsInt() != value) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    xTag.set(y, new JsonPrimitive(value));
                }
            }
        }
    }

    private static void expandCompressedPatternRows(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject placeholders = json.getAsJsonObject().getAsJsonObject("placeholders");
        if (placeholders == null || !placeholders.has("pattern")) {
            return;
        }
        int zSize = placeholders.has("z") ? placeholders.get("z").getAsInt() : 0;
        JsonArray pattern = placeholders.getAsJsonArray("pattern");
        for (JsonElement xElement : pattern) {
            if (!xElement.isJsonArray()) {
                continue;
            }
            JsonArray xTag = xElement.getAsJsonArray();
            for (int y = 0; y < xTag.size(); y++) {
                JsonElement rowElement = xTag.get(y);
                if (!isJsonInteger(rowElement)) {
                    continue;
                }
                JsonArray row = new JsonArray();
                int value = rowElement.getAsInt();
                for (int z = 0; z < zSize; z++) {
                    row.add(value);
                }
                xTag.set(y, row);
            }
        }
    }

    private static Tag jsonToTag(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return StringTag.valueOf("");
        }
        if (json.isJsonObject()) {
            var tag = new CompoundTag();
            JsonObject object = json.getAsJsonObject();
            for (var entry : object.entrySet()) {
                tag.put(entry.getKey(), jsonToTag(entry.getValue()));
            }
            return tag;
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.isEmpty()) {
                return new ListTag();
            }
            if (array.asList().stream().allMatch(MultiblockMachineProject::isJsonInteger)) {
                return new IntArrayTag(array.asList().stream().mapToInt(JsonElement::getAsInt).toArray());
            }
            var tag = new ListTag();
            for (JsonElement element : array) {
                tag.add(jsonToTag(element));
            }
            return tag;
        }
        var primitive = json.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return IntTag.valueOf(primitive.getAsBoolean() ? 1 : 0);
        }
        if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            double doubleValue = number.doubleValue();
            if (doubleValue == Math.rint(doubleValue)) {
                return IntTag.valueOf(number.intValue());
            }
            float floatValue = number.floatValue();
            return (double) floatValue == doubleValue ? FloatTag.valueOf(floatValue) : DoubleTag.valueOf(doubleValue);
        }
        return StringTag.valueOf(primitive.getAsString());
    }

    private static boolean isJsonInteger(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        double value = element.getAsDouble();
        return value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static String resourcePatternPath(String projectFile, String fileName) {
        String normalized = projectFile.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? fileName : normalized.substring(0, slash + 1) + fileName;
    }

    private static File patternFile(File projectFile, String fileName) {
        return new File(patternDirectory(projectFile), fileName);
    }

    private static File patternDirectory(File projectFile) {
        File parent = projectFile.getParentFile();
        return parent == null ? new File(".") : parent;
    }

    private static String patternFileName(File projectFile, int index, int count) {
        var baseName = sanitizeFileName(stripExtension(projectFile.getName()));
        if (count <= 1) {
            return baseName + ".json";
        }
        return baseName + "_" + (index + 1) + ".json";
    }

    private static String sanitizeFileName(String name) {
        var sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return sanitized.isBlank() ? "multiblock" : sanitized;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static void removePatternValues(CompoundTag tag) {
        tag.remove("placeholders");
        tag.remove("layer_axis");
        tag.remove("aisle_repetitions");
        tag.remove("shape_infos");
    }

    public static CompoundTag serializeBlockPlaceholders(BlockPlaceholder[][][] blockPlaceholders){
        var placeholders = new ArrayList<BlockPlaceholder>();
        var placeHolderMap = new HashMap<BlockPlaceholder, Integer>();
        var placeHolderIndex = new ArrayList<Integer>();
        var predicates = new ArrayList<Either<String, File>>();
        var predicateMap = new HashMap<Either<String, File>, Integer>();
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
            placeHoldersListTag.add(serializeIndexedHolder(placeholder, predicates, predicateMap));
        }
        placeHoldersTag.put("holders", placeHoldersListTag);
        var predicatesTag = new ListTag();
        for (var predicate : predicates) {
            predicatesTag.add(serializePredicateReference(predicate));
        }
        placeHoldersTag.put("predicates", predicatesTag);
        placeHoldersTag.putInt("x", blockPlaceholders.length);
        placeHoldersTag.putInt("y", blockPlaceholders[0].length);
        placeHoldersTag.putInt("z", blockPlaceholders[0][0].length);
        placeHoldersTag.put("pattern", serializePlaceholderPattern(placeHolderIndex, blockPlaceholders.length, blockPlaceholders[0].length, blockPlaceholders[0][0].length));
        return placeHoldersTag;
    }

    private static CompoundTag serializeIndexedHolder(BlockPlaceholder placeholder,
                                                      List<Either<String, File>> predicates,
                                                      Map<Either<String, File>, Integer> predicateMap) {
        var tag = new CompoundTag();
        tag.putIntArray("predicates", placeholder.getPredicates().stream()
                .mapToInt(predicate -> predicateMap.computeIfAbsent(predicate, key -> {
                    predicates.add(key);
                    return predicates.size() - 1;
                }))
                .toArray());
        tag.putBoolean("isController", placeholder.isController());
        tag.putInt("facing", placeholder.getFacing().get3DDataValue());
        return tag;
    }

    private static CompoundTag serializePredicateReference(Either<String, File> predicate) {
        return predicate.map(l -> {
            var tag = new CompoundTag();
            tag.putString("key", l);
            tag.putString("type", "builtin");
            return tag;
        }, r -> {
            var tag = new CompoundTag();
            tag.putString("key", r.getPath());
            tag.putString("type", "project");
            return tag;
        });
    }

    private static BlockPlaceholder findControllerFallback(BlockPlaceholder[][][] blockPlaceholders) {
        for (BlockPlaceholder[][] xSlice : blockPlaceholders) {
            for (BlockPlaceholder[] ySlice : xSlice) {
                for (BlockPlaceholder placeholder : ySlice) {
                    if (placeholder != null) {
                        return placeholder.setController(true);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Multiblock pattern has no controller placeholder");
    }

    private static ListTag serializePlaceholderPattern(List<Integer> placeHolderIndex, int xSize, int ySize, int zSize) {
        var patternTag = new ListTag();
        var index = 0;
        for (int x = 0; x < xSize; x++) {
            var xTag = new ListTag();
            for (int y = 0; y < ySize; y++) {
                var row = new int[zSize];
                for (int z = 0; z < zSize; z++) {
                    row[z] = placeHolderIndex.get(index++);
                }
                xTag.add(new IntArrayTag(row));
            }
            patternTag.add(xTag);
        }
        return patternTag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (loadingFile != null) {
            try {
                expandPatternReferences(loadingFile, tag);
            } catch (IOException e) {
                MBD2.LOGGER.error("Failed to load multiblock pattern json for {}", loadingFile, e);
            }
        }
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
        if (isPatternReference(tag)) {
            throw new IllegalArgumentException("Unresolved multiblock pattern reference: " + tag.getString(PATTERN_FILE_KEY));
        }
        var placeHoldersTag = tag.getCompound("placeholders");
        var x = placeHoldersTag.getInt("x");
        var y = placeHoldersTag.getInt("y");
        var z = placeHoldersTag.getInt("z");
        var blockPlaceholders = deserializeBlockPlaceholders(placeHoldersTag, predicateResource);
        var layerAxisName = tag.getString("layer_axis");
        var layerAxis = layerAxisName.isBlank() ? Direction.Axis.Y : Direction.Axis.valueOf(layerAxisName);
        var aisleLength = switch (layerAxis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        var aisleRepetitions = new int[aisleLength][2];
        var repetitions = getIntArrayCompat(tag, "aisle_repetitions");
        for (int i = 0; i < aisleLength; i++) {
            aisleRepetitions[i][0] = i * 2 < repetitions.length ? repetitions[i * 2] : 1;
            aisleRepetitions[i][1] = i * 2 + 1 < repetitions.length ? repetitions[i * 2 + 1] : aisleRepetitions[i][0];
        }
        var shapeInfos = new ArrayList<MultiblockShapeInfo>();
        tag.getList("shape_infos", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast)
                .map(MultiblockShapeInfo::loadFromTag)
                .forEach(shapeInfos::add);
        return new PatternInfo(blockPlaceholders, layerAxis, aisleRepetitions, shapeInfos);
    }

    public static CompoundTag copyTopLevelPattern(CompoundTag projectTag) {
        CompoundTag pattern = new CompoundTag();
        copyPatternValue(projectTag, pattern, "placeholders");
        copyPatternValue(projectTag, pattern, "layer_axis");
        copyPatternValue(projectTag, pattern, "aisle_repetitions");
        copyPatternValue(projectTag, pattern, "shape_infos");
        return pattern;
    }

    public static void mirrorPatternToTopLevel(CompoundTag projectTag, CompoundTag pattern) {
        copyPatternValue(pattern, projectTag, "placeholders");
        copyPatternValue(pattern, projectTag, "layer_axis");
        copyPatternValue(pattern, projectTag, "aisle_repetitions");
        copyPatternValue(pattern, projectTag, "shape_infos");
    }

    private static void copyPatternValue(CompoundTag from, CompoundTag to, String key) {
        if (from.contains(key)) {
            to.put(key, from.get(key).copy());
        }
    }

    public static BlockPlaceholder[][][] deserializeBlockPlaceholders(CompoundTag placeHoldersTag, PredicateResource predicateResource) {
        var placeHoldersListTag = placeHoldersTag.getList("holders", Tag.TAG_COMPOUND);
        var predicateRefs = deserializePredicateReferences(placeHoldersTag);
        var x = placeHoldersTag.getInt("x");
        var y = placeHoldersTag.getInt("y");
        var z = placeHoldersTag.getInt("z");
        var pattern = deserializePlaceholderPattern(placeHoldersTag, x, y, z);
        var blockPlaceholders = new BlockPlaceholder[x][y][z];
        for (int i = 0; i < pattern.length; i++) {
            var index = pattern[i];
            var holder = index == -1 ? BlockPlaceholder.create(predicateResource, Either.left("any")) :
                    deserializeHolder(predicateResource, placeHoldersListTag.getCompound(index), predicateRefs);
            blockPlaceholders[i / (y * z)][(i / z) % y][i % z] = holder;
        }
        return blockPlaceholders;
    }

    private static List<Either<String, File>> deserializePredicateReferences(CompoundTag placeHoldersTag) {
        var predicates = new ArrayList<Either<String, File>>();
        if (!placeHoldersTag.contains("predicates", Tag.TAG_LIST)) {
            return predicates;
        }
        var predicatesTag = placeHoldersTag.getList("predicates", Tag.TAG_COMPOUND);
        for (var tag : predicatesTag) {
            var compoundTag = (CompoundTag) tag;
            predicates.add(deserializePredicateReference(compoundTag));
        }
        return predicates;
    }

    private static Either<String, File> deserializePredicateReference(CompoundTag tag) {
        var key = tag.getString("key");
        var type = tag.getString("type");
        if ("project".equals(type)) {
            return Either.right(new File(key));
        }
        return Either.left(key);
    }

    private static BlockPlaceholder deserializeHolder(PredicateResource predicateResource,
                                                      CompoundTag tag,
                                                      List<Either<String, File>> predicateRefs) {
        if (!predicateRefs.isEmpty() && tag.contains("predicates")) {
            var holder = BlockPlaceholder.create(predicateResource);
            for (int predicateIndex : getIntArrayCompat(tag, "predicates")) {
                if (predicateIndex >= 0 && predicateIndex < predicateRefs.size()) {
                    holder.getPredicates().add(predicateRefs.get(predicateIndex));
                }
            }
            holder.setController(getBooleanCompat(tag, "isController"));
            holder.setFacing(Direction.from3DDataValue(tag.getInt("facing")));
            return holder;
        }
        return BlockPlaceholder.fromTag(predicateResource, tag);
    }

    private static int[] deserializePlaceholderPattern(CompoundTag placeHoldersTag, int xSize, int ySize, int zSize) {
        if (placeHoldersTag.contains("pattern", Tag.TAG_INT_ARRAY)) {
            return placeHoldersTag.getIntArray("pattern");
        }
        var patternTag = placeHoldersTag.getList("pattern", Tag.TAG_LIST);
        var pattern = new int[xSize * ySize * zSize];
        var index = 0;
        for (int x = 0; x < xSize; x++) {
            var xTag = patternTag.getList(x);
            for (int y = 0; y < ySize; y++) {
                var row = getIntArrayCompat(xTag.get(y));
                for (int z = 0; z < zSize; z++) {
                    pattern[index++] = z < row.length ? row[z] : -1;
                }
            }
        }
        return pattern;
    }

    public static int[] getIntArrayCompat(CompoundTag tag, String key) {
        return tag.contains(key) ? getIntArrayCompat(tag.get(key)) : new int[0];
    }

    public static int[] getIntArrayCompat(Tag tag) {
        if (tag instanceof IntArrayTag intArrayTag) {
            return intArrayTag.getAsIntArray();
        }
        if (tag instanceof ListTag listTag) {
            var values = new int[listTag.size()];
            for (int i = 0; i < listTag.size(); i++) {
                Tag entry = listTag.get(i);
                values[i] = entry instanceof NumericTag numericTag ? numericTag.getAsInt() : 0;
            }
            return values;
        }
        return new int[0];
    }

    public static boolean getBooleanCompat(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return false;
        }
        Tag value = tag.get(key);
        if (value instanceof NumericTag numericTag) {
            return numericTag.getAsInt() != 0;
        }
        return tag.getBoolean(key);
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
