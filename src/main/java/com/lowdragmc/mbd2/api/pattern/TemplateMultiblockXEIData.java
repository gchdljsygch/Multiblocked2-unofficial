package com.lowdragmc.mbd2.api.pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Common, serializable data for a template multiblock XEI page.
 *
 * <p>This class intentionally contains no client Widget code. Server data-pack
 * reloads parse it, serialize it into a network packet, and clients turn it
 * into a {@link TemplateMultiblockXEI} entry.</p>
 */
public final class TemplateMultiblockXEIData {
    public static final String DATA_DIRECTORY = "mbd2_xei";
    private static final String TYPE = "template";

    private final ResourceLocation id;
    private final String title;
    private final List<String> description;
    private final ItemStack workstation;
    private final List<List<ItemStack>> inputs;
    private final List<StructureBlock> structure;

    public TemplateMultiblockXEIData(ResourceLocation id, String title, List<String> description,
                                     ItemStack workstation, List<List<ItemStack>> inputs) {
        this(id, title, description, workstation, inputs, List.of());
    }

    public TemplateMultiblockXEIData(ResourceLocation id, String title, List<String> description,
                                     ItemStack workstation, List<List<ItemStack>> inputs,
                                     List<StructureBlock> structure) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title == null || title.isBlank() ? id.toString() : title;
        this.description = copyDescription(description);
        this.workstation = workstation == null ? ItemStack.EMPTY : workstation.copy();
        this.inputs = copyInputs(inputs);
        this.structure = copyStructure(structure);
    }

    /**
     * Parses one data-pack JSON entry.
     *
     * <p>The file path supplies the entry id. This prevents a JSON file from
     * silently registering under a different id after a pack override.</p>
     *
     * @param id   resource id derived from the data-pack file path
     * @param json raw template JSON object
     * @return parsed template data
     */
    public static TemplateMultiblockXEIData fromJson(ResourceLocation id, JsonElement json) {
        if (id == null || json == null || !json.isJsonObject()) {
            throw new JsonParseException("Template XEI entry must be a JSON object: " + id);
        }
        JsonObject object = json.getAsJsonObject();
        String type = GsonHelper.getAsString(object, "type", TYPE);
        if (!TYPE.equals(type)) {
            throw new JsonParseException("Unsupported template XEI type '" + type + "' in " + id);
        }

        String title = GsonHelper.getAsString(object, "title", id.toString());
        List<String> description = parseDescription(id, object);
        ItemStack workstation = object.has("workstation") && !object.get("workstation").isJsonNull()
                ? parseStack(object.get("workstation"), id)
                : ItemStack.EMPTY;
        List<List<ItemStack>> inputs = parseInputs(id, object);
        List<StructureBlock> structure = parseStructure(id, object);
        return new TemplateMultiblockXEIData(id, title, description, workstation, inputs, structure);
    }

    /**
     * Restores a packet entry from NBT.
     *
     * @param tag serialized template data
     * @return restored template data
     */
    public static TemplateMultiblockXEIData fromNBT(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) {
            throw new IllegalArgumentException("Invalid template XEI id in packet");
        }

        List<String> description = new ArrayList<>();
        ListTag descriptionTag = tag.getList("description", Tag.TAG_STRING);
        for (int i = 0; i < descriptionTag.size(); i++) {
            description.add(descriptionTag.getString(i));
        }

        ItemStack workstation = tag.contains("workstation", Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("workstation"))
                : ItemStack.EMPTY;
        List<List<ItemStack>> inputs = new ArrayList<>();
        ListTag inputGroups = tag.getList("inputs", Tag.TAG_LIST);
        for (int i = 0; i < inputGroups.size(); i++) {
            ListTag candidates = inputGroups.getList(i);
            List<ItemStack> group = new ArrayList<>();
            for (int j = 0; j < candidates.size(); j++) {
                ItemStack stack = ItemStack.of(candidates.getCompound(j));
                if (!stack.isEmpty()) {
                    group.add(stack);
                }
            }
            if (!group.isEmpty()) {
                inputs.add(group);
            }
        }
        List<StructureBlock> structure = new ArrayList<>();
        ListTag structureTag = tag.getList("structure", Tag.TAG_COMPOUND);
        for (int i = 0; i < structureTag.size(); i++) {
            structure.add(StructureBlock.fromNBT(structureTag.getCompound(i)));
        }
        return new TemplateMultiblockXEIData(id, tag.getString("title"), description, workstation, inputs, structure);
    }

    /**
     * Serializes this data for server-to-client synchronization.
     *
     * @return independent NBT payload
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("title", title);

        ListTag descriptionTag = new ListTag();
        for (String line : description) {
            descriptionTag.add(StringTag.valueOf(line));
        }
        tag.put("description", descriptionTag);

        if (!workstation.isEmpty()) {
            tag.put("workstation", workstation.save(new CompoundTag()));
        }

        ListTag inputGroups = new ListTag();
        for (List<ItemStack> group : inputs) {
            ListTag candidates = new ListTag();
            for (ItemStack stack : group) {
                if (!stack.isEmpty()) {
                    candidates.add(stack.save(new CompoundTag()));
                }
            }
            if (!candidates.isEmpty()) {
                inputGroups.add(candidates);
            }
        }
        tag.put("inputs", inputGroups);

        if (!structure.isEmpty()) {
            ListTag structureTag = new ListTag();
            for (StructureBlock block : structure) {
                structureTag.add(block.toNBT());
            }
            tag.put("structure", structureTag);
        }
        return tag;
    }

    /**
     * Serializes this entry to the JSON format consumed by
     * {@link #fromJson(ResourceLocation, JsonElement)}.
     *
     * <p>The method is useful for tools that generate an external XEI data
     * pack. Keeping the writer beside the parser prevents generated entries
     * from drifting away from the documented data-pack schema.</p>
     *
     * @return independent JSON object representing this template entry
     */
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", TYPE);
        object.addProperty("title", title);

        if (!description.isEmpty()) {
            JsonArray descriptionArray = new JsonArray();
            description.forEach(descriptionArray::add);
            object.add("description", descriptionArray);
        }
        if (!workstation.isEmpty()) {
            object.add("workstation", stackToJson(workstation));
        }
        if (!inputs.isEmpty()) {
            JsonArray inputGroups = new JsonArray();
            for (List<ItemStack> group : inputs) {
                JsonArray candidates = new JsonArray();
                for (ItemStack stack : group) {
                    if (!stack.isEmpty()) {
                        candidates.add(stackToJson(stack));
                    }
                }
                if (candidates.size() > 0) {
                    inputGroups.add(candidates);
                }
            }
            if (inputGroups.size() > 0) {
                object.add("inputs", inputGroups);
            }
        }
        if (!structure.isEmpty()) {
            JsonArray structureArray = new JsonArray();
            structure.forEach(block -> structureArray.add(block.toJson()));
            object.add("structure", structureArray);
        }
        return object;
    }

    @NotNull
    public ResourceLocation id() {
        return id;
    }

    @NotNull
    public String title() {
        return title;
    }

    @NotNull
    public List<String> description() {
        return description;
    }

    @NotNull
    public ItemStack workstation() {
        return workstation.copy();
    }

    @NotNull
    public List<List<ItemStack>> inputs() {
        return copyInputs(inputs);
    }

    /**
     * @return copied relative structure blocks for the client scene preview
     */
    @NotNull
    public List<StructureBlock> structure() {
        return copyStructure(structure);
    }

    private static List<String> parseDescription(ResourceLocation id, JsonObject object) {
        if (!object.has("description")) {
            return List.of();
        }
        JsonArray array = GsonHelper.getAsJsonArray(object, "description");
        List<String> description = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Description entries must be strings in " + id);
            }
            description.add(element.getAsString());
        }
        return description;
    }

    private static List<List<ItemStack>> parseInputs(ResourceLocation id, JsonObject object) {
        if (!object.has("inputs")) {
            return List.of();
        }
        JsonArray groups = GsonHelper.getAsJsonArray(object, "inputs");
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (JsonElement groupElement : groups) {
            if (!groupElement.isJsonArray()) {
                throw new JsonParseException("Input groups must be arrays in " + id);
            }
            List<ItemStack> candidates = new ArrayList<>();
            for (JsonElement candidate : groupElement.getAsJsonArray()) {
                ItemStack stack = parseStack(candidate, id);
                if (!stack.isEmpty()) {
                    candidates.add(stack);
                }
            }
            if (!candidates.isEmpty()) {
                inputs.add(candidates);
            }
        }
        return inputs;
    }

    private static List<StructureBlock> parseStructure(ResourceLocation id, JsonObject object) {
        if (!object.has("structure")) {
            return List.of();
        }
        JsonArray blocks = GsonHelper.getAsJsonArray(object, "structure");
        List<StructureBlock> structure = new ArrayList<>();
        Set<BlockPos> positions = new HashSet<>();
        for (JsonElement blockElement : blocks) {
            if (!blockElement.isJsonObject()) {
                throw new JsonParseException("Structure entries must be objects in " + id);
            }
            JsonObject blockObject = blockElement.getAsJsonObject();
            JsonArray position = GsonHelper.getAsJsonArray(blockObject, "pos");
            boolean validPosition = position.size() == 3;
            for (JsonElement element : position) {
                validPosition &= element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
            }
            if (!validPosition) {
                throw new JsonParseException("Structure positions must be [x, y, z] numbers in " + id);
            }
            BlockPos blockPos = new BlockPos(position.get(0).getAsInt(), position.get(1).getAsInt(),
                    position.get(2).getAsInt());
            if (!positions.add(blockPos)) {
                throw new JsonParseException("Duplicate structure position " + blockPos + " in " + id);
            }

            String blockSpec = GsonHelper.getAsString(blockObject, "block");
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), blockSpec, false)
                        .blockState();
            } catch (CommandSyntaxException exception) {
                throw new JsonParseException("Invalid block state '" + blockSpec + "' in " + id, exception);
            }
            if (state.isAir()) {
                throw new JsonParseException("Air cannot be used as a structure block in " + id);
            }

            CompoundTag blockEntityTag = null;
            String nbt = GsonHelper.getAsString(blockObject, "nbt", null);
            if (nbt != null && !nbt.isBlank()) {
                try {
                    blockEntityTag = TagParser.parseTag(nbt);
                } catch (CommandSyntaxException exception) {
                    throw new JsonParseException("Invalid structure block NBT in " + id, exception);
                }
            }
            structure.add(new StructureBlock(blockPos, state, blockEntityTag));
        }
        return structure;
    }

    private static ItemStack parseStack(JsonElement element, ResourceLocation source) {
        String itemId;
        int count = 1;
        String nbt = null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            itemId = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            itemId = GsonHelper.getAsString(object, "item");
            count = GsonHelper.getAsInt(object, "count", 1);
            nbt = GsonHelper.getAsString(object, "nbt", null);
        } else {
            throw new JsonParseException("Item entries must be strings or objects in " + source);
        }

        ResourceLocation location = ResourceLocation.tryParse(itemId);
        Item item = location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
        if (item == Items.AIR) {
            throw new JsonParseException("Unknown item '" + itemId + "' in " + source);
        }

        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (nbt != null && !nbt.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(nbt));
            } catch (CommandSyntaxException exception) {
                throw new JsonParseException("Invalid item NBT in " + source, exception);
            }
        }
        return stack;
    }

    private static List<String> copyDescription(List<String> description) {
        if (description == null || description.isEmpty()) {
            return List.of();
        }
        return description.stream()
                .filter(line -> line != null && !line.isBlank())
                .toList();
    }

    private static List<List<ItemStack>> copyInputs(List<List<ItemStack>> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<List<ItemStack>> copied = new ArrayList<>();
        for (List<ItemStack> group : inputs) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            List<ItemStack> copiedGroup = group.stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            if (!copiedGroup.isEmpty()) {
                copied.add(copiedGroup);
            }
        }
        return copied.stream().map(List::copyOf).toList();
    }

    private static List<StructureBlock> copyStructure(List<StructureBlock> structure) {
        if (structure == null || structure.isEmpty()) {
            return List.of();
        }
        return structure.stream()
                .filter(block -> block != null)
                .map(StructureBlock::copy)
                .toList();
    }

    private static JsonObject stackToJson(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            throw new IllegalArgumentException("Cannot serialize an unregistered item stack");
        }
        JsonObject object = new JsonObject();
        object.addProperty("item", itemId.toString());
        if (stack.getCount() != 1) {
            object.addProperty("count", stack.getCount());
        }
        if (stack.getTag() != null) {
            object.addProperty("nbt", stack.getTag().toString());
        }
        return object;
    }

    private static String blockStateToString(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            throw new IllegalArgumentException("Cannot serialize an unregistered block state");
        }
        if (state.getValues().isEmpty()) {
            return blockId.toString();
        }

        List<Property<?>> properties = new ArrayList<>(state.getValues().keySet());
        properties.sort(Comparator.comparing(Property::getName));
        StringBuilder builder = new StringBuilder(blockId.toString()).append('[');
        for (int i = 0; i < properties.size(); i++) {
            Property<?> property = properties.get(i);
            builder.append(property.getName()).append('=').append(propertyValueToString(state, property));
            if (i + 1 < properties.size()) {
                builder.append(',');
            }
        }
        return builder.append(']').toString();
    }

    private static <T extends Comparable<T>> String propertyValueToString(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /**
     * One relative block in a template structure preview.
     */
    public static final class StructureBlock {
        private final BlockPos pos;
        private final BlockState state;
        @Nullable
        private final CompoundTag blockEntityTag;

        public StructureBlock(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityTag) {
            this.pos = Objects.requireNonNull(pos, "pos").immutable();
            this.state = Objects.requireNonNull(state, "state");
            this.blockEntityTag = blockEntityTag == null ? null : blockEntityTag.copy();
        }

        @NotNull
        public BlockPos pos() {
            return pos;
        }

        @NotNull
        public BlockState state() {
            return state;
        }

        @Nullable
        public CompoundTag blockEntityTag() {
            return blockEntityTag == null ? null : blockEntityTag.copy();
        }

        private StructureBlock copy() {
            return new StructureBlock(pos, state, blockEntityTag);
        }

        private CompoundTag toNBT() {
            CompoundTag tag = NbtUtils.writeBlockState(state);
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            if (blockEntityTag != null) {
                tag.put("nbt", blockEntityTag.copy());
            }
            return tag;
        }

        private JsonObject toJson() {
            JsonObject tag = new JsonObject();
            JsonArray position = new JsonArray();
            position.add(pos.getX());
            position.add(pos.getY());
            position.add(pos.getZ());
            tag.add("pos", position);
            tag.addProperty("block", blockStateToString(state));
            if (blockEntityTag != null) {
                tag.addProperty("nbt", blockEntityTag.toString());
            }
            return tag;
        }

        private static StructureBlock fromNBT(CompoundTag tag) {
            if (!tag.contains("pos", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Structure block is missing its position");
            }
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
            CompoundTag blockEntityTag = tag.contains("nbt", Tag.TAG_COMPOUND)
                    ? tag.getCompound("nbt") : null;
            return new StructureBlock(NbtUtils.readBlockPos(tag.getCompound("pos")), state, blockEntityTag);
        }
    }
}
