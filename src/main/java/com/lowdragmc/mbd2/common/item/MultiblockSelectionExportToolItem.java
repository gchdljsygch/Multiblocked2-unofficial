package com.lowdragmc.mbd2.common.item;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SearchComponentWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateBlocks;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateFluids;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateStates;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.PredicateResource;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.BlockPlaceholder;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockSelectionExportToolItem extends Item implements HeldItemUIFactory.IHeldItemUIHolder {
    private static final String TAG_ROOT = "selection_export_tool";
    private static final String TAG_FIRST = "first";
    private static final String TAG_SECOND = "second";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_TARGET = "target";
    private static final String TAG_FACING = "facing";
    private static final String DEFAULT_TARGET = "new_multiblock.mb";
    private static final Direction[] GUI_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };

    public MultiblockSelectionExportToolItem() {
        super(new Item.Properties().fireResistant().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isCrouching()) {
            if (player instanceof ServerPlayer serverPlayer) {
                clearSelection(stack);
                serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_selection_export_tool.selection.clear"));
                player.getInventory().setChanged();
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.isCrouching()) {
            clearSelection(stack);
            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_selection_export_tool.selection.clear"));
            player.getInventory().setChanged();
            return InteractionResult.SUCCESS;
        }

        Selection selection = readSelection(stack);
        ResourceLocation dimension = context.getLevel().dimension().location();
        BlockPos clickedPos = context.getClickedPos().immutable();
        if (!selection.hasFirst() || selection.hasSecond() || !dimension.equals(selection.dimension())) {
            writeFirstCorner(stack, dimension, clickedPos);
            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_selection_export_tool.selection.first",
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
        } else {
            writeSecondCorner(stack, clickedPos);
            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_selection_export_tool.selection.second",
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
        }
        player.getInventory().setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, java.util.List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.0").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.1").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.blocks").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.2", getTargetPath(stack)).withStyle(ChatFormatting.DARK_GREEN));
        components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.3", getFacing(stack).getSerializedName()).withStyle(ChatFormatting.DARK_GREEN));
        Selection selection = readSelection(stack);
        selection.first().ifPresent(pos -> components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.first",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.DARK_AQUA)));
        selection.second().ifPresent(pos -> components.add(Component.translatable("item.mbd2.mbd_selection_export_tool.tooltip.second",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.DARK_AQUA)));
    }

    @Override
    public ModularUI createUI(Player entityPlayer, HeldItemUIFactory.HeldItemHolder holder) {
        ItemStack stack = holder.getHeld();
        var fileSearch = new SearchComponentWidget<>(66, 22, 158, 12, new SearchComponentWidget.IWidgetSearch<String>() {
            @Override
            public String resultDisplay(String value) {
                return value;
            }

            @Override
            public void selectResult(String value) {
                setTargetPath(stack, value);
            }

            @Override
            public void search(String word, Consumer<String> find) {
                findExistingMultiblockFiles(word, find);
            }

            @Override
            public void serialize(String value, FriendlyByteBuf buf) {
                buf.writeUtf(value);
            }

            @Override
            public String deserialize(FriendlyByteBuf buf) {
                return buf.readUtf();
            }
        }, true);
        fileSearch.setCapacity(5);
        fileSearch.textFieldWidget.setCurrentString(getTargetPath(stack));
        fileSearch.textFieldWidget.setMaxStringLength(256);

        WidgetGroup directionButtons = new WidgetGroup(66, 48, 158, 14);
        int x = 0;
        for (Direction direction : GUI_DIRECTIONS) {
            directionButtons.addWidget(new ButtonWidget(x, 0, 24, 14,
                    buttonTexture(direction.getSerializedName()),
                    cd -> setFacing(stack, direction))
                    .setHoverTexture(ColorPattern.WHITE.borderTexture(1),
                            new TextTexture(direction.getSerializedName())));
            x += 26;
        }

        TextTextureWidget facingText = new TextTextureWidget(66, 64, 158, 10)
                .setText(() -> Component.translatable("item.mbd2.mbd_selection_export_tool.gui.current_facing",
                        getFacing(stack).getSerializedName()));
        TextTextureWidget selectionText = new TextTextureWidget(10, 78, 214, 10)
                .setText(() -> selectionStatus(stack));

        ModularUI ui = new ModularUI(234, 112, holder, entityPlayer)
                .background(ResourceBorderTexture.BORDERED_BACKGROUND)
                .widget(new ImageWidget(10, 8, 214, 10,
                        new TextTexture("item.mbd2.mbd_selection_export_tool.gui.title").setWidth(214)))
                .widget(new ImageWidget(10, 22, 52, 12,
                        new TextTexture("item.mbd2.mbd_selection_export_tool.gui.file").setWidth(52)))
                .widget(fileSearch)
                .widget(new ImageWidget(10, 48, 52, 12,
                        new TextTexture("item.mbd2.mbd_selection_export_tool.gui.facing").setWidth(52)))
                .widget(directionButtons)
                .widget(facingText)
                .widget(selectionText)
                .widget(new ButtonWidget(66, 92, 158, 14,
                        buttonTexture("item.mbd2.mbd_selection_export_tool.gui.export"),
                        cd -> {
                            if (entityPlayer instanceof ServerPlayer serverPlayer) {
                                String target = fileSearch.textFieldWidget.getCurrentString();
                                setTargetPath(stack, target);
                                ExportResult result = exportSelection(serverPlayer, stack, target);
                                serverPlayer.sendSystemMessage(result.message());
                                serverPlayer.getInventory().setChanged();
                            }
                        }).setHoverTexture(ColorPattern.WHITE.borderTexture(1),
                        new TextTexture("item.mbd2.mbd_selection_export_tool.gui.export")));
        ui.registerCloseListener(() -> setTargetPath(stack, fileSearch.textFieldWidget.getCurrentString()));
        return ui;
    }

    private static Component selectionStatus(ItemStack stack) {
        Selection selection = readSelection(stack);
        if (selection.isComplete()) {
            BlockPos first = selection.first().orElseThrow();
            BlockPos second = selection.second().orElseThrow();
            int x = Math.abs(first.getX() - second.getX()) + 1;
            int y = Math.abs(first.getY() - second.getY()) + 1;
            int z = Math.abs(first.getZ() - second.getZ()) + 1;
            return Component.translatable("item.mbd2.mbd_selection_export_tool.gui.selection.complete", x, y, z);
        }
        if (selection.hasFirst()) {
            return Component.translatable("item.mbd2.mbd_selection_export_tool.gui.selection.first_only");
        }
        return Component.translatable("item.mbd2.mbd_selection_export_tool.gui.selection.empty");
    }

    private static GuiTextureGroup buttonTexture(String text) {
        return new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture().setRadius(4), new TextTexture(text).setWidth(80));
    }

    private static ExportResult exportSelection(ServerPlayer player, ItemStack stack, String rawPath) {
        Selection selection = readSelection(stack);
        if (!selection.isComplete()) {
            return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.incomplete"));
        }
        if (!player.level().dimension().location().equals(selection.dimension())) {
            return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.dimension",
                    selection.dimension()));
        }

        File target = resolveTargetFile(rawPath);
        if (target == null) {
            return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.path"));
        }

        try {
            File projectFile = MultiblockMachineProject.existingProjectFile(target);
            boolean existed = projectFile.isFile();
            CompoundTag projectTag = existed ? NbtIo.read(projectFile) : createDefaultProjectTag(target);
            if (projectTag == null) {
                return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.read"));
            }

            CompoundTag resourcesTag = projectTag.getCompound("resources");
            projectTag.put("resources", resourcesTag);
            PredicateResource predicateResource = new PredicateResource();
            predicateResource.deserializeNBT(resourcesTag.getCompound(PredicateResource.RESOURCE_NAME));

            PatternBuildResult pattern = buildPattern(player.level(), selection, getFacing(stack), predicateResource);
            if (!pattern.success()) {
                return ExportResult.failure(pattern.message());
            }
            resourcesTag.put(PredicateResource.RESOURCE_NAME, predicateResource.serializeNBT());

            int patternIndex = MultiblockMachineProject.appendPattern(projectTag, pattern.patternTag(), existed, MultiblockMachineProject.projectManifestFile(target));
            File parent = MultiblockMachineProject.projectManifestFile(target).getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.write"));
            }
            MultiblockMachineProject.writeProjectFile(target, projectTag);
            return ExportResult.success(Component.translatable("item.mbd2.mbd_selection_export_tool.export.success",
                    target.getPath(), patternIndex + 1, pattern.createdPredicates()));
        } catch (IOException | RuntimeException e) {
            MBD2.LOGGER.error("Failed to export multiblock selection to {}", target, e);
            return ExportResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.write"));
        }
    }

    private static PatternBuildResult buildPattern(Level level, Selection selection, Direction facing, PredicateResource predicateResource) {
        BlockPos first = selection.first().orElseThrow();
        BlockPos second = selection.second().orElseThrow();
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        BlockPlaceholder[][][] placeholders = new BlockPlaceholder[sizeX][sizeY][sizeZ];
        BlockPos controllerPos = null;
        int createdPredicates = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    int localX = x - minX;
                    int localY = y - minY;
                    int localZ = z - minZ;

                    if (block == Blocks.STRUCTURE_BLOCK) {
                        if (controllerPos != null) {
                            return PatternBuildResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.multiple_controllers"));
                        }
                        controllerPos = pos.immutable();
                        placeholders[localX][localY][localZ] = BlockPlaceholder.controller(predicateResource).setFacing(facing);
                    } else if (block == Blocks.BARRIER) {
                        placeholders[localX][localY][localZ] = BlockPlaceholder.create(predicateResource, Either.left("any"));
                    } else {
                        PredicateKey predicateKey = findOrCreatePredicate(predicateResource, state);
                        createdPredicates += predicateKey.created() ? 1 : 0;
                        placeholders[localX][localY][localZ] = BlockPlaceholder.create(predicateResource, Either.left(predicateKey.key()));
                    }
                }
            }
        }

        if (controllerPos == null) {
            return PatternBuildResult.failure(Component.translatable("item.mbd2.mbd_selection_export_tool.export.failure.no_controller"));
        }

        CompoundTag patternTag = new CompoundTag();
        patternTag.put("placeholders", MultiblockMachineProject.serializeBlockPlaceholders(placeholders));
        patternTag.putString("layer_axis", Direction.Axis.Y.name());
        int[][] repetitions = new int[sizeY][2];
        for (int[] repetition : repetitions) {
            repetition[0] = 1;
            repetition[1] = 1;
        }
        patternTag.putIntArray("aisle_repetitions", Arrays.stream(repetitions).flatMapToInt(Arrays::stream).toArray());
        patternTag.put("shape_infos", new ListTag());
        return PatternBuildResult.success(patternTag, createdPredicates);
    }

    private static PredicateKey findOrCreatePredicate(PredicateResource predicateResource, BlockState state) {
        if (state.isAir()) {
            return new PredicateKey("any", false);
        }

        SimplePredicate predicate;
        String preferredKey;
        Block block = state.getBlock();
        if (block instanceof LiquidBlock liquidBlock) {
            Fluid fluid = liquidBlock.getFluid().getSource();
            predicate = new PredicateFluids(fluid);
            preferredKey = Optional.ofNullable(ForgeRegistries.FLUIDS.getKey(fluid)).map(ResourceLocation::toString).orElse("fluid");
        } else if (state.equals(block.defaultBlockState())) {
            predicate = new PredicateBlocks(block);
            preferredKey = Optional.ofNullable(ForgeRegistries.BLOCKS.getKey(block)).map(ResourceLocation::toString).orElse("block");
        } else {
            predicate = new PredicateStates(state);
            preferredKey = toStateKey(state);
        }

        CompoundTag targetTag = SimplePredicate.serializeWrapper(predicate);
        for (Map.Entry<String, SimplePredicate> entry : predicateResource.getBuiltinResources().entrySet()) {
            if (entry.getValue() != null && targetTag.equals(SimplePredicate.serializeWrapper(entry.getValue()))) {
                return new PredicateKey(entry.getKey(), false);
            }
        }

        String key = uniquePredicateKey(predicateResource, preferredKey);
        predicateResource.addBuiltinResource(key, predicate);
        return new PredicateKey(key, true);
    }

    private static String uniquePredicateKey(PredicateResource predicateResource, String preferredKey) {
        String key = preferredKey == null || preferredKey.isBlank() ? "predicate" : preferredKey;
        if (!predicateResource.hasBuiltinResource(key)) {
            return key;
        }
        int index = 1;
        while (predicateResource.hasBuiltinResource(key + "_" + index)) {
            index++;
        }
        return key + "_" + index;
    }

    private static CompoundTag createDefaultProjectTag(File target) {
        CompoundTag projectTag = new MultiblockMachineProject().newEmptyProject().serializeNBT();
        projectTag.getCompound("definition").putString("id", defaultMachineId(target).toString());
        projectTag.remove("patterns");
        projectTag.remove("placeholders");
        projectTag.remove("layer_axis");
        projectTag.remove("aisle_repetitions");
        projectTag.remove("shape_infos");
        projectTag.putInt("selected_pattern", 0);
        return projectTag;
    }

    private static ResourceLocation defaultMachineId(File target) {
        File root = new File(MBD2.getLocation(), "multiblock").getAbsoluteFile();
        String path;
        try {
            path = root.toPath().relativize(target.getAbsoluteFile().toPath()).toString();
        } catch (IllegalArgumentException e) {
            path = target.getName();
        }
        path = path.replace(File.separatorChar, '/');
        if (path.endsWith(".mb")) {
            path = path.substring(0, path.length() - 3);
        }
        path = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (path.isBlank()) {
            path = "selection_export";
        }
        return MBD2.id(path);
    }

    @Nullable
    private static File resolveTargetFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String path = rawPath.trim();
        if (!path.endsWith(".mb")) {
            path += ".mb";
        }
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(new File(MBD2.getLocation(), "multiblock"), path);
        }
        return file;
    }

    private static void findExistingMultiblockFiles(String word, Consumer<String> find) {
        File root = new File(MBD2.getLocation(), "multiblock");
        if (!root.isDirectory()) {
            return;
        }
        String filter = word == null ? "" : word.toLowerCase(Locale.ROOT);
        try (var stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mb"))
                    .map(path -> root.toPath().relativize(path).toString().replace(File.separatorChar, '/'))
                    .map(MultiblockSelectionExportToolItem::displayProjectPath)
                    .filter(path -> path.toLowerCase(Locale.ROOT).contains(filter))
                    .forEach(find);
        } catch (IOException ignored) {
        }
    }

    private static String displayProjectPath(String path) {
        if (!path.endsWith(".mb")) {
            return path;
        }
        String withoutExtension = path.substring(0, path.length() - 3);
        int slash = withoutExtension.lastIndexOf('/');
        if (slash >= 0 && withoutExtension.substring(slash + 1).equals(withoutExtension.substring(0, slash))) {
            return withoutExtension;
        }
        if (slash >= 0) {
            String parent = withoutExtension.substring(0, slash);
            String name = withoutExtension.substring(slash + 1);
            if (name.equals(parent.substring(parent.lastIndexOf('/') + 1))) {
                return parent;
            }
        }
        return path;
    }

    private static String toStateKey(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return "any";
        }
        var values = state.getValues();
        if (values.isEmpty()) {
            return id.toString();
        }
        var properties = new ArrayList<Property<?>>(values.keySet());
        properties.sort(Comparator.comparing(Property::getName));
        StringBuilder builder = new StringBuilder(64);
        builder.append(id).append('[');
        for (int i = 0; i < properties.size(); i++) {
            Property<?> property = properties.get(i);
            builder.append(property.getName()).append('=').append(getValueName(state, property));
            if (i + 1 < properties.size()) {
                builder.append(',');
            }
        }
        builder.append(']');
        return "state:" + builder;
    }

    private static <T extends Comparable<T>> String getValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static CompoundTag toolTag(ItemStack stack) {
        return stack.getOrCreateTagElement(TAG_ROOT);
    }

    @Nullable
    private static CompoundTag readToolTag(ItemStack stack) {
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return stackTag.getCompound(TAG_ROOT);
    }

    private static Selection readSelection(ItemStack stack) {
        CompoundTag tag = readToolTag(stack);
        if (tag == null) {
            return new Selection(Optional.empty(), Optional.empty(), null);
        }
        ResourceLocation dimension = tag.contains(TAG_DIMENSION) ? ResourceLocation.tryParse(tag.getString(TAG_DIMENSION)) : null;
        return new Selection(readPos(tag, TAG_FIRST), readPos(tag, TAG_SECOND), dimension);
    }

    private static Optional<BlockPos> readPos(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag pos = tag.getCompound(key);
        return Optional.of(new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z")));
    }

    private static void writeFirstCorner(ItemStack stack, ResourceLocation dimension, BlockPos pos) {
        CompoundTag tag = toolTag(stack);
        tag.put(TAG_FIRST, writePos(pos));
        tag.remove(TAG_SECOND);
        tag.putString(TAG_DIMENSION, dimension.toString());
    }

    private static void writeSecondCorner(ItemStack stack, BlockPos pos) {
        toolTag(stack).put(TAG_SECOND, writePos(pos));
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = toolTag(stack);
        tag.remove(TAG_FIRST);
        tag.remove(TAG_SECOND);
        tag.remove(TAG_DIMENSION);
    }

    private static String getTargetPath(ItemStack stack) {
        CompoundTag tag = readToolTag(stack);
        if (tag == null) {
            return DEFAULT_TARGET;
        }
        return tag.contains(TAG_TARGET) ? tag.getString(TAG_TARGET) : DEFAULT_TARGET;
    }

    private static void setTargetPath(ItemStack stack, String path) {
        toolTag(stack).putString(TAG_TARGET, path == null || path.isBlank() ? DEFAULT_TARGET : path.trim());
    }

    private static Direction getFacing(ItemStack stack) {
        CompoundTag tag = readToolTag(stack);
        if (tag == null || !tag.contains(TAG_FACING)) {
            return Direction.NORTH;
        }
        return Direction.from3DDataValue(tag.getInt(TAG_FACING));
    }

    private static void setFacing(ItemStack stack, Direction facing) {
        toolTag(stack).putInt(TAG_FACING, facing.get3DDataValue());
    }

    private record Selection(Optional<BlockPos> first, Optional<BlockPos> second, @Nullable ResourceLocation dimension) {
        boolean hasFirst() {
            return first.isPresent() && dimension != null;
        }

        boolean hasSecond() {
            return second.isPresent();
        }

        boolean isComplete() {
            return hasFirst() && hasSecond();
        }
    }

    private record PredicateKey(String key, boolean created) {
    }

    private record PatternBuildResult(boolean success, @Nullable CompoundTag patternTag, int createdPredicates, Component message) {
        static PatternBuildResult success(CompoundTag patternTag, int createdPredicates) {
            return new PatternBuildResult(true, patternTag, createdPredicates, Component.empty());
        }

        static PatternBuildResult failure(Component message) {
            return new PatternBuildResult(false, null, 0, message);
        }

        public CompoundTag patternTag() {
            if (patternTag == null) {
                throw new IllegalStateException("Missing pattern tag");
            }
            return patternTag;
        }
    }

    private record ExportResult(Component message) {
        static ExportResult success(Component message) {
            return new ExportResult(message);
        }

        static ExportResult failure(Component message) {
            return new ExportResult(message);
        }
    }
}
