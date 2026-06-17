package com.lowdragmc.mbd2.api.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.mbd2.MBD2;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Minecraft item ingredient with an explicit stack amount.
 *
 * <p>The business goal is to let MBD recipes express "any item matching this
 * vanilla ingredient, but in this count" while remaining compatible with
 * Forge/Minecraft ingredient JSON and network serializers. Instances cache their
 * display stacks lazily and are not thread-safe while
 * {@link #updateInnerIngredient(Ingredient)} is changing the inner ingredient.</p>
 */
public class SizedIngredient extends Ingredient {
    public static final ResourceLocation TYPE = MBD2.id("sized");

    @Getter
    protected final int amount;
    @Getter
    protected Ingredient inner;
    protected ItemStack[] itemStacks = null;

    /**
     * Creates a sized wrapper around an existing ingredient.
     *
     * @param inner  ingredient predicate to delegate matching to
     * @param amount stack count represented in previews and recipe amounts
     */
    protected SizedIngredient(Ingredient inner, int amount) {
        super(Stream.empty());
        this.amount = amount;
        this.inner = inner;
    }

    /**
     * Creates a sized ingredient from an item tag.
     *
     * @param tag    item tag accepted by this ingredient
     * @param amount stack count represented by the ingredient
     */
    protected SizedIngredient(@NotNull TagKey<Item> tag, int amount) {
        this(Ingredient.of(tag), amount);
    }

    /**
     * Creates a sized ingredient from a concrete item stack.
     *
     * <p>Stacks with NBT or damage use strict matching; plain stacks use vanilla
     * item matching. The original stack is not retained.</p>
     *
     * @param itemStack stack whose item, optional NBT/damage, and count define the
     *                  ingredient
     */
    protected SizedIngredient(ItemStack itemStack) {
        this((itemStack.hasTag() || itemStack.getDamageValue() > 0) ? StrictNBTIngredient.of(itemStack) : Ingredient.of(itemStack), itemStack.getCount());
    }

    /**
     * Creates a sized ingredient from a concrete item stack.
     *
     * @param inner stack whose count becomes the ingredient amount
     * @return new sized ingredient
     */
    public static SizedIngredient create(ItemStack inner) {
        return new SizedIngredient(inner);
    }

    /**
     * Creates a sized ingredient from an existing predicate and explicit count.
     *
     * @param inner  ingredient predicate to delegate matching to
     * @param amount stack count represented by the ingredient
     * @return new sized ingredient
     */
    public static SizedIngredient create(Ingredient inner, int amount) {
        return new SizedIngredient(inner, amount);
    }

    /**
     * Creates a sized ingredient with amount {@code 1}.
     *
     * @param inner ingredient predicate to delegate matching to
     * @return new sized ingredient
     */
    public static SizedIngredient create(Ingredient inner) {
        return new SizedIngredient(inner, 1);
    }

    /**
     * Creates a sized ingredient from an item tag.
     *
     * @param tag    item tag accepted by this ingredient
     * @param amount stack count represented by the ingredient
     * @return new sized ingredient
     */
    public static SizedIngredient create(TagKey<Item> tag, int amount) {
        return new SizedIngredient(tag, amount);
    }

    /**
     * Copies an ingredient while preserving MBD amount metadata.
     *
     * <p>Side effects: none on the source. If the source has already built its
     * preview stacks, those stacks are copied into the returned instance.</p>
     *
     * @param ingredient source ingredient
     * @return sized copy; vanilla ingredients are wrapped with amount {@code 1}
     */
    public static SizedIngredient copy(Ingredient ingredient) {
        if (ingredient instanceof SizedIngredient sizedIngredient) {
            var copied = SizedIngredient.create(sizedIngredient.inner, sizedIngredient.amount);
            if (sizedIngredient.itemStacks != null) {
                copied.itemStacks = Arrays.stream(sizedIngredient.itemStacks).map(ItemStack::copy).toArray(ItemStack[]::new);
            }
            return copied;
        }
        return SizedIngredient.create(ingredient);
    }

    /**
     * Deep-copies an ingredient through JSON serialization.
     *
     * <p>Use this when a recipe copy must not share mutable inner ingredient
     * state with the source. Invalid source JSON will propagate through
     * {@link Ingredient#fromJson(JsonElement)}.</p>
     *
     * @param ingredient source ingredient
     * @return sized deep copy
     */
    public static SizedIngredient deepCopy(Ingredient ingredient) {
        if (ingredient instanceof SizedIngredient sizedIngredient) {
            var copied = SizedIngredient.create(Ingredient.fromJson(sizedIngredient.inner.toJson()), sizedIngredient.amount);
            if (sizedIngredient.itemStacks != null) {
                copied.itemStacks = Arrays.stream(sizedIngredient.itemStacks).map(ItemStack::copy).toArray(ItemStack[]::new);
            }
            return copied;
        }
        return SizedIngredient.create(Ingredient.fromJson(ingredient.toJson()));
    }

    /**
     * Replaces the underlying ingredient predicate.
     *
     * <p>Side effects: clears the cached preview stacks so the next
     * {@link #getItems()} call reflects the new predicate. This method is not
     * thread-safe with concurrent readers.</p>
     *
     * @param inner replacement ingredient predicate; must not be {@code null}
     */
    public void updateInnerIngredient(@Nonnull Ingredient inner) {
        this.inner = inner;
        this.itemStacks = null;
    }

    /**
     * Returns the Forge serializer for the MBD sized ingredient type.
     *
     * @return singleton serializer registered for {@link #TYPE}
     */
    @Override
    @Nonnull
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return SERIALIZER;
    }

    /**
     * Parses a sized ingredient from its JSON object representation.
     *
     * @param json object containing {@code count} and {@code ingredient}
     * @return decoded sized ingredient
     */
    public static SizedIngredient fromJson(JsonObject json) {
        return SERIALIZER.parse(json);
    }

    /**
     * Converts this ingredient to Forge recipe JSON.
     *
     * @return object containing the MBD ingredient type id, stack count, and inner
     * ingredient JSON
     */
    @Override
    public @NotNull JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE.toString());
        json.addProperty("count", amount);
        json.add("ingredient", inner.toJson());
        return json;
    }

    /**
     * Tests whether a stack matches the wrapped ingredient predicate.
     *
     * <p>The stack count is intentionally not checked here; MBD recipe handlers
     * use {@link #getAmount()} when consuming or displaying the required count.</p>
     *
     * @param stack stack to test; {@code null} never matches unless the inner
     *              ingredient does so
     * @return {@code true} when the inner ingredient accepts the stack
     */
    @Override
    public boolean test(@Nullable ItemStack stack) {
        return inner.test(stack);
    }

    /**
     * Returns preview stacks with the configured amount.
     *
     * <p>Side effects: builds and caches copied preview stacks on first access or
     * after {@link #updateInnerIngredient(Ingredient)}. The returned array is the
     * cached array and should be treated as read-only by callers.</p>
     *
     * @return item stacks accepted by the inner ingredient, each with
     * {@link #getAmount()} as its count
     */
    @Override
    public ItemStack @NotNull [] getItems() {
        if (itemStacks == null)
            itemStacks = Arrays.stream(inner.getItems()).map(i -> {
                ItemStack ic = i.copy();
                ic.setCount(amount);
                return ic;
            }).toArray(ItemStack[]::new);
        return itemStacks;
    }

    /**
     * Returns stacking ids from the inner ingredient.
     *
     * @return ids used by vanilla recipe matching
     */
    @Override
    public @NotNull IntList getStackingIds() {
        return inner.getStackingIds();
    }

    /**
     * Returns whether the wrapped predicate has no acceptable items.
     *
     * @return {@code true} when the inner ingredient is empty
     */
    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    /**
     * Forge serializer for the MBD sized ingredient JSON and network format.
     *
     * <p>Network format is {@code VarInt amount} followed by the inner
     * ingredient. JSON format is an object with {@code count} and
     * {@code ingredient}; invalid inner ingredient JSON is logged and replaced
     * with {@link Ingredient#EMPTY}.</p>
     */
    public static final IIngredientSerializer<SizedIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public @NotNull SizedIngredient parse(FriendlyByteBuf buffer) {
            int amount = buffer.readVarInt();
            return new SizedIngredient(Ingredient.fromNetwork(buffer), amount);
        }

        @Override
        public @NotNull SizedIngredient parse(JsonObject json) {
            int amount = json.get("count").getAsInt();
            Ingredient inner;
            try {
                inner = Ingredient.fromJson(json.get("ingredient"));
            } catch (Exception e) {
                LDLib.LOGGER.error("Failed to parse ingredient from json: " + json, e);
                inner = Ingredient.EMPTY;
            }
            return new SizedIngredient(inner, amount);
        }

        @Override
        public void write(FriendlyByteBuf buffer, SizedIngredient ingredient) {
            buffer.writeVarInt(ingredient.getAmount());
            ingredient.inner.toNetwork(buffer);
        }
    };
}
