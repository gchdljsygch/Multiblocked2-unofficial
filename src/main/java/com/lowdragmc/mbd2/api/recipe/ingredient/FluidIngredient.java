package com.lowdragmc.mbd2.api.recipe.ingredient;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Predicate for matching fluid recipe content by explicit fluids, fluid tags,
 * amount, and optional NBT.
 *
 * <p>The business goal is to provide an item-ingredient-like abstraction for
 * fluids so recipe definitions, network sync, and editor previews can share one
 * representation. Instances are mutable and cache resolved stacks lazily; they
 * are not thread-safe while {@link #setAmount(long)} or
 * {@link #setNbt(CompoundTag)} is called concurrently with readers.</p>
 */
public class FluidIngredient implements Predicate<FluidStack> {
    public static final FluidIngredient EMPTY = new FluidIngredient(Stream.empty(), 0, null);
    public Value[] values;
    @Nullable
    public FluidStack[] stacks;
    @Getter
    private long amount;
    @Getter
    @Nullable
    private CompoundTag nbt;
    private boolean changed = true;

    /**
     * Creates a fluid ingredient from resolved value providers.
     *
     * @param empty stream of fluid or tag values accepted by this ingredient
     * @param amount required amount in LowDragLib fluid units
     * @param nbt optional NBT that candidate fluid stacks must match exactly
     */
    public FluidIngredient(Stream<? extends Value> empty, long amount, @Nullable CompoundTag nbt) {
        this.values = empty.toArray(Value[]::new);
        this.amount = amount;
        this.nbt = nbt;
    }

    /**
     * Creates a fluid ingredient or returns {@link #EMPTY} when no values remain.
     *
     * @param stream stream of fluid or tag values
     * @param amount required amount in LowDragLib fluid units
     * @param nbt optional exact-match NBT
     * @return non-empty ingredient, or the shared empty ingredient
     */
    public static FluidIngredient fromValues(Stream<? extends Value> stream, long amount, @Nullable CompoundTag nbt) {
        FluidIngredient ingredient = new FluidIngredient(stream, amount, nbt);
        return ingredient.isEmpty() ? EMPTY : ingredient;
    }

    /**
     * Writes this ingredient to a network buffer.
     *
     * <p>Network format is the resolved stack collection, followed by amount and
     * optional NBT. Side effects: may resolve and cache stacks through
     * {@link #getStacks()}.</p>
     *
     * @param buffer destination buffer
     */
    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeCollection(Arrays.asList(this.getStacks()), (buf, stack) -> stack.writeToBuf(buf));
        buffer.writeVarLong(amount);
        buffer.writeNbt(nbt);
    }

    /**
     * Converts this ingredient to recipe JSON.
     *
     * @return object containing {@code amount}, optional {@code nbt}, and a
     * {@code value} array of fluid/tag entries
     */
    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("amount", this.amount * FluidHelper.getBucket() / 1000);
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
    public FluidIngredient copy() {
        return new FluidIngredient(Arrays.stream(this.values).map(Value::copy), this.amount, this.nbt == null ? null : this.nbt.copy());
    }

    /**
     * Copies this ingredient with a replacement amount.
     *
     * @param amount required amount for the copy
     * @return new ingredient with copied values and copied NBT
     */
    public FluidIngredient copy(long amount) {
        return new FluidIngredient(Arrays.stream(this.values).map(Value::copy), amount, this.nbt == null ? null : this.nbt.copy());
    }
    
    /**
     * Tests whether a fluid stack matches this ingredient.
     *
     * <p>The amount is intentionally not checked here; recipe handlers use
     * {@link #getAmount()} when consuming or displaying the required fluid
     * quantity.</p>
     *
     * @param stack candidate stack; {@code null} never matches
     * @return {@code true} when the fluid type is accepted and optional NBT
     * matches exactly
     */
    @Override
    public boolean test(@Nullable FluidStack stack) {
        if (stack == null) {
            return false;
        }
        if (this.isEmpty()) {
            return stack.isEmpty();
        }
        if (this.nbt != null && !this.nbt.equals(stack.getTag())) {
            return false;
        }
        for (FluidStack fluidStack : this.getStacks()) {
            if (fluidStack.getFluid() != stack.getFluid()) continue;
            return true;
        }
        return false;
    }
    
    /**
     * Returns whether this ingredient has no accepted fluid values.
     *
     * @return {@code true} when no fluid or tag values were configured
     */
    public boolean isEmpty() {
        return this.values.length == 0;
    }

    /**
     * Resolves accepted fluid values to display/recipe stacks.
     *
     * <p>Side effects: rebuilds and caches the stack array when the ingredient was
     * changed. The returned array is the cached array and should be treated as
     * read-only.</p>
     *
     * @return distinct fluid stacks using this ingredient's amount and NBT
     */
    public FluidStack[] getStacks() {
        if (changed || this.stacks == null) {
            this.stacks = Arrays.stream(this.values).flatMap(entry -> entry.getStacks().stream()).distinct().map(fluid -> FluidStack.create(fluid, this.amount, this.nbt)).toArray(FluidStack[]::new);
            this.changed = false;
        }
        return this.stacks;
    }

    /**
     * Replaces the required fluid amount.
     *
     * <p>Side effects: invalidates the resolved stack cache.</p>
     *
     * @param amount required amount in LowDragLib fluid units
     */
    public void setAmount(long amount) {
        this.amount = amount;
        this.changed = true;
    }

    /**
     * Replaces the NBT condition used for exact stack matching.
     *
     * <p>Side effects: invalidates the resolved stack cache. The tag reference is
     * stored directly; callers should pass a defensive copy when later mutation is
     * possible.</p>
     *
     * @param nbt optional NBT tag, or {@code null} for no NBT restriction
     */
    public void setNbt(CompoundTag nbt) {
        this.nbt = nbt;
        this.changed = true;
    }

    /**
     * Returns the shared empty fluid ingredient.
     *
     * @return empty ingredient that matches empty stacks
     */
    public static FluidIngredient of() {
        return EMPTY;
    }

    /**
     * Creates an ingredient from explicit fluids.
     *
     * @param amount required amount in LowDragLib fluid units
     * @param items fluids accepted by the ingredient; empty fluids are ignored
     * @return new ingredient, or {@link #EMPTY} when no valid fluids are supplied
     */
    public static FluidIngredient of(long amount, Fluid... items) {
        return FluidIngredient.of(Arrays.stream(items), amount, null);
    }

    /**
     * Creates an ingredient from concrete fluid stacks.
     *
     * <p>The first stack supplies the required amount and NBT; all stacks supply
     * accepted fluid types.</p>
     *
     * @param stacks fluid stacks to accept
     * @return new ingredient, or {@link #EMPTY} for an empty array
     */
    public static FluidIngredient of(FluidStack... stacks) {
        return FluidIngredient.of(Arrays.stream(stacks).map(FluidStack::getFluid), stacks.length == 0 ? 0 : stacks[0].getAmount(), stacks.length == 0 ? null : stacks[0].getTag());
    }

    /**
     * Creates an ingredient from a fluid stream.
     *
     * @param stacks fluid stream; {@code null} and empty fluids are ignored
     * @param amount required amount in LowDragLib fluid units
     * @param nbt optional exact-match NBT
     * @return new ingredient, or {@link #EMPTY} when the filtered stream is empty
     */
    public static FluidIngredient of(Stream<Fluid> stacks, long amount, CompoundTag nbt) {
        return FluidIngredient.fromValues(stacks.filter(stack -> stack != null && !stack.isSame(Fluids.EMPTY)).map(FluidValue::new), amount, nbt);
    }

    /**
     * Creates an ingredient that accepts fluids in a tag.
     *
     * @param tag fluid tag accepted by the ingredient
     * @param amount required amount in LowDragLib fluid units
     * @return new tagged fluid ingredient
     */
    public static FluidIngredient of(TagKey<Fluid> tag, long amount) {
        return FluidIngredient.fromValues(Stream.of(new TagValue(tag)), amount, null);
    }

    /**
     * Creates an ingredient that accepts fluids in a tag and requires NBT.
     *
     * @param tag fluid tag accepted by the ingredient
     * @param amount required amount in LowDragLib fluid units
     * @param nbt optional exact-match NBT
     * @return new tagged fluid ingredient
     */
    public static FluidIngredient of(TagKey<Fluid> tag, long amount, CompoundTag nbt) {
        return FluidIngredient.fromValues(Stream.of(new TagValue(tag)), amount, nbt);
    }

    /**
     * Reads a fluid ingredient from a network buffer.
     *
     * @param buffer source buffer positioned at {@link #toNetwork(FriendlyByteBuf)}
     * output
     * @return decoded ingredient
     */
    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        return FluidIngredient.fromValues(buffer.readList(FluidStack::readFromBuf).stream().map(stack -> new FluidValue(stack.getFluid())), buffer.readVarLong(), buffer.readNbt());
    }

    /**
     * Parses a fluid ingredient from JSON.
     *
     * @param json object containing {@code amount}, optional {@code nbt}, and
     *             {@code value}
     * @return decoded ingredient
     * @throws JsonSyntaxException when the JSON is null or not an object
     * @throws JsonParseException when a value entry is invalid
     */
    public static FluidIngredient fromJson(@Nullable JsonElement json) {
        return FluidIngredient.fromJson(json, true);
    }

    /**
     * Parses a fluid ingredient from JSON with explicit empty-array handling.
     *
     * @param json object containing {@code amount}, optional {@code nbt}, and
     *             {@code value}
     * @param allowAir {@code true} to allow an empty value array as an empty
     *                 ingredient
     * @return decoded ingredient
     * @throws JsonSyntaxException when required structure is missing or an empty
     * array is disallowed
     * @throws JsonParseException when a value entry is invalid
     */
    public static FluidIngredient fromJson(@Nullable JsonElement json, boolean allowAir) {
        if (json == null || json.isJsonNull()) {
            throw new JsonSyntaxException("Fluid ingredient cannot be null");
        }
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected fluid ingredient to be object");
        }
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "ingredient");
        long amount = GsonHelper.getAsLong(jsonObject, "amount", 0);
        CompoundTag nbt = jsonObject.has("nbt") ? CraftingHelper.getNBT(jsonObject.get("nbt")) : null;
        if (GsonHelper.isObjectNode(jsonObject, "value")) {
            return FluidIngredient.fromValues(Stream.of(FluidIngredient.valueFromJson(GsonHelper.getAsJsonObject(jsonObject, "value"))), amount, nbt);
        } else if (GsonHelper.isArrayNode(jsonObject, "value")) {
            JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "value");
            if (jsonArray.size() == 0 && !allowAir) {
                throw new JsonSyntaxException("Fluid array cannot be empty, at least one item must be defined");
            }
            return FluidIngredient.fromValues(StreamSupport.stream(jsonArray.spliterator(), false).map(jsonElement -> FluidIngredient.valueFromJson(GsonHelper.convertToJsonObject(jsonElement, "fluid"))), amount, nbt);
        }
        throw new JsonSyntaxException("expected value to be either object or array.");
    }

    private static Value valueFromJson(JsonObject json) {
        if (json.has("fluid") && json.has("tag")) {
            throw new JsonParseException("A fluid ingredient entry is either a tag or a fluid, not both");
        }
        if (json.has("fluid")) {
            Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(GsonHelper.getAsString(json, "fluid")));
            return new FluidValue(fluid);
        }
        if (json.has("tag")) {
            ResourceLocation resourceLocation = new ResourceLocation(GsonHelper.getAsString(json, "tag"));
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, resourceLocation);
            return new TagValue(tagKey);
        }
        throw new JsonParseException("A fluid ingredient entry needs either a tag or a fluid");
    }

    /**
     * Resolvable component of a fluid ingredient.
     */
    public interface Value {
        /**
         * Resolves this value to concrete fluids.
         *
         * @return collection of fluids represented by the value; may be empty
         * when a tag has no members
         */
        Collection<Fluid> getStacks();

        /**
         * Converts this value to JSON.
         *
         * @return object containing either {@code fluid} or {@code tag}
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
     * Fluid value resolved from a fluid tag.
     */
    public static class TagValue
            implements Value {
        @Getter @Setter
        private TagKey<Fluid> tag;

        /**
         * Creates a tag-backed fluid value.
         *
         * @param tag fluid tag to resolve through the built-in registry
         */
        public TagValue(TagKey<Fluid> tag) {
            this.tag = tag;
        }

        /**
         * Resolves all fluids currently assigned to the tag.
         *
         * @return fluids in the tag, or an empty collection when the tag is empty
         */
        @Override
        public Collection<Fluid> getStacks() {
            ArrayList<Fluid> list = Lists.newArrayList();
            for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(this.tag)) {
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
     * Fluid value resolved from a single concrete fluid.
     */
    public static class FluidValue implements Value {
        @Getter @Setter
        private Fluid fluid;

        /**
         * Creates a concrete fluid value.
         *
         * @param item fluid accepted by the ingredient
         */
        public FluidValue(Fluid item) {
            this.fluid = item;
        }

        /**
         * Returns the single configured fluid.
         *
         * @return singleton collection containing the configured fluid
         */
        @Override
        public Collection<Fluid> getStacks() {
            return Collections.singleton(this.fluid);
        }

        /**
         * Converts this fluid value to JSON.
         *
         * @return object containing the fluid registry id
         */
        @Override
        public JsonObject serialize() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("fluid", BuiltInRegistries.FLUID.getKey(this.fluid).toString());
            return jsonObject;
        }

        /**
         * Copies this fluid value.
         *
         * @return new value referencing the same fluid
         */
        @Override
        public Value copy() {
            return new FluidValue(this.fluid);
        }
    }
}
