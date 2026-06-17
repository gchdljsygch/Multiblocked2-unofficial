package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.mbd2.api.recipe.ingredient.EntityIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Serializer for entity recipe content.
 *
 * <p>The business goal is to normalize entity type and entity-instance inputs
 * across JSON, network sync, scripts, and editor builders. The singleton is
 * stateless and thread-safe; returned {@link EntityIngredient} instances are
 * mutable and should be owned by one recipe-processing or editor thread at a
 * time.</p>
 */
public class SerializerEntityIngredient implements IContentSerializer<EntityIngredient> {

    public static SerializerEntityIngredient INSTANCE = new SerializerEntityIngredient();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerEntityIngredient() {}

    /**
     * Writes an entity ingredient to the network buffer.
     *
     * @param buf     destination buffer positioned for this ingredient
     * @param content ingredient to encode; must not be {@code null}
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, EntityIngredient content) {
        content.toNetwork(buf);
    }

    /**
     * Reads an entity ingredient from the network buffer.
     *
     * @param buf source buffer positioned at a value written by
     *            {@link #toNetwork(FriendlyByteBuf, EntityIngredient)}
     * @return decoded entity ingredient
     */
    @Override
    public EntityIngredient fromNetwork(FriendlyByteBuf buf) {
        return EntityIngredient.fromNetwork(buf);
    }

    /**
     * Parses an entity ingredient from recipe JSON.
     *
     * @param json JSON object containing a count and one or more entity type/tag
     *            values
     * @return decoded entity ingredient
     */
    @Override
    public EntityIngredient fromJson(JsonElement json) {
        return EntityIngredient.fromJson(json);
    }

    /**
     * Converts an entity ingredient to recipe JSON.
     *
     * @param content ingredient to encode
     * @return JSON form accepted by {@link #fromJson(JsonElement)}
     */
    @Override
    public JsonElement toJson(EntityIngredient content) {
        return content.toJson();
    }

    /**
     * Converts builder or script input into an entity ingredient.
     *
     * <p>Supported inputs are {@link EntityIngredient}, {@link EntityType}, and
     * live {@link Entity} instances. Single type/entity inputs require one
     * matching entity.</p>
     *
     * @param o source object from builder, editor, or script code
     * @return normalized entity ingredient, or {@link EntityIngredient#EMPTY} for
     * unsupported input
     */
    @Override
    public EntityIngredient of(Object o) {
        if (o instanceof EntityIngredient ingredient) {
            return ingredient;
        }
        if (o instanceof EntityType<?> entityType) {
            return EntityIngredient.of(1, entityType);
        }
        if (o instanceof Entity entity) {
            return EntityIngredient.of(1, entity.getType());
        }
        return EntityIngredient.EMPTY;
    }

    /**
     * Copies an entity ingredient for recipe matching.
     *
     * @param content source ingredient
     * @return copied ingredient including copied NBT
     */
    @Override
    public EntityIngredient copyInner(EntityIngredient content) {
        return content.copy();
    }

    /**
     * Copies an entity ingredient and applies a numeric modifier to its count.
     *
     * <p>Side effects: none on the source ingredient. Empty ingredients are
     * copied without applying the modifier. Non-empty counts are truncated to
     * {@code int}; callers should keep modifiers in a range that produces a
     * meaningful required entity count.</p>
     *
     * @param content  source ingredient
     * @param modifier count transform to apply
     * @return copied ingredient with the modified count
     */
    @Override
    public EntityIngredient copyWithModifier(EntityIngredient content, ContentModifier modifier) {
        if (content.isEmpty()) return content.copy();
        EntityIngredient copy = content.copy();
        copy.setCount(modifier.apply(copy.getCount()).intValue());
        return copy;
    }

    /**
     * Deep-copies an entity ingredient.
     *
     * @param content source ingredient
     * @return copied ingredient including copied NBT
     */
    @Override
    public EntityIngredient deepCopyInner(EntityIngredient content) {
        return content.copy();
    }
}
