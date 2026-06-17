package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.lowdragmc.mbd2.api.recipe.ingredient.SizedIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/**
 * Serializer for item recipe content.
 *
 * <p>The business goal is to normalize item inputs from JSON, network packets,
 * scripts, and editor builders into Minecraft {@link Ingredient} instances while
 * preserving stack counts through {@link SizedIngredient} when possible. This
 * singleton is stateless and thread-safe as long as caller-owned ingredient
 * instances are not mutated concurrently.</p>
 */
public class SerializerIngredient implements IContentSerializer<Ingredient> {

    public static SerializerIngredient INSTANCE = new SerializerIngredient();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerIngredient() {}

    /**
     * Writes the ingredient using Minecraft's ingredient network format.
     *
     * @param buf     destination buffer positioned for this ingredient
     * @param content ingredient to encode; must not be {@code null}
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, Ingredient content) {
        content.toNetwork(buf);
    }

    /**
     * Reads an ingredient from Minecraft's ingredient network format.
     *
     * @param buf source buffer positioned at a value written by
     *            {@link #toNetwork(FriendlyByteBuf, Ingredient)}
     * @return decoded ingredient
     */
    @Override
    public Ingredient fromNetwork(FriendlyByteBuf buf) {
        return Ingredient.fromNetwork(buf);
    }

    /**
     * Parses an item ingredient from recipe JSON.
     *
     * @param json vanilla or Forge ingredient JSON
     * @return decoded ingredient; may be a {@link SizedIngredient} when the JSON
     * declares the MBD sized ingredient type
     */
    @Override
    public Ingredient fromJson(JsonElement json) {
        return Ingredient.fromJson(json);
    }

    /**
     * Converts an item ingredient to recipe JSON.
     *
     * @param content ingredient to encode
     * @return JSON form accepted by {@link #fromJson(JsonElement)}
     */
    @Override
    public JsonElement toJson(Ingredient content) {
        return content.toJson();
    }

    /**
     * Converts builder or script input into an item ingredient.
     *
     * <p>Supported inputs are {@link Ingredient}, {@link ItemStack},
     * {@link ItemLike}, and item {@link TagKey}. An {@link ItemStack} keeps its
     * count and strict NBT/damage matching through {@link SizedIngredient}.</p>
     *
     * @param o source object from builder, editor, or script code
     * @return normalized ingredient, or {@link Ingredient#EMPTY} for unsupported
     * input
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Ingredient of(Object o) {
        if (o instanceof Ingredient ingredient) {
            return ingredient;
        } else if (o instanceof ItemStack itemStack) {
            return SizedIngredient.create(itemStack);
        } else if (o instanceof ItemLike itemLike) {
            return Ingredient.of(itemLike);
        } else if (o instanceof TagKey tag) {
            return Ingredient.of(tag);
        }
        return Ingredient.EMPTY;
    }

    /**
     * Copies an ingredient for recipe matching.
     *
     * @param content source ingredient
     * @return copied sized ingredient wrapper; vanilla ingredients are wrapped
     * with amount {@code 1}
     */
    @Override
    public Ingredient copyInner(Ingredient content) {
        return SizedIngredient.copy(content);
    }

    /**
     * Copies an ingredient and applies a numeric modifier to its stack count.
     *
     * <p>Side effects: none on the source ingredient. The resulting amount is the
     * modifier result truncated to {@code int}; callers should keep modifiers in
     * a range that produces a positive recipe amount.</p>
     *
     * @param content  source ingredient
     * @param modifier amount transform to apply
     * @return sized ingredient with the modified amount
     */
    @Override
    public Ingredient copyWithModifier(Ingredient content, ContentModifier modifier) {
        return content instanceof SizedIngredient sizedIngredient ? SizedIngredient.create(sizedIngredient.getInner(), modifier.apply(sizedIngredient.getAmount()).intValue()) : SizedIngredient.create(content, modifier.apply(1).intValue());
    }

    /**
     * Deep-copies an ingredient through JSON serialization.
     *
     * @param content source ingredient
     * @return copied sized ingredient whose inner ingredient no longer shares the
     * source JSON object graph
     */
    @Override
    public Ingredient deepCopyInner(Ingredient content) {
        return SizedIngredient.deepCopy(content);
    }
}
