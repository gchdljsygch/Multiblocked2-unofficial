package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.lowdragmc.mbd2.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Serializer for fluid recipe content.
 *
 * <p>The business goal is to normalize fluid stack and ingredient definitions
 * across JSON, network sync, scripts, and editor builders. The singleton is
 * stateless and thread-safe; returned {@link FluidIngredient} instances remain
 * mutable and should be confined to the recipe-processing or editor thread that
 * owns them.</p>
 */
public class SerializerFluidIngredient implements IContentSerializer<FluidIngredient> {

    public static SerializerFluidIngredient INSTANCE = new SerializerFluidIngredient();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerFluidIngredient() {}

    /**
     * Writes a fluid ingredient to the network buffer.
     *
     * @param buf     destination buffer positioned for this ingredient
     * @param content ingredient to encode; must not be {@code null}
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, FluidIngredient content) {
        content.toNetwork(buf);
    }

    /**
     * Reads a fluid ingredient from the network buffer.
     *
     * @param buf source buffer positioned at a value written by
     *            {@link #toNetwork(FriendlyByteBuf, FluidIngredient)}
     * @return decoded fluid ingredient
     */
    @Override
    public FluidIngredient fromNetwork(FriendlyByteBuf buf) {
        return FluidIngredient.fromNetwork(buf);
    }

    /**
     * Parses a fluid ingredient from recipe JSON.
     *
     * @param json JSON object containing an amount and one or more fluid/tag
     *            values
     * @return decoded fluid ingredient
     */
    @Override
    public FluidIngredient fromJson(JsonElement json) {
        return FluidIngredient.fromJson(json);
    }

    /**
     * Converts a fluid ingredient to recipe JSON.
     *
     * @param content ingredient to encode
     * @return JSON form accepted by {@link #fromJson(JsonElement)}
     */
    @Override
    public JsonElement toJson(FluidIngredient content) {
        return content.toJson();
    }

    /**
     * Converts builder or script input into a fluid ingredient.
     *
     * <p>Supported inputs are {@link FluidIngredient} and LowDragLib
     * {@link FluidStack}. Stack inputs are copied so later stack mutations do not
     * affect the returned ingredient.</p>
     *
     * @param o source object from builder, editor, or script code
     * @return normalized fluid ingredient, or {@link FluidIngredient#EMPTY} for
     * unsupported input
     */
    @Override
    public FluidIngredient of(Object o) {
        if (o instanceof FluidIngredient ingredient) {
            return ingredient;
        }
        if (o instanceof FluidStack stack) {
            return FluidIngredient.of(stack.copy());
        }
        return FluidIngredient.EMPTY;
    }

    /**
     * Copies a fluid ingredient for recipe matching.
     *
     * @param content source ingredient
     * @return copied ingredient including copied NBT
     */
    @Override
    public FluidIngredient copyInner(FluidIngredient content) {
        return content.copy();
    }

    /**
     * Copies a fluid ingredient and applies a numeric modifier to its amount.
     *
     * <p>Side effects: none on the source ingredient. Empty ingredients are
     * copied without applying the modifier. Non-empty amounts are truncated to
     * {@code int}, matching the current fluid recipe handling path.</p>
     *
     * @param content  source ingredient
     * @param modifier amount transform to apply
     * @return copied ingredient with the modified amount
     */
    @Override
    public FluidIngredient copyWithModifier(FluidIngredient content, ContentModifier modifier) {
        if (content.isEmpty()) return content.copy();
        FluidIngredient copy = content.copy();
        copy.setAmount(modifier.apply(copy.getAmount()).intValue());
        return copy;
    }

    /**
     * Deep-copies a fluid ingredient.
     *
     * @param content source ingredient
     * @return copied ingredient including copied NBT
     */
    @Override
    public FluidIngredient deepCopyInner(FluidIngredient content) {
        return content.copy();
    }
}
