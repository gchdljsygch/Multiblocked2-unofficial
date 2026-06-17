package com.lowdragmc.mbd2.api.recipe.ingredient;

import com.google.common.collect.Lists;
import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Predicate for matching entity recipe content by explicit entity types, entity
 * type tags, required count, and optional NBT.
 *
 * <p>The business goal is to let recipes require nearby or supplied entities in
 * the same way item/fluid ingredients require materials. Instances are mutable
 * and cache resolved entity types lazily; they are not thread-safe while
 * {@link #setCount(int)} or {@link #setNbt(CompoundTag)} is called concurrently
 * with readers.</p>
 */
public class EntityIngredient implements Predicate<Collection<Entity>> {
    public static final EntityIngredient EMPTY = new EntityIngredient(Stream.empty(), 0, null);
    public Value[] values;
    @Nullable
    public EntityType<?>[] types;
    @Getter
    private int count;
    @Getter
    @Nullable
    private CompoundTag nbt;
    private boolean changed = true;

    /**
     * Creates an entity ingredient from resolved value providers.
     *
     * @param empty stream of entity type or tag values accepted by this
     *              ingredient
     * @param count minimum number of matching live entities required
     * @param nbt optional NBT subset that matching entities must contain
     */
    public EntityIngredient(Stream<? extends Value> empty, int count, @Nullable CompoundTag nbt) {
        this.values = empty.toArray(Value[]::new);
        this.count = count;
        this.nbt = nbt;
    }

    /**
     * Creates an entity ingredient or returns {@link #EMPTY} when no values
     * remain.
     *
     * @param stream stream of entity type or tag values
     * @param count minimum number of matching live entities required
     * @param nbt optional NBT subset required on matching entities
     * @return non-empty ingredient, or the shared empty ingredient
     */
    public static EntityIngredient fromValues(Stream<? extends Value> stream, int count, @Nullable CompoundTag nbt) {
        EntityIngredient ingredient = new EntityIngredient(stream, count, nbt);
        return ingredient.isEmpty() ? EMPTY : ingredient;
    }

    /**
     * Writes this ingredient to a network buffer.
     *
     * <p>Network format is the resolved entity type id collection, followed by
     * required count and optional NBT. Side effects: may resolve and cache entity
     * types through {@link #getTypes()}.</p>
     *
     * @param buffer destination buffer
     */
    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeCollection(Arrays.asList(this.getTypes()), (buf, entityType) ->
                buf.writeResourceLocation(BuiltInRegistries.ENTITY_TYPE.getKey(entityType)));
        buffer.writeVarInt(count);
        buffer.writeNbt(nbt);
    }

    /**
     * Converts this ingredient to recipe JSON.
     *
     * @return object containing {@code count}, optional {@code nbt}, and a
     * {@code value} array of entity type/tag entries
     */
    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("count", this.count);
        if (this.nbt != null) {
            jsonObject.addProperty("nbt", this.nbt.getAsString());
        }
        if (this.values.length == 1) {
            jsonObject.add("value", this.values[0].serialize());
        }
        JsonArray jsonArray = new JsonArray();
        for (Value value : this.values) {
            jsonArray.add(value.serialize());
        }
        jsonObject.add("value", jsonArray);
        return jsonObject;
    }

    /**
     * Copies this ingredient.
     *
     * @return new ingredient with copied value providers and copied NBT
     */
    public EntityIngredient copy() {
        return new EntityIngredient(Arrays.stream(this.values).map(Value::copy), this.count, this.nbt == null ? null : this.nbt.copy());
    }

    /**
     * Copies this ingredient with a replacement required count.
     *
     * @param count minimum number of matching live entities for the copy
     * @return new ingredient with copied values and copied NBT
     */
    public EntityIngredient copy(int count) {
        return new EntityIngredient(Arrays.stream(this.values).map(Value::copy), count, this.nbt == null ? null : this.nbt.copy());
    }
    
    /**
     * Tests whether a collection contains enough matching live entities.
     *
     * <p>When NBT is configured, it is treated as a subset requirement: the
     * entity's serialized NBT must already contain all entries from this
     * ingredient's tag.</p>
     *
     * @param entities candidate entity collection; {@code null} never matches
     * @return {@code true} when at least {@link #getCount()} live entities match
     * the accepted types and optional NBT requirement
     */
    @Override
    public boolean test(@Nullable Collection<Entity> entities) {
        if (entities == null) {
            return false;
        }
        if (this.isEmpty()) {
            return entities.isEmpty();
        }
        int matches = 0;
        var types = Arrays.stream(getTypes()).collect(Collectors.toSet());
        for (var entity : entities) {
            if (entity.isAlive() && types.contains(entity.getType())) {
                if (nbt != null && !nbt.isEmpty()) {
                    var held = entity.serializeNBT();
                    if (!held.copy().merge(nbt).equals(held)) {
                        continue;
                    }
                }
                matches++;
            }
        }
        return matches >= count;
    }
    
    /**
     * Returns whether this ingredient has no accepted entity values.
     *
     * @return {@code true} when no entity type or tag values were configured
     */
    public boolean isEmpty() {
        return this.values.length == 0;
    }

    /**
     * Resolves accepted values to concrete entity types.
     *
     * <p>Side effects: rebuilds and caches the type array when the ingredient was
     * changed. The returned array is the cached array and should be treated as
     * read-only.</p>
     *
     * @return distinct entity types accepted by this ingredient
     */
    public EntityType<?>[] getTypes() {
        if (changed || this.types == null) {
            this.types = Arrays.stream(this.values).flatMap(entry -> entry.getTypes().stream()).distinct().toArray(EntityType<?>[]::new);
            this.changed = false;
        }
        return this.types;
    }

    /**
     * Replaces the required entity count.
     *
     * <p>Side effects: invalidates the resolved type cache.</p>
     *
     * @param count minimum number of matching live entities required
     */
    public void setCount(int count) {
        this.count = count;
        this.changed = true;
    }

    /**
     * Replaces the NBT subset required on matching entities.
     *
     * <p>Side effects: invalidates the resolved type cache. The tag reference is
     * stored directly; callers should pass a defensive copy when later mutation is
     * possible.</p>
     *
     * @param nbt optional NBT subset, or {@code null} for no NBT restriction
     */
    public void setNbt(CompoundTag nbt) {
        this.nbt = nbt;
        this.changed = true;
    }

    /**
     * Returns the shared empty entity ingredient.
     *
     * @return empty ingredient that matches empty entity collections
     */
    public static EntityIngredient of() {
        return EMPTY;
    }

    /**
     * Creates an ingredient from explicit entity types.
     *
     * @param count minimum number of matching live entities required
     * @param entityTypes entity types accepted by the ingredient
     * @return new ingredient, or {@link #EMPTY} when no types are supplied
     */
    public static EntityIngredient of(int count, EntityType<?>... entityTypes) {
        return EntityIngredient.of(Arrays.stream(entityTypes), count, null);
    }

    /**
     * Creates an ingredient from an entity type stream.
     *
     * @param types entity types accepted by the ingredient
     * @param count minimum number of matching live entities required
     * @param nbt optional NBT subset required on matching entities
     * @return new ingredient, or {@link #EMPTY} when the stream is empty
     */
    public static EntityIngredient of(Stream<EntityType<?>> types, int count, CompoundTag nbt) {
        return EntityIngredient.fromValues(types.map(EntityTypeValue::new), count, nbt);
    }

    /**
     * Creates an ingredient that accepts entity types in a tag.
     *
     * @param tag entity type tag accepted by the ingredient
     * @param count minimum number of matching live entities required
     * @return new tagged entity ingredient
     */
    public static EntityIngredient of(TagKey<EntityType<?>> tag, int count) {
        return EntityIngredient.fromValues(Stream.of(new TagValue(tag)), count, null);
    }

    /**
     * Creates an ingredient that accepts entity types in a tag and requires NBT.
     *
     * @param tag entity type tag accepted by the ingredient
     * @param count minimum number of matching live entities required
     * @param nbt optional NBT subset required on matching entities
     * @return new tagged entity ingredient
     */
    public static EntityIngredient of(TagKey<EntityType<?>> tag, int count, CompoundTag nbt) {
        return EntityIngredient.fromValues(Stream.of(new TagValue(tag)), count, nbt);
    }

    /**
     * Reads an entity ingredient from a network buffer.
     *
     * @param buffer source buffer positioned at {@link #toNetwork(FriendlyByteBuf)}
     * output
     * @return decoded ingredient
     */
    public static EntityIngredient fromNetwork(FriendlyByteBuf buffer) {
        return EntityIngredient.fromValues(buffer.readList(buf -> BuiltInRegistries.ENTITY_TYPE.get(buf.readResourceLocation())).stream()
                .map(EntityTypeValue::new), buffer.readVarInt(), buffer.readNbt());
    }

    /**
     * Parses an entity ingredient from JSON.
     *
     * @param json object containing {@code count}, optional {@code nbt}, and
     *             {@code value}
     * @return decoded ingredient
     * @throws JsonSyntaxException when the JSON is null or not an object
     * @throws JsonParseException when a value entry is invalid
     */
    public static EntityIngredient fromJson(@Nullable JsonElement json) {
        return EntityIngredient.fromJson(json, true);
    }

    /**
     * Parses an entity ingredient from JSON with explicit empty-array handling.
     *
     * @param json object containing {@code count}, optional {@code nbt}, and
     *             {@code value}
     * @param allowAir {@code true} to allow an empty value array as an empty
     *                 ingredient
     * @return decoded ingredient
     * @throws JsonSyntaxException when required structure is missing or an empty
     * array is disallowed
     * @throws JsonParseException when a value entry is invalid
     */
    public static EntityIngredient fromJson(@Nullable JsonElement json, boolean allowAir) {
        if (json == null || json.isJsonNull()) {
            throw new JsonSyntaxException("Entity ingredient cannot be null");
        }
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected entity ingredient to be object");
        }
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "ingredient");
        var count = GsonHelper.getAsInt(jsonObject, "count", 0);
        var nbt = jsonObject.has("nbt") ? CraftingHelper.getNBT(jsonObject.get("nbt")) : null;
        if (GsonHelper.isObjectNode(jsonObject, "value")) {
            return EntityIngredient.fromValues(Stream.of(EntityIngredient.valueFromJson(GsonHelper.getAsJsonObject(jsonObject, "value"))), count, nbt);
        } else if (GsonHelper.isArrayNode(jsonObject, "value")) {
            JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "value");
            if (jsonArray.isEmpty() && !allowAir) {
                throw new JsonSyntaxException("Entity array cannot be empty, at least one item must be defined");
            }
            return EntityIngredient.fromValues(StreamSupport.stream(jsonArray.spliterator(), false).map(jsonElement -> EntityIngredient.valueFromJson(GsonHelper.convertToJsonObject(jsonElement, "entityType"))), count, nbt);
        }
        throw new JsonSyntaxException("expected value to be either object or array.");
    }

    private static Value valueFromJson(JsonObject json) {
        if (json.has("entityType") && json.has("tag")) {
            throw new JsonParseException("A entity ingredient entry is either a tag or a entityType, not both");
        }
        if (json.has("entityType")) {
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(GsonHelper.getAsString(json, "entityType")));
            return new EntityTypeValue(entityType);
        }
        if (json.has("tag")) {
            ResourceLocation resourceLocation = new ResourceLocation(GsonHelper.getAsString(json, "tag"));
            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, resourceLocation);
            return new TagValue(tagKey);
        }
        throw new JsonParseException("A entity ingredient entry needs either a tag or a entityType");
    }

    /**
     * Resolvable component of an entity ingredient.
     */
    public interface Value {
        /**
         * Resolves this value to concrete entity types.
         *
         * @return collection of entity types represented by the value; may be
         * empty when a tag has no members
         */
        Collection<EntityType<?>> getTypes();

        /**
         * Converts this value to JSON.
         *
         * @return object containing either {@code entityType} or {@code tag}
         */
        JsonObject serialize();
        /**
         * Copies this value provider.
         *
         * @return copied value provider
         */
        Value copy();
    }

    /**
     * Entity value resolved from an entity type tag.
     */
    public static class TagValue implements Value {
        @Getter @Setter
        private TagKey<EntityType<?>> tag;

        /**
         * Creates a tag-backed entity type value.
         *
         * @param tag entity type tag to resolve through the built-in registry
         */
        public TagValue(TagKey<EntityType<?>> tag) {
            this.tag = tag;

        }

        /**
         * Resolves all entity types currently assigned to the tag.
         *
         * @return entity types in the tag, or an empty collection when the tag is
         * empty
         */
        @Override
        public Collection<EntityType<?>> getTypes() {
            ArrayList<EntityType<?>> list = Lists.newArrayList();
            for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(this.tag)) {
                list.add(holder.value());
            }
            return list;
        }

        /**
         * Converts this tag value to JSON.
         *
         * @return object containing the tag id
         */
        @Override
        public JsonObject serialize() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("tag", this.tag.location().toString());
            return jsonObject;
        }

        /**
         * Copies this tag value.
         *
         * @return new value referencing the same tag key
         */
        @Override
        public Value copy() {
            return new TagValue(this.tag);
        }
    }

    /**
     * Entity value resolved from a single concrete entity type.
     */
    public static class EntityTypeValue implements Value {
        @Getter @Setter
        private EntityType<?> entityType;

        /**
         * Creates a concrete entity type value.
         *
         * @param item entity type accepted by the ingredient
         */
        public EntityTypeValue(EntityType<?> item) {
            this.entityType = item;
        }

        /**
         * Returns the single configured entity type.
         *
         * @return singleton collection containing the configured entity type
         */
        @Override
        public Collection<EntityType<?>> getTypes() {
            return Collections.singleton(this.entityType);
        }

        /**
         * Converts this entity type value to JSON.
         *
         * @return object containing the entity type registry id
         */
        @Override
        public JsonObject serialize() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("entityType", BuiltInRegistries.ENTITY_TYPE.getKey(this.entityType).toString());
            return jsonObject;
        }

        /**
         * Copies this entity type value.
         *
         * @return new value referencing the same entity type
         */
        @Override
        public Value copy() {
            return new EntityTypeValue(this.entityType);
        }
    }
}
