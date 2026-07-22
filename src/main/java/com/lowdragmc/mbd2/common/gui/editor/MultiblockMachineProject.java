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
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Editor project for multiblock machine definitions and pattern authoring.
 *
 * <p>The project extends the base machine project with predicate resources, 3D block placeholders, layer-axis and
 * aisle-repetition settings, shape previews, and multiple selectable patterns. Project saving writes a manifest
 * {@code .mb} file and splits pattern data into adjacent JSON files for easier inspection and version control; loading
 * expands those pattern references back into NBT before deserialization.</p>
 */
@Getter
@LDLRegister(name = "mb", group = "editor.machine")
@NoArgsConstructor
public class MultiblockMachineProject extends MachineProject {
    private static final Gson PATTERN_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PATTERN_FILE_KEY = "file";
    private static final String PATTERN_FILES_KEY = "pattern_files";
    private static final String JSON_TYPE_TABLE_KEY = "__mbd2_nbt_types";
    private static final int MAX_PATTERN_AXIS_SIZE = 512;
    private static final long MAX_PATTERN_CELLS = 1_000_000L;
    private static final int MAX_PATTERN_REPETITION = BlockPattern.MAX_AISLE_REPETITION;

    private transient File loadingFile;
    protected BlockPlaceholder[][][] blockPlaceholders;
    protected Direction.Axis layerAxis = Direction.Axis.Y;
    protected int[][] aisleRepetitions;
    protected PredicateResource predicateResource;
    protected List<MultiblockShapeInfo> multiblockShapeInfos = new ArrayList<>();
    protected List<PatternInfo> multiblockPatterns = new ArrayList<>();
    protected int selectedPatternIndex;

    private record PendingFile(Path temporary, Path target) {
    }

    /**
     * Immutable snapshot of one editable multiblock pattern.
     *
     * @param blockPlaceholders 3D placeholder grid
     * @param layerAxis         axis used by the pattern editor for layer slicing
     * @param aisleRepetitions  min/max repetition pairs for each layer along {@code layerAxis}
     * @param shapeInfos        explicit preview shape variants
     */
    protected record PatternInfo(BlockPlaceholder[][][] blockPlaceholders,
                                 Direction.Axis layerAxis,
                                 int[][] aisleRepetitions,
                                 List<MultiblockShapeInfo> shapeInfos) {
    }

    /**
     * Creates a multiblock project with explicit resources, definition, and UI.
     *
     * @param resources  project resource map; must include {@link PredicateResource}
     * @param definition multiblock definition to edit
     * @param ui         configurable machine UI root
     */
    public MultiblockMachineProject(Resources resources, MultiblockMachineDefinition definition, WidgetGroup ui) {
        super(resources, definition, ui);
        this.blockPlaceholders = new BlockPlaceholder[1][1][1];
        if (resources.resources.get(PredicateResource.RESOURCE_NAME) instanceof PredicateResource resource) {
            this.predicateResource = resource;
            this.blockPlaceholders[0][0][0] = BlockPlaceholder.controller(predicateResource);
            setBlockPlaceholders(blockPlaceholders);
        }
    }

    /**
     * Creates the base machine resources plus the multiblock predicate resource.
     *
     * @return ordered resource map for multiblock projects
     */
    @Override
    protected Map<String, Resource<?>> createResources() {
        var resources = super.createResources();
        // predicate
        var predicate = new PredicateResource();
        resources.put(PredicateResource.RESOURCE_NAME, predicate);
        return resources;
    }

    /**
     * Returns this project's definition as a multiblock definition.
     *
     * @return multiblock machine definition
     */
    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) super.getDefinition();
    }

    /**
     * Creates the default multiblock definition for a new project.
     *
     * @return definition with a multiblock-capable default state
     */
    protected MultiblockMachineDefinition createDefinition() {
        // use vanilla furnace model as an example
        var builder = MultiblockMachineDefinition.builder();
        builder.id(MBD2.id("new_machine"))
                .rootState(StateMachine.createMultiblockDefault(MachineState::builder, FURNACE_RENDERER));
        return builder.build();
    }

    /**
     * Changes the axis used to slice the editable placeholder grid.
     *
     * <p>The aisle repetition array is reset to {@code [1,1]} pairs for every layer on the new axis and the current
     * pattern snapshot is updated.</p>
     *
     * @param layerAxis axis used by multiblock pattern layers
     */
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

    /**
     * Replaces the editable placeholder grid and recalculates layer repetitions for the current axis.
     *
     * @param blockPlaceholders non-empty 3D placeholder grid
     */
    public void setBlockPlaceholders(BlockPlaceholder[][][] blockPlaceholders) {
        this.blockPlaceholders = blockPlaceholders;
        setLayerAxis(this.layerAxis);
    }

    /**
     * Returns the number of patterns after synchronizing the active editable fields.
     *
     * @return pattern count
     */
    public int getPatternCount() {
        syncCurrentPattern();
        return multiblockPatterns.size();
    }

    /**
     * Returns the selected pattern index after synchronizing the active editable fields.
     *
     * @return selected pattern index clamped to the pattern list
     */
    public int getSelectedPatternIndex() {
        syncCurrentPattern();
        return selectedPatternIndex;
    }

    /**
     * Selects a pattern by index and applies its snapshot to the editable fields.
     *
     * @param index requested pattern index; clamped to the valid range
     */
    public void selectPattern(int index) {
        syncCurrentPattern();
        if (multiblockPatterns.isEmpty()) return;
        selectedPatternIndex = Math.max(0, Math.min(index, multiblockPatterns.size() - 1));
        applyPattern(multiblockPatterns.get(selectedPatternIndex));
    }

    /**
     * Adds a new pattern and selects it.
     *
     * @param copyCurrent when {@code true}, copies the selected pattern; otherwise creates a default controller-only pattern
     */
    public void addPattern(boolean copyCurrent) {
        syncCurrentPattern();
        PatternInfo pattern = copyCurrent && !multiblockPatterns.isEmpty() ?
                copyPattern(multiblockPatterns.get(selectedPatternIndex)) :
                createDefaultPattern();
        multiblockPatterns.add(pattern);
        selectedPatternIndex = multiblockPatterns.size() - 1;
        applyPattern(pattern);
    }

    /**
     * Removes the selected pattern when more than one pattern exists.
     */
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

    /**
     * Creates a runtime {@link BlockPattern} for normal structure matching.
     * <p>
     * This overload uses real controller matching rather than shape-preview fake controller data. Inputs must describe a
     * rectangular placeholder grid in editor x/y/z order, and {@code aisleRepetitions} must provide one min/max pair per
     * aisle along {@code layerAxis}. The method is pure with respect to project state but constructs new predicate
     * objects for the returned pattern.
     *
     * @param blockPlaceholders rectangular placeholder grid in editor x/y/z order
     * @param layerAxis         axis that determines aisle order
     * @param aisleRepetitions  min/max repetition pairs for each aisle; each bound should be at least {@code 1}
     * @param definition        multiblock definition whose controller and catalyst predicates are injected
     * @return runtime block pattern used by structure checks
     */
    public static BlockPattern createBlockPattern(BlockPlaceholder[][][] blockPlaceholders,
                                                  Direction.Axis layerAxis,
                                                  int[][] aisleRepetitions,
                                                  MultiblockMachineDefinition definition) {
        return createBlockPattern(blockPlaceholders, layerAxis, aisleRepetitions, definition, false);
    }

    /**
     * Creates a runtime {@link BlockPattern} from editor placeholders.
     *
     * <p>The method converts placeholder predicate references into traceability predicates, injects the controller
     * predicate, computes the structure-relative directions from the layer axis and controller facing, and stores the
     * base facing on the resulting pattern. If no placeholder is marked as controller, the first non-null placeholder is
     * promoted as a fallback.</p>
     *
     * @param blockPlaceholders placeholder grid in editor x/y/z order
     * @param layerAxis         axis that defines aisle order
     * @param aisleRepetitions  min/max repetition pairs for every aisle along {@code layerAxis}
     * @param definition        multiblock definition whose block and catalyst candidates are injected
     * @param shapeInfo         whether to create a fake-controller predicate for shape-preview generation
     * @return runtime block pattern
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
        if (aisleRepetitions.length != aisleLength) {
            throw new IllegalArgumentException("Aisle repetition count does not match the " + layerAxis + " axis length");
        }
        for (int[] range : aisleRepetitions) {
            if (range == null || range.length < 2) {
                throw new IllegalArgumentException("Aisle repetition range is missing");
            }
            BlockPattern.validateAisleRepetitionRange(range[0], range[1]);
        }

        BlockPlaceholder controller = null;
        int controllerX = -1;
        int controllerY = -1;
        int controllerZ = -1;
        for (int x = 0; x < blockPlaceholders.length; x++) {
            for (int y = 0; y < blockPlaceholders[x].length; y++) {
                for (int z = 0; z < blockPlaceholders[x][y].length; z++) {
                    var placeholder = blockPlaceholders[x][y][z];
                    if (placeholder != null && placeholder.isController()) {
                        if (controller != null) {
                            throw new IllegalArgumentException("Multiblock pattern contains more than one controller placeholder");
                        }
                        controller = placeholder;
                        controllerX = x;
                        controllerY = y;
                        controllerZ = z;
                    }
                }
            }
        }
        if (controller == null) {
            controller = findControllerFallback(blockPlaceholders);
            outer:
            for (int x = 0; x < blockPlaceholders.length; x++) {
                for (int y = 0; y < blockPlaceholders[x].length; y++) {
                    for (int z = 0; z < blockPlaceholders[x][y].length; z++) {
                        if (blockPlaceholders[x][y][z] == controller) {
                            controllerX = x;
                            controllerY = y;
                            controllerZ = z;
                            break outer;
                        }
                    }
                }
            }
        }
        int controllerAisle = switch (layerAxis) {
            case X -> controllerX;
            case Y -> controllerY;
            case Z -> controllerZ;
        };
        if (aisleRepetitions[controllerAisle][0] != 1 || aisleRepetitions[controllerAisle][1] != 1) {
            throw new IllegalArgumentException("The aisle containing the controller cannot be repeated");
        }
        int minBeforeController = 0;
        int maxBeforeController = 0;
        for (int aisle = 0; aisle < controllerAisle; aisle++) {
            minBeforeController = Math.addExact(minBeforeController, aisleRepetitions[aisle][0]);
            maxBeforeController = Math.addExact(maxBeforeController, aisleRepetitions[aisle][1]);
        }
        var centerOffset = switch (layerAxis) {
            case X -> new int[]{controllerZ, controllerY, controllerX, minBeforeController, maxBeforeController};
            case Y -> new int[]{controllerZ, controllerX, controllerY, minBeforeController, maxBeforeController};
            case Z -> new int[]{controllerY, controllerX, controllerZ, minBeforeController, maxBeforeController};
        };

        var predicate = new TraceabilityPredicate[aisleLength][aisleHeight][rowWidth];
        var x = 0;
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
                    if (placeholder == controller) {
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
                        traceabilityPredicate.setController();
                    }
                    if (Direction.Axis.X == layerAxis) {
                        predicate[x][y][z] = traceabilityPredicate;
                    } else if (Direction.Axis.Y == layerAxis) {
                        predicate[y][x][z] = traceabilityPredicate;
                    } else {
                        predicate[z][x][y] = traceabilityPredicate;
                    }
                    z++;
                }
                y++;
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

    /**
     * Creates a new empty multiblock project.
     *
     * @return initialized multiblock project with default resources, definition, UI, and controller placeholder
     */
    @Override
    public MultiblockMachineProject newEmptyProject() {
        return new MultiblockMachineProject(new Resources(createResources()), createDefinition(), createDefaultUI());
    }

    /**
     * Returns the workspace directory for multiblock projects.
     *
     * @param editor owning editor
     * @return {@code multiblock} subdirectory under the editor workspace
     */
    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "multiblock");
    }

    /**
     * Saves the project using the split-pattern manifest format and reloads the runtime definition when registered.
     *
     * @param file requested project file or directory
     */
    @Override
    public void saveProject(File file) {
        saveProjectChecked(file);
    }

    /**
     * Saves the manifest and all pattern files, reporting the actual result to the editor.
     */
    public boolean saveProjectChecked(File file) {
        try {
            writeProjectFile(file, serializeNBT());
            try {
                reloadRuntimeDefinition(file);
            } catch (IOException | RuntimeException e) {
                MBD2.LOGGER.warn("Saved multiblock project {}, but runtime reload failed", file, e);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            MBD2.LOGGER.error("Failed to save multiblock project {}", file, e);
            return false;
        }
    }

    private void reloadRuntimeDefinition(File file) throws IOException {
        if (!(MBDRegistries.MACHINE_DEFINITIONS.get(getDefinition().id()) instanceof MultiblockMachineDefinition definition)) {
            return;
        }
        var projectFile = existingProjectFile(file);
        var tag = NbtIo.read(projectFile);
        if (tag == null) {
            return;
        }
        synchronized (MultiblockMachineDefinition.CATALYST_CANDIDATES) {
            MultiblockMachineDefinition.CATALYST_CANDIDATES.values().forEach(candidates -> candidates.remove(definition));
            MultiblockMachineDefinition.CATALYST_CANDIDATES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        var postTask = new ArrayDeque<Runnable>();
        definition.loadProductiveTag(projectFile, tag, postTask);
        postTask.forEach(Runnable::run);
    }

    /**
     * Loads a multiblock project from disk.
     *
     * @param file flat legacy file, manifest file, or project directory
     * @return loaded project, or {@code null} when the project cannot be read
     */
    @Nullable
    @Override
    public IProject loadProject(File file) {
        try {
            var tag = readProjectFile(file);
            if (tag != null) {
                var proj = new MultiblockMachineProject();
                proj.loadingFile = existingProjectFile(file);
                proj.deserializeNBT(tag);
                proj.loadingFile = null;
                return proj;
            }
        } catch (IOException | RuntimeException e) {
            MBD2.LOGGER.error("Failed to load multiblock project {}", file, e);
        }
        return null;
    }

    /**
     * Serializes the base project plus selected multiblock pattern data.
     *
     * @return project NBT with top-level selected pattern mirror and full pattern list
     */
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

    /**
     * Writes a multiblock project, splitting pattern payloads into adjacent JSON files.
     *
     * @param file       requested output file or directory
     * @param projectTag fully expanded project tag
     * @throws IOException when the manifest or pattern files cannot be written
     */
    public static void writeProjectFile(File file, CompoundTag projectTag) throws IOException {
        File manifestFile = projectManifestFile(file).getAbsoluteFile();
        var splitSourceTag = projectTag.copy();
        expandPatternReferences(manifestFile, splitSourceTag);
        var splitProject = splitPatterns(manifestFile, splitSourceTag);
        Path parent = manifestFile.toPath().getParent();
        if (parent == null) {
            throw new IOException("Project manifest has no parent directory: " + manifestFile);
        }
        Files.createDirectories(parent);
        if (Files.isDirectory(manifestFile.toPath())) {
            throw new IOException("Project manifest target is a directory: " + manifestFile);
        }

        var pendingFiles = new ArrayList<PendingFile>();
        try {
            for (var entry : splitProject.patternFiles().entrySet()) {
                var target = patternFile(manifestFile, entry.getKey()).toPath();
                pendingFiles.add(new PendingFile(writePatternTemp(parent, entry.getValue()), target));
            }
            var manifestTemp = writeManifestTemp(parent, splitProject.manifest());
            pendingFiles.add(new PendingFile(manifestTemp, manifestFile.toPath()));
            commitPendingFiles(pendingFiles);
        } finally {
            for (var pending : pendingFiles) {
                deleteQuietly(pending.temporary());
            }
        }

        File requestedFile = file.getAbsoluteFile();
        if (isFlatProjectFile(requestedFile) && !requestedFile.equals(manifestFile) && requestedFile.isFile()) {
            try {
                Files.deleteIfExists(requestedFile.toPath());
            } catch (IOException e) {
                MBD2.LOGGER.warn("Saved multiblock manifest {}, but could not remove legacy flat file {}", manifestFile, requestedFile, e);
            }
        }
    }

    private static Path writePatternTemp(Path parent, CompoundTag patternTag) throws IOException {
        Path temporary = Files.createTempFile(parent, ".mbd2-pattern.", ".tmp");
        boolean verified = false;
        try {
            writePatternJson(temporary.toFile(), patternTag);
            var written = readPatternFile(temporary.toFile());
            if (!written.equals(patternTag)) {
                throw new IOException("Written multiblock pattern did not pass verification");
            }
            verified = true;
            return temporary;
        } finally {
            if (!verified) {
                deleteQuietly(temporary);
            }
        }
    }

    private static Path writeManifestTemp(Path parent, CompoundTag manifestTag) throws IOException {
        Path temporary = Files.createTempFile(parent, ".mbd2-manifest.", ".tmp");
        boolean verified = false;
        try {
            NbtIo.write(manifestTag, temporary.toFile());
            var written = NbtIo.read(temporary.toFile());
            if (written == null || !written.equals(manifestTag)) {
                throw new IOException("Written multiblock manifest did not pass verification");
            }
            verified = true;
            return temporary;
        } finally {
            if (!verified) {
                deleteQuietly(temporary);
            }
        }
    }

    private static void commitPendingFiles(List<PendingFile> pendingFiles) throws IOException {
        var backups = new LinkedHashMap<Path, Path>();
        var originalTargets = new HashSet<Path>();
        var touched = new ArrayList<Path>();
        try {
            for (var pending : pendingFiles) {
                var target = pending.target();
                if (Files.isDirectory(target)) {
                    throw new IOException("Project output target is a directory: " + target);
                }
                touched.add(target);
                if (Files.exists(target)) {
                    originalTargets.add(target);
                    var backup = Files.createTempFile(target.getParent(), ".mbd2-backup.", ".tmp");
                    Files.deleteIfExists(backup);
                    backups.put(target, backup);
                    moveAtomically(target, backup);
                }
                moveAtomically(pending.temporary(), target);
            }
        } catch (IOException e) {
            for (int i = touched.size() - 1; i >= 0; i--) {
                var target = touched.get(i);
                if (!originalTargets.contains(target) || backups.containsKey(target) && !Files.exists(target)) {
                    deleteQuietly(target);
                }
            }
            var backupEntries = new ArrayList<>(backups.entrySet());
            for (int i = backupEntries.size() - 1; i >= 0; i--) {
                var entry = backupEntries.get(i);
                if (Files.exists(entry.getValue())) {
                    moveAtomically(entry.getValue(), entry.getKey());
                }
            }
            throw e;
        } finally {
            for (var backup : backups.values()) {
                deleteQuietly(backup);
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            MBD2.LOGGER.warn("Could not remove temporary multiblock file {}", path, e);
        }
    }

    /**
     * Reads a multiblock project and expands any referenced pattern JSON files.
     *
     * @param file flat legacy file, manifest file, or project directory
     * @return expanded project tag, or {@code null} when the NBT file is empty
     * @throws IOException when the manifest or a referenced pattern file cannot be read
     */
    @Nullable
    public static CompoundTag readProjectFile(File file) throws IOException {
        var projectFile = existingProjectFile(file);
        var tag = NbtIo.read(projectFile);
        if (tag != null) {
            expandPatternReferences(projectFile, tag);
        }
        return tag;
    }

    /**
     * Resolves the existing project file for a requested path.
     *
     * @param file flat file, manifest file, or project directory
     * @return manifest file when present, otherwise {@code file}
     */
    public static File existingProjectFile(File file) {
        File manifestFile = projectManifestFile(file);
        if (manifestFile.isFile()) {
            return manifestFile;
        }
        return file;
    }

    /**
     * Resolves the manifest path used by the split-pattern project format.
     *
     * @param file flat file, manifest file, or project directory
     * @return manifest path named after its containing directory
     */
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

    /**
     * Appends a pattern to a project tag and mirrors it as the selected top-level pattern.
     *
     * @param projectTag   mutable project tag to update
     * @param newPattern   expanded pattern tag to append
     * @param existingFile whether {@code projectTag} came from an existing project file and may contain references
     * @param projectFile  project file used to resolve references when {@code existingFile} is true
     * @return selected index of the appended pattern
     * @throws IOException when existing pattern references cannot be expanded
     */
    public static int appendPattern(CompoundTag projectTag, CompoundTag newPattern, boolean existingFile, File projectFile) throws IOException {
        if (existingFile) {
            expandPatternReferences(projectFile, projectTag);
        }
        ListTag patterns = new ListTag();
        if (projectTag.contains("patterns")) {
            if (!(projectTag.get("patterns") instanceof ListTag existingPatterns) || !allCompounds(existingPatterns)) {
                throw new IOException("Multiblock patterns must be a compound list");
            }
            patterns.addAll(existingPatterns);
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

    private record SplitProject(CompoundTag manifest, LinkedHashMap<String, CompoundTag> patternFiles) {
    }

    private static SplitProject splitPatterns(File projectFile, CompoundTag projectTag) throws IOException {
        var tag = projectTag.copy();
        ListTag patternRefs = new ListTag();
        var patternFiles = new LinkedHashMap<String, CompoundTag>();
        if (tag.contains("patterns")) {
            if (!(tag.get("patterns") instanceof ListTag patterns) || !allCompounds(patterns)) {
                throw new IOException("Multiblock patterns must be a compound list");
            }
            for (int i = 0; i < patterns.size(); i++) {
                var pattern = patterns.getCompound(i);
                if (isPatternReference(pattern)) {
                    pattern = readPatternFile(patternFile(projectFile, pattern.getString(PATTERN_FILE_KEY)));
                }
                var fileName = patternFileName(projectFile, i, patterns.size());
                patternFiles.put(fileName, pattern);
                var ref = new CompoundTag();
                ref.putString(PATTERN_FILE_KEY, fileName);
                patternRefs.add(ref);
            }
        } else if (tag.contains("placeholders", Tag.TAG_COMPOUND)) {
            var pattern = copyTopLevelPattern(tag);
            var fileName = patternFileName(projectFile, 0, 1);
            patternFiles.put(fileName, pattern);
            var ref = new CompoundTag();
            ref.putString(PATTERN_FILE_KEY, fileName);
            patternRefs.add(ref);
        }
        if (!patternRefs.isEmpty()) {
            tag.put("patterns", patternRefs);
            tag.remove(PATTERN_FILES_KEY);
            removePatternValues(tag);
        }
        return new SplitProject(tag, patternFiles);
    }

    /**
     * Expands pattern JSON references in a project tag loaded from the filesystem.
     *
     * @param projectFile manifest or flat project file used as the pattern-reference base path
     * @param projectTag  mutable project tag to expand
     * @throws IOException when a referenced pattern file cannot be read
     */
    public static void expandPatternReferences(File projectFile, CompoundTag projectTag) throws IOException {
        String key = getPatternReferenceKey(projectTag);
        if (key == null) {
            return;
        }
        if (!projectTag.contains(key, Tag.TAG_LIST) || !(projectTag.get(key) instanceof ListTag patterns) || !allCompounds(patterns)) {
            throw new IOException("Multiblock pattern references must be a compound list");
        }
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
            projectTag.remove(PATTERN_FILES_KEY);
            if (!expandedPatterns.isEmpty()) {
                var selected = Math.max(0, Math.min(projectTag.getInt("selected_pattern"), expandedPatterns.size() - 1));
                mirrorPatternToTopLevel(projectTag, expandedPatterns.getCompound(selected));
            }
        }
    }

    /**
     * Expands pattern JSON references in a project tag loaded from mod resources.
     *
     * @param source      class whose classloader provides the asset resource
     * @param projectFile asset-relative project file path
     * @param projectTag  mutable project tag to expand
     * @throws IOException when a referenced pattern resource cannot be read
     */
    public static void expandPatternReferences(Class<?> source, String projectFile, CompoundTag projectTag) throws IOException {
        String key = getPatternReferenceKey(projectTag);
        if (key == null) {
            return;
        }
        if (!projectTag.contains(key, Tag.TAG_LIST) || !(projectTag.get(key) instanceof ListTag patterns) || !allCompounds(patterns)) {
            throw new IOException("Multiblock pattern references must be a compound list");
        }
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
            projectTag.remove(PATTERN_FILES_KEY);
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
                    patterns.stream().map(CompoundTag.class::cast).anyMatch(MultiblockMachineProject::isPatternReference) ?
                    "patterns" : null;
        }
        return projectTag.contains(PATTERN_FILES_KEY, Tag.TAG_LIST) ? PATTERN_FILES_KEY : null;
    }

    private static boolean isPatternReference(CompoundTag tag) {
        return tag.contains(PATTERN_FILE_KEY, Tag.TAG_STRING) && !tag.contains("placeholders", Tag.TAG_COMPOUND);
    }

    private static void writePatternJson(File file, CompoundTag patternTag) throws IOException {
        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var typeTable = new JsonObject();
            JsonElement json = tagToJson(patternTag, "", typeTable);
            compressPatternRows(json, typeTable);
            if (json.isJsonObject()) {
                var root = json.getAsJsonObject();
                if (root.has(JSON_TYPE_TABLE_KEY)) {
                    throw new IOException("Pattern data uses reserved JSON metadata key: " + JSON_TYPE_TABLE_KEY);
                }
                if (typeTable.size() > 0) {
                    root.add(JSON_TYPE_TABLE_KEY, typeTable);
                }
            }
            PATTERN_GSON.toJson(json, writer);
        }
    }

    private static CompoundTag readPatternFile(File file) throws IOException {
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
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
        try {
            JsonElement json = JsonParser.parseReader(reader);
            expandCompressedPatternRows(json);
            Tag tag = jsonToTag(json);
            if (!(tag instanceof CompoundTag compoundTag)) {
                throw new IOException("Multiblock pattern json root must be an object");
            }
            return compoundTag;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Invalid multiblock pattern JSON", e);
        }
    }

    private static void compressPatternRows(JsonElement json, JsonObject typeTable) {
        if (!json.isJsonObject()) {
            return;
        }
        var root = json.getAsJsonObject();
        var placeholdersElement = root.get("placeholders");
        if (placeholdersElement == null || !placeholdersElement.isJsonObject()) {
            return;
        }
        var placeholders = placeholdersElement.getAsJsonObject();
        var patternElement = placeholders.get("pattern");
        if (patternElement == null || !patternElement.isJsonArray()) {
            return;
        }
        JsonArray pattern = patternElement.getAsJsonArray();
        for (int x = 0; x < pattern.size(); x++) {
            var xElement = pattern.get(x);
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
                    removeTypeEntries(typeTable, childPath(childPath(childPath("", "placeholders"), "pattern"), x) + "/" + y);
                    xTag.set(y, new JsonPrimitive(value));
                }
            }
        }
    }

    private static void expandCompressedPatternRows(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        var root = json.getAsJsonObject();
        var placeholdersElement = root.get("placeholders");
        if (placeholdersElement == null || !placeholdersElement.isJsonObject()) {
            return;
        }
        var placeholders = placeholdersElement.getAsJsonObject();
        var patternElement = placeholders.get("pattern");
        if (patternElement == null || !patternElement.isJsonArray()) {
            return;
        }
        int zSize = placeholders.has("z") && isJsonInteger(placeholders.get("z")) ? placeholders.get("z").getAsInt() : 0;
        if (zSize < 0 || zSize > MAX_PATTERN_AXIS_SIZE) {
            throw new IllegalArgumentException("Compressed pattern row width is invalid: " + zSize);
        }
        JsonArray pattern = patternElement.getAsJsonArray();
        if (pattern.size() > MAX_PATTERN_AXIS_SIZE) {
            throw new IllegalArgumentException("Compressed pattern contains too many x-slices");
        }
        for (JsonElement xElement : pattern) {
            if (!xElement.isJsonArray()) {
                continue;
            }
            JsonArray xTag = xElement.getAsJsonArray();
            if (xTag.size() > MAX_PATTERN_AXIS_SIZE) {
                throw new IllegalArgumentException("Compressed pattern contains too many y-slices");
            }
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
        if (json != null && json.isJsonObject()) {
            var root = json.getAsJsonObject();
            var typeTableElement = root.get(JSON_TYPE_TABLE_KEY);
            if (typeTableElement != null && typeTableElement.isJsonObject()) {
                var typeTable = typeTableElement.getAsJsonObject();
                root.remove(JSON_TYPE_TABLE_KEY);
                return jsonToTag(root, "", typeTable);
            }
        }
        return jsonToTag(json, "", null);
    }

    private static Tag jsonToTag(JsonElement json, String path, @Nullable JsonObject typeTable) {
        var declaredType = typeTable == null ? null : getDeclaredType(typeTable, path);
        if (declaredType != null) {
            return jsonToDeclaredTag(json, path, declaredType, typeTable);
        }
        if (json == null || json.isJsonNull()) {
            return StringTag.valueOf("");
        }
        if (json.isJsonObject()) {
            var tag = new CompoundTag();
            JsonObject object = json.getAsJsonObject();
            for (var entry : object.entrySet()) {
                tag.put(entry.getKey(), jsonToTag(entry.getValue(), childPath(path, entry.getKey()), typeTable));
            }
            return tag;
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.isEmpty()) {
                return new ListTag();
            }
            if (array.asList().stream().allMatch(MultiblockMachineProject::isJsonInteger)) {
                var integerValues = array.asList().stream().map(MultiblockMachineProject::parseJsonInteger).toList();
                if (integerValues.stream().allMatch(MultiblockMachineProject::fitsInt)) {
                    return new IntArrayTag(integerValues.stream().mapToInt(BigInteger::intValue).toArray());
                }
                if (integerValues.stream().allMatch(MultiblockMachineProject::fitsLong)) {
                    return new LongArrayTag(integerValues.stream().mapToLong(BigInteger::longValue).toArray());
                }
                throw new IllegalArgumentException("JSON integer array contains a value outside the NBT long range at " + path);
            }
            var tag = new ListTag();
            for (int i = 0; i < array.size(); i++) {
                tag.add(jsonToTag(array.get(i), childPath(path, i), typeTable));
            }
            return tag;
        }
        var primitive = json.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ByteTag.valueOf(primitive.getAsBoolean() ? (byte) 1 : (byte) 0);
        }
        if (primitive.isNumber()) {
            var number = parseJsonNumber(primitive);
            try {
                var integer = number.toBigIntegerExact();
                if (fitsInt(integer)) {
                    return IntTag.valueOf(integer.intValue());
                }
                if (fitsLong(integer)) {
                    return LongTag.valueOf(integer.longValue());
                }
                throw new IllegalArgumentException("JSON integer is outside the NBT long range at " + path);
            } catch (ArithmeticException ignored) {
                double doubleValue = number.doubleValue();
                if (!Double.isFinite(doubleValue)) {
                    throw new IllegalArgumentException("JSON number is outside the NBT double range at " + path);
                }
                float floatValue = (float) doubleValue;
                return (double) floatValue == doubleValue ? FloatTag.valueOf(floatValue) : DoubleTag.valueOf(doubleValue);
            }
        }
        return StringTag.valueOf(primitive.getAsString());
    }

    private static Tag jsonToDeclaredTag(JsonElement json, String path, String type, JsonObject typeTable) {
        return switch (type) {
            case "byte" -> ByteTag.valueOf((byte) requireJsonIntegral(json, path, Byte.MIN_VALUE, Byte.MAX_VALUE));
            case "short" -> ShortTag.valueOf((short) requireJsonIntegral(json, path, Short.MIN_VALUE, Short.MAX_VALUE));
            case "int" -> IntTag.valueOf((int) requireJsonIntegral(json, path, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case "long" -> LongTag.valueOf(requireJsonIntegral(json, path, Long.MIN_VALUE, Long.MAX_VALUE));
            case "float" -> FloatTag.valueOf(requireJsonFloat(json, path));
            case "double" -> DoubleTag.valueOf(requireJsonDouble(json, path));
            case "string" -> StringTag.valueOf(requireJsonPrimitive(json, path).getAsString());
            case "byte_array" -> {
                var array = requireJsonArray(json, path);
                var values = new byte[array.size()];
                for (int i = 0; i < array.size(); i++) {
                    values[i] = (byte) requireJsonIntegral(array.get(i), childPath(path, i), Byte.MIN_VALUE, Byte.MAX_VALUE);
                }
                yield new ByteArrayTag(values);
            }
            case "int_array" -> new IntArrayTag(requireJsonArray(json, path).asList().stream()
                    .mapToInt(element -> (int) requireJsonIntegral(element, path, Integer.MIN_VALUE, Integer.MAX_VALUE)).toArray());
            case "long_array" -> new LongArrayTag(requireJsonArray(json, path).asList().stream()
                    .mapToLong(element -> requireJsonIntegral(element, path, Long.MIN_VALUE, Long.MAX_VALUE)).toArray());
            case "list" -> {
                var array = requireJsonArray(json, path);
                var list = new ListTag();
                for (int i = 0; i < array.size(); i++) {
                    list.add(jsonToTag(array.get(i), childPath(path, i), typeTable));
                }
                yield list;
            }
            default -> throw new IllegalArgumentException("Unknown NBT JSON type '" + type + "' at " + path);
        };
    }

    private static String getDeclaredType(JsonObject typeTable, String path) {
        var value = typeTable.get(path);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static JsonPrimitive requireJsonPrimitive(JsonElement json, String path) {
        if (json == null || !json.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected JSON primitive at " + path);
        }
        return json.getAsJsonPrimitive();
    }

    private static JsonArray requireJsonArray(JsonElement json, String path) {
        if (json == null || !json.isJsonArray()) {
            throw new IllegalArgumentException("Expected JSON array at " + path);
        }
        return json.getAsJsonArray();
    }

    private static long requireJsonIntegral(JsonElement json, String path, long min, long max) {
        var value = parseJsonInteger(json);
        if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
            throw new IllegalArgumentException("JSON integer is outside the expected range at " + path);
        }
        return value.longValue();
    }

    private static float requireJsonFloat(JsonElement json, String path) {
        var primitive = requireJsonPrimitive(json, path);
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException("Expected JSON number at " + path);
        }
        var value = Float.parseFloat(primitive.getAsString());
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("JSON float is not finite at " + path);
        }
        return value;
    }

    private static double requireJsonDouble(JsonElement json, String path) {
        var primitive = requireJsonPrimitive(json, path);
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException("Expected JSON number at " + path);
        }
        var value = Double.parseDouble(primitive.getAsString());
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON double is not finite at " + path);
        }
        return value;
    }

    private static BigInteger parseJsonInteger(JsonElement element) {
        var primitive = requireJsonPrimitive(element, "number");
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException("Expected JSON integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).toBigIntegerExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Expected JSON integer", e);
        }
    }

    private static BigDecimal parseJsonNumber(JsonPrimitive primitive) {
        try {
            return new BigDecimal(primitive.getAsString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JSON number", e);
        }
    }

    private static boolean fitsInt(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 &&
                value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
    }

    private static boolean fitsLong(BigInteger value) {
        return value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0 &&
                value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }

    private static String childPath(String path, String key) {
        return path + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static String childPath(String path, int index) {
        return path + "/" + index;
    }

    private static void removeTypeEntries(JsonObject typeTable, String path) {
        var keys = new ArrayList<String>();
        for (var key : typeTable.keySet()) {
            if (key.equals(path) || key.startsWith(path + "/")) {
                keys.add(key);
            }
        }
        keys.forEach(typeTable::remove);
    }

    private static JsonElement tagToJson(Tag tag, String path, JsonObject typeTable) {
        if (tag instanceof CompoundTag compoundTag) {
            var object = new JsonObject();
            for (var key : compoundTag.getAllKeys()) {
                object.add(key, tagToJson(compoundTag.get(key), childPath(path, key), typeTable));
            }
            return object;
        }
        if (tag instanceof ListTag listTag) {
            typeTable.addProperty(path, "list");
            var array = new JsonArray();
            for (int i = 0; i < listTag.size(); i++) {
                array.add(tagToJson(listTag.get(i), childPath(path, i), typeTable));
            }
            return array;
        }
        if (tag instanceof ByteArrayTag byteArrayTag) {
            typeTable.addProperty(path, "byte_array");
            var array = new JsonArray();
            for (byte value : byteArrayTag.getAsByteArray()) {
                array.add(value);
            }
            return array;
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            typeTable.addProperty(path, "int_array");
            var array = new JsonArray();
            for (int value : intArrayTag.getAsIntArray()) {
                array.add(value);
            }
            return array;
        }
        if (tag instanceof LongArrayTag longArrayTag) {
            typeTable.addProperty(path, "long_array");
            var array = new JsonArray();
            for (long value : longArrayTag.getAsLongArray()) {
                array.add(value);
            }
            return array;
        }
        if (tag instanceof ByteTag byteTag) {
            typeTable.addProperty(path, "byte");
            return new JsonPrimitive(byteTag.getAsByte());
        }
        if (tag instanceof ShortTag shortTag) {
            typeTable.addProperty(path, "short");
            return new JsonPrimitive(shortTag.getAsShort());
        }
        if (tag instanceof IntTag intTag) {
            typeTable.addProperty(path, "int");
            return new JsonPrimitive(intTag.getAsInt());
        }
        if (tag instanceof LongTag longTag) {
            typeTable.addProperty(path, "long");
            return new JsonPrimitive(longTag.getAsLong());
        }
        if (tag instanceof FloatTag floatTag) {
            typeTable.addProperty(path, "float");
            return new JsonPrimitive(floatTag.getAsFloat());
        }
        if (tag instanceof DoubleTag doubleTag) {
            typeTable.addProperty(path, "double");
            return new JsonPrimitive(doubleTag.getAsDouble());
        }
        if (tag instanceof StringTag stringTag) {
            typeTable.addProperty(path, "string");
            return new JsonPrimitive(stringTag.getAsString());
        }
        if (tag instanceof EndTag) {
            throw new IllegalArgumentException("EndTag cannot be encoded in a multiblock pattern JSON object");
        }
        throw new IllegalArgumentException("Unsupported NBT tag in multiblock pattern JSON: " + tag.getClass().getName());
    }

    private static boolean isJsonInteger(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            return fitsInt(parseJsonInteger(element));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String resourcePatternPath(String projectFile, String fileName) throws IOException {
        var normalized = projectFile.replace('\\', '/');
        Path projectPath = Path.of(normalized);
        Path parent = projectPath.getParent();
        Path base = parent == null ? Path.of("") : parent;
        Path candidate = base.resolve(fileName).normalize();
        if (candidate.isAbsolute() || candidate.startsWith("..")) {
            throw new IOException("Pattern resource path escapes its project directory: " + fileName);
        }
        return candidate.toString().replace('\\', '/');
    }

    private static File patternFile(File projectFile, String fileName) throws IOException {
        Path base = patternDirectory(projectFile).getAbsoluteFile().toPath().normalize();
        Path candidate = base.resolve(fileName).normalize();
        if (candidate.equals(base) || !candidate.startsWith(base)) {
            throw new IOException("Pattern path escapes its project directory: " + fileName);
        }
        return candidate.toFile();
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

    /**
     * Serializes the placeholder grid using indexed holders and predicate references.
     *
     * <p>Repeated placeholder instances and predicate references are stored once and addressed by integer indices in
     * the pattern grid, keeping large multiblock patterns compact.</p>
     *
     * @param blockPlaceholders non-empty 3D placeholder grid
     * @return placeholder compound containing dimensions, holder table, predicate table, and pattern indices
     */
    public static CompoundTag serializeBlockPlaceholders(BlockPlaceholder[][][] blockPlaceholders) {
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

    /**
     * Deserializes base project state and all multiblock pattern snapshots.
     *
     * <p>When {@link #loadingFile} is set, pattern references are expanded before the base project reads resources and
     * definitions. The selected pattern is clamped and applied to the mutable editor fields.</p>
     *
     * @param tag project NBT
     */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Multiblock project data is missing");
        }
        if (loadingFile != null) {
            try {
                expandPatternReferences(loadingFile, tag);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load multiblock pattern JSON for " + loadingFile, e);
            }
        }
        super.deserializeNBT(tag);
        if (!(resources.resources.get(PredicateResource.RESOURCE_NAME) instanceof PredicateResource resource)) {
            throw new IllegalArgumentException("Multiblock project is missing its predicate resource");
        }
        this.predicateResource = resource;
        this.multiblockPatterns.clear();
        if (tag.contains("patterns")) {
            if (!(tag.get("patterns") instanceof ListTag patterns)) {
                throw new IllegalArgumentException("Multiblock project patterns must be a list");
            }
            for (int i = 0; i < patterns.size(); i++) {
                if (!(patterns.get(i) instanceof CompoundTag pattern)) {
                    throw new IllegalArgumentException("Multiblock pattern " + i + " is not a compound");
                }
                this.multiblockPatterns.add(deserializePattern(pattern));
            }
        }
        if (this.multiblockPatterns.isEmpty()) {
            if (!tag.contains("placeholders", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Multiblock project contains no pattern data");
            }
            this.multiblockPatterns.add(deserializePattern(tag));
        }
        if (tag.contains("selected_pattern") && !(tag.get("selected_pattern") instanceof NumericTag)) {
            throw new IllegalArgumentException("Multiblock selected pattern index is not numeric");
        }
        this.selectedPatternIndex = Math.max(0, Math.min(tag.getInt("selected_pattern"), this.multiblockPatterns.size() - 1));
        applyPattern(this.multiblockPatterns.get(selectedPatternIndex));
    }

    private PatternInfo deserializePattern(CompoundTag tag) {
        if (isPatternReference(tag)) {
            throw new IllegalArgumentException("Unresolved multiblock pattern reference: " + tag.getString(PATTERN_FILE_KEY));
        }
        if (!tag.contains("placeholders", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Multiblock pattern is missing its placeholders compound");
        }
        var placeHoldersTag = tag.getCompound("placeholders");
        var x = requireDimension(placeHoldersTag, "x", "placeholder");
        var y = requireDimension(placeHoldersTag, "y", "placeholder");
        var z = requireDimension(placeHoldersTag, "z", "placeholder");
        var blockPlaceholders = deserializeBlockPlaceholders(placeHoldersTag, predicateResource);
        var layerAxisName = tag.getString("layer_axis");
        Direction.Axis layerAxis;
        if (tag.contains("layer_axis") && !(tag.get("layer_axis") instanceof StringTag)) {
            throw new IllegalArgumentException("Multiblock layer axis must be a string");
        }
        if (layerAxisName.isBlank()) {
            layerAxis = Direction.Axis.Y;
        } else {
            try {
                layerAxis = Direction.Axis.valueOf(layerAxisName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid multiblock layer axis: " + layerAxisName, e);
            }
        }
        var aisleLength = switch (layerAxis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        var repetitions = requireIntArray(tag, "aisle_repetitions", "aisle repetitions");
        if (repetitions.length != aisleLength * 2) {
            throw new IllegalArgumentException("Aisle repetition count does not match the " + layerAxis + " axis length");
        }
        var aisleRepetitions = new int[aisleLength][2];
        for (int i = 0; i < aisleLength; i++) {
            var min = repetitions[i * 2];
            var max = repetitions[i * 2 + 1];
            if (min < 1 || max < min || max > MAX_PATTERN_REPETITION) {
                throw new IllegalArgumentException("Invalid aisle repetition range at index " + i);
            }
            aisleRepetitions[i][0] = min;
            aisleRepetitions[i][1] = max;
        }
        var shapeInfos = new ArrayList<MultiblockShapeInfo>();
        if (tag.contains("shape_infos")) {
            if (!(tag.get("shape_infos") instanceof ListTag shapeInfoTags)) {
                throw new IllegalArgumentException("Multiblock shape_infos must be a list");
            }
            for (int i = 0; i < shapeInfoTags.size(); i++) {
                if (!(shapeInfoTags.get(i) instanceof CompoundTag shapeInfoTag)) {
                    throw new IllegalArgumentException("Multiblock shape info " + i + " is not a compound");
                }
                validateShapeInfo(shapeInfoTag, "shape_infos[" + i + "]");
                shapeInfos.add(MultiblockShapeInfo.loadFromTag(shapeInfoTag));
            }
        }
        return new PatternInfo(blockPlaceholders, layerAxis, aisleRepetitions, shapeInfos);
    }

    /**
     * Copies the legacy top-level pattern fields into a standalone pattern tag.
     *
     * @param projectTag project tag containing top-level pattern fields
     * @return new pattern tag
     */
    public static CompoundTag copyTopLevelPattern(CompoundTag projectTag) {
        CompoundTag pattern = new CompoundTag();
        copyPatternValue(projectTag, pattern, "placeholders");
        copyPatternValue(projectTag, pattern, "layer_axis");
        copyPatternValue(projectTag, pattern, "aisle_repetitions");
        copyPatternValue(projectTag, pattern, "shape_infos");
        return pattern;
    }

    /**
     * Mirrors a selected pattern into legacy top-level project fields.
     *
     * @param projectTag project tag to mutate
     * @param pattern    pattern tag whose fields should be mirrored
     */
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

    /**
     * Deserializes a placeholder grid from the indexed placeholder format.
     *
     * @param placeHoldersTag   serialized placeholder compound
     * @param predicateResource predicate resource used to resolve placeholder references
     * @return reconstructed 3D placeholder grid
     */
    public static BlockPlaceholder[][][] deserializeBlockPlaceholders(CompoundTag placeHoldersTag, PredicateResource predicateResource) {
        if (placeHoldersTag == null || predicateResource == null) {
            throw new IllegalArgumentException("Placeholder data or predicate resource is missing");
        }
        if (!placeHoldersTag.contains("holders", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Placeholder data is missing its holders list");
        }
        var holdersValue = placeHoldersTag.get("holders");
        if (!(holdersValue instanceof ListTag holders) || !allCompounds(holders)) {
            throw new IllegalArgumentException("Placeholder holders must be a compound list");
        }
        var placeHoldersListTag = holders;
        var predicateRefs = deserializePredicateReferences(placeHoldersTag);
        var x = requireDimension(placeHoldersTag, "x", "placeholder");
        var y = requireDimension(placeHoldersTag, "y", "placeholder");
        var z = requireDimension(placeHoldersTag, "z", "placeholder");
        var pattern = deserializePlaceholderPattern(placeHoldersTag, x, y, z);
        var blockPlaceholders = new BlockPlaceholder[x][y][z];
        for (int i = 0; i < pattern.length; i++) {
            var index = pattern[i];
            if (index < -1 || index >= placeHoldersListTag.size()) {
                throw new IllegalArgumentException("Placeholder index " + index + " is outside the holders list");
            }
            var holder = index == -1 ? BlockPlaceholder.create(predicateResource, Either.left("any")) :
                    deserializeHolder(predicateResource, placeHoldersListTag.getCompound(index), predicateRefs);
            blockPlaceholders[i / (y * z)][(i / z) % y][i % z] = holder;
        }
        return blockPlaceholders;
    }

    private static List<Either<String, File>> deserializePredicateReferences(CompoundTag placeHoldersTag) {
        var predicates = new ArrayList<Either<String, File>>();
        if (!placeHoldersTag.contains("predicates")) {
            return predicates;
        }
        if (!(placeHoldersTag.get("predicates") instanceof ListTag predicatesTag) || !allCompounds(predicatesTag)) {
            throw new IllegalArgumentException("Placeholder predicates must be a compound list");
        }
        for (var tag : predicatesTag) {
            var compoundTag = (CompoundTag) tag;
            predicates.add(deserializePredicateReference(compoundTag));
        }
        return predicates;
    }

    private static Either<String, File> deserializePredicateReference(CompoundTag tag) {
        var key = tag.getString("key");
        var type = tag.getString("type");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Placeholder predicate reference has no key");
        }
        if ("project".equals(type)) {
            return Either.right(new File(key));
        }
        if ("builtin".equals(type)) {
            return Either.left(key);
        }
        throw new IllegalArgumentException("Unknown placeholder predicate reference type: " + type);
    }

    private static BlockPlaceholder deserializeHolder(PredicateResource predicateResource,
                                                      CompoundTag tag,
                                                      List<Either<String, File>> predicateRefs) {
        if (tag.contains("predicates") && tag.contains("isController") && tag.contains("facing")) {
            if (!(tag.get("isController") instanceof NumericTag)) {
                throw new IllegalArgumentException("Holder controller flag is not numeric");
            }
            var holder = BlockPlaceholder.create(predicateResource);
            for (int predicateIndex : requireIntArray(tag, "predicates", "holder predicates")) {
                if (predicateIndex < 0 || predicateIndex >= predicateRefs.size()) {
                    throw new IllegalArgumentException("Holder predicate index " + predicateIndex + " is invalid");
                }
                holder.getPredicates().add(predicateRefs.get(predicateIndex));
            }
            holder.setController(getBooleanCompat(tag, "isController"));
            var facing = requireInt(tag, "facing", "holder facing");
            if (facing < 0 || facing >= Direction.values().length) {
                throw new IllegalArgumentException("Holder facing value is invalid: " + facing);
            }
            holder.setFacing(Direction.from3DDataValue(facing));
            return holder;
        }
        if (tag.contains("facing")) {
            var facing = requireInt(tag, "facing", "holder facing");
            if (facing < 0 || facing >= Direction.values().length) {
                throw new IllegalArgumentException("Holder facing value is invalid: " + facing);
            }
        }
        return BlockPlaceholder.fromTag(predicateResource, tag);
    }

    private static int[] deserializePlaceholderPattern(CompoundTag placeHoldersTag, int xSize, int ySize, int zSize) {
        long cellCount = (long) xSize * ySize * zSize;
        if (cellCount > MAX_PATTERN_CELLS || cellCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Placeholder grid is too large");
        }
        if (placeHoldersTag.contains("pattern", Tag.TAG_INT_ARRAY)) {
            var flat = placeHoldersTag.getIntArray("pattern");
            if (flat.length != cellCount) {
                throw new IllegalArgumentException("Flattened placeholder pattern length does not match its dimensions");
            }
            return flat;
        }
        if (!(placeHoldersTag.get("pattern") instanceof ListTag patternTag) || patternTag.size() != xSize) {
            throw new IllegalArgumentException("Placeholder pattern must contain one x-slice per dimension");
        }
        var pattern = new int[(int) cellCount];
        var index = 0;
        for (int x = 0; x < xSize; x++) {
            if (!(patternTag.get(x) instanceof ListTag xTag) || xTag.size() != ySize) {
                throw new IllegalArgumentException("Placeholder pattern y-slice does not match its dimensions at x=" + x);
            }
            for (int y = 0; y < ySize; y++) {
                var row = requireIntArray(xTag.get(y), "placeholder pattern row " + x + "," + y);
                if (row.length != zSize) {
                    throw new IllegalArgumentException("Placeholder pattern row does not match its z dimension at x=" + x + ", y=" + y);
                }
                for (int value : row) {
                    if (value < -1) {
                        throw new IllegalArgumentException("Placeholder pattern contains an invalid index: " + value);
                    }
                    pattern[index++] = value;
                }
            }
        }
        return pattern;
    }

    private static boolean allCompounds(ListTag list) {
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag)) {
                return false;
            }
        }
        return true;
    }

    private static int requireDimension(CompoundTag tag, String key, String context) {
        var value = requireInt(tag, key, context + " " + key);
        if (value <= 0 || value > MAX_PATTERN_AXIS_SIZE) {
            throw new IllegalArgumentException("Invalid " + context + " dimension " + key + ": " + value);
        }
        return value;
    }

    private static int requireInt(CompoundTag tag, String key, String context) {
        if (!tag.contains(key)) {
            throw new IllegalArgumentException("Missing " + context);
        }
        var value = tag.get(key);
        if (!(value instanceof NumericTag numericTag)) {
            throw new IllegalArgumentException("Non-numeric " + context);
        }
        var number = numericTag.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid integer " + context);
        }
        return numericTag.getAsInt();
    }

    private static int[] requireIntArray(CompoundTag tag, String key, String context) {
        if (!tag.contains(key)) {
            throw new IllegalArgumentException("Missing " + context);
        }
        return requireIntArray(tag.get(key), context);
    }

    private static int[] requireIntArray(Tag tag, String context) {
        if (tag instanceof IntArrayTag intArrayTag) {
            return intArrayTag.getAsIntArray();
        }
        if (!(tag instanceof ListTag listTag)) {
            throw new IllegalArgumentException("" + context + " must be an integer array or numeric list");
        }
        var values = new int[listTag.size()];
        for (int i = 0; i < listTag.size(); i++) {
            var entry = listTag.get(i);
            if (!(entry instanceof NumericTag numericTag)) {
                throw new IllegalArgumentException(context + " contains a non-numeric value at index " + i);
            }
            var number = numericTag.getAsDouble();
            if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(context + " contains an invalid integer at index " + i);
            }
            values[i] = numericTag.getAsInt();
        }
        return values;
    }

    private static void validateShapeInfo(CompoundTag tag, String context) {
        var x = requireDimension(tag, "x", context);
        var y = requireDimension(tag, "y", context);
        var z = requireDimension(tag, "z", context);
        var cells = (long) x * y * z;
        if (cells > MAX_PATTERN_CELLS) {
            throw new IllegalArgumentException(context + " is too large");
        }
        if (!(tag.get("blocks") instanceof ListTag blocks) || !allCompounds(blocks) || blocks.size() != cells) {
            throw new IllegalArgumentException(context + " blocks do not match its dimensions");
        }
        for (int i = 0; i < blocks.size(); i++) {
            var block = (CompoundTag) blocks.get(i);
            if (block.contains("facing")) {
                if (!(block.get("facing") instanceof StringTag facingTag) || Direction.byName(facingTag.getAsString()) == null) {
                    throw new IllegalArgumentException(context + " contains an invalid controller facing at block " + i);
                }
            }
        }
        if (tag.contains("description")) {
            if (!(tag.get("description") instanceof ListTag description) ||
                    !description.stream().allMatch(StringTag.class::isInstance)) {
                throw new IllegalArgumentException(context + " description must be a string list");
            }
        }
    }

    /**
     * Reads an integer array from a compound in either modern int-array or legacy numeric-list form.
     *
     * @param tag compound containing the value
     * @param key value key
     * @return decoded integers, or an empty array when absent or incompatible
     */
    public static int[] getIntArrayCompat(CompoundTag tag, String key) {
        return tag.contains(key) ? getIntArrayCompat(tag.get(key)) : new int[0];
    }

    /**
     * Decodes an integer array from an NBT tag.
     *
     * @param tag int-array or numeric-list tag
     * @return decoded integers, or an empty array for unsupported tag types
     */
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

    /**
     * Reads a boolean from either a normal boolean tag or a legacy numeric tag.
     *
     * @param tag compound containing the value
     * @param key value key
     * @return decoded boolean, defaulting to {@code false} when absent
     */
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

    /**
     * Adds multiblock-specific editor tabs after the base machine tabs are loaded.
     *
     * @param editor editor receiving the multiblock area and pattern panels
     */
    @Override
    public void onLoad(Editor editor) {
        if (editor instanceof MachineEditor machineEditor) {
            super.onLoad(editor);
            var tabContainer = machineEditor.getTabPages();
            var multiblockPatternPanel = createMultiblockPatternPanel(machineEditor);
            var multiblockAreaPanel = createMultiblockAreaPanel(multiblockPatternPanel);
            tabContainer.addTab("editor.machine.multiblock_area", multiblockAreaPanel, multiblockAreaPanel::onPanelSelected, multiblockAreaPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.multiblock_pattern", multiblockPatternPanel, multiblockPatternPanel::onPanelSelected, multiblockPatternPanel::onPanelDeselected);
        }
    }

    /**
     * Creates the multiblock pattern editor panel.
     *
     * @param editor owning machine editor
     * @return pattern panel bound to this project
     */
    public MultiblockPatternPanel createMultiblockPatternPanel(MachineEditor editor) {
        return new MultiblockPatternPanel(editor, this);
    }

    /**
     * Creates the multiblock area panel paired with a pattern panel.
     *
     * @param multiblockPatternPanel pattern panel to coordinate with
     * @return multiblock area panel bound to this project
     */
    public MultiblockAreaPanel createMultiblockAreaPanel(MultiblockPatternPanel multiblockPatternPanel) {
        return new MultiblockAreaPanel(this, multiblockPatternPanel);
    }

}
