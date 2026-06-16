package com.lowdragmc.mbd2.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.utils.NBTToJsonConverter;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Serializer for MBD recipes across datapack JSON, network sync, and project
 * NBT formats.
 *
 * <p>The business goal is to keep one recipe model usable by Minecraft's recipe
 * manager, editor/project persistence, and client sync. Methods are stateless
 * except for reads from MBD registries and Minecraft registries; callers should
 * invoke them after recipe capabilities, conditions, and recipe types are
 * registered.</p>
 */
public class MBDRecipeSerializer implements RecipeSerializer<MBDRecipe> {

    /**
     * Shared serializer instance registered with Forge.
     */
    public static final MBDRecipeSerializer SERIALIZER = new MBDRecipeSerializer();

    /**
     * Reads capability content maps from a JSON object.
     *
     * <p>Preconditions: each key should be a registered recipe capability name
     * and each value should be an array accepted by that capability's content
     * serializer. Unknown capability keys are ignored. Side effects: reads the
     * MBD recipe capability registry.</p>
     *
     * @param json object keyed by capability registry name
     * @return mutable map from capability to decoded content list
     */
    public Map<RecipeCapability<?>, List<Content>> capabilitiesFromJson(JsonObject json) {
        Map<RecipeCapability<?>, List<Content>> capabilities = new HashMap<>();
        for (String key : json.keySet()) {
            JsonArray contentsJson = json.getAsJsonArray(key);
            RecipeCapability<?> capability = MBDRegistries.RECIPE_CAPABILITIES.get(key);
            if (capability != null) {
                List<Content> contents = new ArrayList<>();
                for (JsonElement contentJson : contentsJson) {
                    contents.add(capability.serializer.fromJsonContent(contentJson));
                }
                capabilities.put(capability, contents);
            }
        }
        return capabilities;
    }

    /**
     * Decodes a datapack recipe JSON object into an {@link MBDRecipe}.
     *
     * <p>Required field: {@code type}. Optional fields include duration, data,
     * inputs, outputs, recipeConditions, isFuel, isXEIHidden, priority, and
     * recipeGroup. Side effects: reads built-in recipe type registry and MBD
     * condition/capability registries.</p>
     *
     * @param id   recipe id assigned by Minecraft's recipe manager
     * @param json source JSON object
     * @return decoded recipe
     */
    @Override
    public @NotNull MBDRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
        String recipeType = GsonHelper.getAsString(json, "type");
        int duration = json.has("duration") ? GsonHelper.getAsInt(json, "duration") : 100;
        CompoundTag data = new CompoundTag();
        if (json.has("data"))
            data = CraftingHelper.getNBT(json.get("data"));
        Map<RecipeCapability<?>, List<Content>> inputs = capabilitiesFromJson(json.has("inputs") ? json.getAsJsonObject("inputs") : new JsonObject());
        Map<RecipeCapability<?>, List<Content>> outputs = capabilitiesFromJson(json.has("outputs") ? json.getAsJsonObject("outputs") : new JsonObject());
        List<RecipeCondition> conditions = new ArrayList<>();
        JsonArray conditionsJson = json.has("recipeConditions") ? json.getAsJsonArray("recipeConditions") : new JsonArray();
        for (JsonElement jsonElement : conditionsJson) {
            if (jsonElement instanceof JsonObject jsonObject) {
                var conditionKey = GsonHelper.getAsString(jsonObject, "type", "");
                var clazz = MBDRegistries.RECIPE_CONDITIONS.get(conditionKey);
                if (clazz != null) {
                    RecipeCondition condition = RecipeCondition.create(clazz);
                    if (condition != null) {
                        conditions.add(condition.deserialize(GsonHelper.getAsJsonObject(jsonObject, "data", new JsonObject())));
                    }
                }
            }
        }
        boolean isFuel = GsonHelper.getAsBoolean(json, "isFuel", false);
        boolean isXEIHidden = GsonHelper.getAsBoolean(json, "isXEIHidden", false);
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        String recipeGroup = json.has("recipeGroup") ? RecipeGroup.normalize(GsonHelper.getAsString(json, "recipeGroup")) : null;
        return new MBDRecipe((MBDRecipeType) BuiltInRegistries.RECIPE_TYPE.get(new ResourceLocation(recipeType)), id, inputs, outputs, conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
    }

    /**
     * Encodes capability content maps to JSON.
     *
     * <p>Preconditions: every capability must have a registered key and every
     * content object must be accepted by that capability's JSON serializer. Side
     * effects: none beyond registry key lookup.</p>
     *
     * @param contents capability content map
     * @return JSON object keyed by capability registry name
     */
    public JsonObject capabilitiesToJson(Map<RecipeCapability<?>, List<Content>> contents) {
        JsonObject jsonObject = new JsonObject();
        contents.forEach((cap, list) -> {
            JsonArray contentsJson = new JsonArray();
            for (Content content : list) {
                contentsJson.add(cap.serializer.toJsonContent(content));
            }
            jsonObject.add(MBDRegistries.RECIPE_CAPABILITIES.getKey(cap), contentsJson);
        });
        return jsonObject;
    }

    /**
     * Encodes a recipe to datapack-style JSON.
     *
     * <p>Side effects: none. The duration is written as an absolute value to
     * preserve existing serializer behavior.</p>
     *
     * @param recipe recipe to encode
     * @return JSON representation containing only non-default optional fields
     */
    public JsonObject toJson(@NotNull MBDRecipe recipe) {
        JsonObject json = new JsonObject();
        json.addProperty("type", recipe.recipeType.getRegistryName().toString());
        json.addProperty("duration", Math.abs(recipe.duration));
        if (recipe.data != null && !recipe.data.isEmpty()) {
            json.add("data", NBTToJsonConverter.getObject(recipe.data));
        }
        json.add("inputs", capabilitiesToJson(recipe.inputs));
        json.add("outputs", capabilitiesToJson(recipe.outputs));
        if (!recipe.conditions.isEmpty()) {
            JsonArray array = new JsonArray();
            for (RecipeCondition condition : recipe.conditions) {
                JsonObject cond = new JsonObject();
                cond.addProperty("type", MBDRegistries.RECIPE_CONDITIONS.getKey(condition.getClass()));
                cond.add("data", condition.serialize());
                array.add(cond);
            }
            json.add("recipeConditions", array);
        }
        if (recipe.isFuel) {
            json.addProperty("isFuel", true);
        }
        if (recipe.isXEIHidden) {
            json.addProperty("isXEIHidden", true);
        }
        if (recipe.priority != 0) {
            json.addProperty("priority", recipe.priority);
        }
        if (RecipeGroup.hasGroup(recipe.recipeGroup)) {
            json.addProperty("recipeGroup", RecipeGroup.normalize(recipe.recipeGroup));
        }
        return json;
    }

    /**
     * Reads one capability content entry from a network buffer.
     *
     * <p>Preconditions: the buffer must contain a capability key written by
     * {@link #entryWriter(FriendlyByteBuf, Map.Entry)} followed by that
     * capability's content collection. Side effects: advances the buffer read
     * index.</p>
     *
     * @param buf source buffer
     * @return tuple of capability and decoded content list
     */
    public static Tuple<RecipeCapability<?>, List<Content>> entryReader(FriendlyByteBuf buf) {
        RecipeCapability<?> capability = MBDRegistries.RECIPE_CAPABILITIES.get(buf.readUtf());
        List<Content> contents = buf.readList(capability.serializer::fromNetworkContent);
        return new Tuple<>(capability, contents);
    }

    /**
     * Writes one capability content entry to a network buffer.
     *
     * @param buf   destination buffer
     * @param entry capability and content list to encode
     */
    public static void entryWriter(FriendlyByteBuf buf, Map.Entry<RecipeCapability<?>, ? extends List<Content>> entry) {
        RecipeCapability<?> capability = entry.getKey();
        List<Content> contents = entry.getValue();
        buf.writeUtf(MBDRegistries.RECIPE_CAPABILITIES.getKey(capability));
        buf.writeCollection(contents, capability.serializer::toNetworkContent);
    }

    /**
     * Reads one recipe condition from a network buffer.
     *
     * <p>Preconditions: the condition type must be registered and expose a
     * no-argument constructor. Side effects: advances the buffer read index and
     * mutates the newly created condition during deserialization.</p>
     *
     * @param buf source buffer
     * @return decoded condition
     */
    public static RecipeCondition conditionReader(FriendlyByteBuf buf) {
        RecipeCondition condition = RecipeCondition.create(MBDRegistries.RECIPE_CONDITIONS.get(buf.readUtf()));
        return condition.fromNetwork(buf);
    }

    /**
     * Writes one recipe condition to a network buffer.
     *
     * @param buf       destination buffer
     * @param condition configured condition to encode
     */
    public static void conditionWriter(FriendlyByteBuf buf, RecipeCondition condition) {
        buf.writeUtf(MBDRegistries.RECIPE_CONDITIONS.getKey(condition.getClass()));
        condition.toNetwork(buf);
    }

    /**
     * Converts decoded network tuples into a capability map.
     *
     * @param entries decoded entries; later duplicate capabilities replace
     *                earlier ones
     * @return mutable map keyed by capability
     */
    public static Map<RecipeCapability<?>, List<Content>> tuplesToMap(List<Tuple<RecipeCapability<?>, List<Content>>> entries) {
        Map<RecipeCapability<?>, List<Content>> map = new HashMap<>();
        entries.forEach(entry -> map.put(entry.getA(), entry.getB()));
        return map;
    }

    /**
     * Decodes a recipe from the Forge recipe-sync network buffer.
     *
     * <p>Preconditions: fields must be in the order written by
     * {@link #toNetwork(FriendlyByteBuf, MBDRecipe)}. Side effects: advances the
     * buffer read index and reads recipe type/capability/condition registries.</p>
     *
     * @param id  recipe id supplied by the recipe sync layer
     * @param buf source buffer
     * @return decoded recipe
     */
    @Override
    @NotNull
    public MBDRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buf) {
        String recipeType = buf.readUtf();
        int duration = buf.readVarInt();
        Map<RecipeCapability<?>, List<Content>> inputs = tuplesToMap(buf.readCollection(c -> new ArrayList<>(), MBDRecipeSerializer::entryReader));
        Map<RecipeCapability<?>, List<Content>> outputs = tuplesToMap(buf.readCollection(c -> new ArrayList<>(), MBDRecipeSerializer::entryReader));
        List<RecipeCondition> conditions = buf.readCollection(c -> new ArrayList<>(), MBDRecipeSerializer::conditionReader);
        CompoundTag data = buf.readNbt();
        boolean isFuel = buf.readBoolean();
        boolean isXEIHidden = buf.readBoolean();
        int priority = buf.readVarInt();
        String recipeGroup = RecipeGroup.normalizeOptional(buf.readUtf());
        return new MBDRecipe((MBDRecipeType) BuiltInRegistries.RECIPE_TYPE.get(new ResourceLocation(recipeType)), id, inputs, outputs, conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
    }

    /**
     * Encodes a recipe for Forge recipe-sync networking.
     *
     * <p>Side effects: appends bytes to the destination buffer. The recipe group
     * is encoded as an empty string when absent.</p>
     *
     * @param buf    destination buffer
     * @param recipe recipe to encode
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, MBDRecipe recipe) {
        buf.writeUtf(recipe.recipeType == null ? "dummy" : recipe.recipeType.toString());
        buf.writeVarInt(recipe.duration);
        buf.writeCollection(recipe.inputs.entrySet(), MBDRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.outputs.entrySet(), MBDRecipeSerializer::entryWriter);
        buf.writeCollection(recipe.conditions, MBDRecipeSerializer::conditionWriter);
        buf.writeNbt(recipe.data);
        buf.writeBoolean(recipe.isFuel);
        buf.writeBoolean(recipe.isXEIHidden);
        buf.writeVarInt(recipe.priority);
        buf.writeUtf(recipe.recipeGroup == null ? "" : RecipeGroup.normalize(recipe.recipeGroup));
    }

    /**
     * Reads capability content maps from project NBT.
     *
     * <p>Preconditions: each key should be a registered capability name and each
     * value should be a list of compound tags. Unknown capabilities are ignored.</p>
     *
     * @param nbt source tag keyed by capability name
     * @return mutable capability content map
     */
    public Map<RecipeCapability<?>, List<Content>> capabilitiesFromNBT(CompoundTag nbt) {
        Map<RecipeCapability<?>, List<Content>> capabilities = new HashMap<>();
        for (String key : nbt.getAllKeys()) {
            List<Content> contents = new ArrayList<>();
            RecipeCapability<?> capability = MBDRegistries.RECIPE_CAPABILITIES.get(key);
            if (capability != null) {
                for (var tag : nbt.getList(key, Tag.TAG_COMPOUND)) {
                    contents.add(capability.serializer.fromNBT((CompoundTag) tag));
                }
                capabilities.put(capability, contents);
            }
        }
        return capabilities;
    }

    /**
     * Decodes a recipe from project/editor NBT.
     *
     * <p>Required keys mirror {@link #toNBT(MBDRecipe)}. Side effects: reads
     * recipe type, capability, and condition registries.</p>
     *
     * @param id  recipe id assigned by the project loader
     * @param nbt source recipe tag
     * @return decoded recipe
     */
    public MBDRecipe fromNBT(@NotNull ResourceLocation id, @NotNull CompoundTag nbt) {
        String recipeType = nbt.getString("type");
        int duration = nbt.getInt("duration");
        Map<RecipeCapability<?>, List<Content>> inputs = capabilitiesFromNBT(nbt.getCompound("inputs"));
        Map<RecipeCapability<?>, List<Content>> outputs = capabilitiesFromNBT(nbt.getCompound("outputs"));
        List<RecipeCondition> conditions = new ArrayList<>();
        for (var tag : nbt.getList("recipeConditions", Tag.TAG_COMPOUND)) {
            CompoundTag conditionTag = (CompoundTag) tag;
            RecipeCondition condition = RecipeCondition.create(MBDRegistries.RECIPE_CONDITIONS.get(conditionTag.getString("type")));
            if (condition != null) {
                conditions.add(condition.fromNBT(conditionTag.getCompound("data")));
            }
        }
        CompoundTag data = nbt.getCompound("data");
        boolean isFuel = nbt.getBoolean("isFuel");
        boolean isXEIHidden = nbt.getBoolean("isXEIHidden");
        int priority = nbt.getInt("priority");
        String recipeGroup = nbt.contains("recipeGroup", Tag.TAG_STRING) ? RecipeGroup.normalize(nbt.getString("recipeGroup")) : null;
        return new MBDRecipe((MBDRecipeType) BuiltInRegistries.RECIPE_TYPE.get(new ResourceLocation(recipeType)), id, inputs, outputs, conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
    }

    /**
     * Encodes capability content maps to project NBT.
     *
     * @param contents capability content map to encode
     * @return compound tag keyed by capability internal name
     */
    public CompoundTag capabilitiesToNBT(Map<RecipeCapability<?>, List<Content>> contents) {
        CompoundTag tag = new CompoundTag();
        contents.forEach((cap, list) -> {
            ListTag contentsTag = new ListTag();
            for (Content content : list) {
                contentsTag.add(cap.serializer.toNBT(content));
            }
            tag.put(cap.name, contentsTag);
        });
        return tag;
    }

    /**
     * Encodes a recipe to project/editor NBT.
     *
     * <p>Side effects: none; returned tag contains nested copies produced by
     * capability and condition serializers.</p>
     *
     * @param recipe recipe to encode
     * @return compound tag suitable for project persistence
     */
    public CompoundTag toNBT(@NotNull MBDRecipe recipe) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", recipe.recipeType.toString());
        nbt.putInt("duration", recipe.duration);
        nbt.put("inputs", capabilitiesToNBT(recipe.inputs));
        nbt.put("outputs", capabilitiesToNBT(recipe.outputs));
        ListTag conditions = new ListTag();
        for (RecipeCondition condition : recipe.conditions) {
            CompoundTag conditionTag = new CompoundTag();
            conditionTag.putString("type", MBDRegistries.RECIPE_CONDITIONS.getKey(condition.getClass()));
            conditionTag.put("data", condition.toNBT());
            conditions.add(conditionTag);
        }
        nbt.put("recipeConditions", conditions);
        nbt.put("data", recipe.data);
        nbt.putBoolean("isFuel", recipe.isFuel);
        nbt.putBoolean("isXEIHidden", recipe.isXEIHidden);
        nbt.putInt("priority", recipe.priority);
        if (RecipeGroup.hasGroup(recipe.recipeGroup)) {
            nbt.putString("recipeGroup", RecipeGroup.normalize(recipe.recipeGroup));
        }
        return nbt;
    }
}
