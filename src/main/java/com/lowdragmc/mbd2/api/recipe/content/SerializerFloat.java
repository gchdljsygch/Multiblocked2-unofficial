package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Serializer for float recipe content.
 *
 * <p>Conversion from unsupported objects falls back to {@code 0f}; string
 * parsing falls back to {@code 1f} when the string is not numeric.</p>
 */
public class SerializerFloat implements IContentSerializer<Float> {

    public static SerializerFloat INSTANCE = new SerializerFloat();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerFloat() {
    }

    /**
     * Writes the value as a network float.
     *
     * @param buf     destination buffer
     * @param content float value
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, Float content) {
        buf.writeFloat(content);
    }

    /**
     * Reads a network float.
     *
     * @param buf source buffer
     * @return decoded float
     */
    @Override
    public Float fromNetwork(FriendlyByteBuf buf) {
        return buf.readFloat();
    }

    /**
     * Converts the value to an NBT float tag.
     *
     * @param content float value
     * @return float tag
     */
    @Override
    public Tag toNBT(Float content) {
        return FloatTag.valueOf(content);
    }

    /**
     * Reads a float from NBT.
     *
     * @param nbt source tag
     * @return tag value, or {@code 0f} when the tag is not a {@link FloatTag}
     */
    @Override
    public Float fromNBT(Tag nbt) {
        if (nbt instanceof FloatTag floatTag) {
            return floatTag.getAsFloat();
        }
        return 0f;
    }

    /**
     * Reads a float from JSON.
     *
     * @param json numeric JSON element
     * @return decoded float
     */
    @Override
    public Float fromJson(JsonElement json) {
        return json.getAsFloat();
    }

    /**
     * Writes a float to JSON.
     *
     * @param content float value
     * @return JSON primitive
     */
    @Override
    public JsonElement toJson(Float content) {
        return new JsonPrimitive(content);
    }

    /**
     * Converts builder/script input to a float.
     *
     * @param o float, number, or numeric string
     * @return converted value; unsupported input returns {@code 0f}
     */
    @Override
    public Float of(Object o) {
        if (o instanceof Float) {
            return (Float) o;
        } else if (o instanceof Number) {
            return ((Number) o).floatValue();
        } else if (o instanceof CharSequence) {
            return NumberUtils.toFloat(o.toString(), 1);
        }
        return 0f;
    }

    /**
     * Applies a content modifier and returns the result as float.
     *
     * @param content  source value
     * @param modifier modifier to apply
     * @return modified float
     */
    @Override
    public Float copyWithModifier(Float content, ContentModifier modifier) {
        return modifier.apply(content).floatValue();
    }

    /**
     * Returns the immutable float value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Float copyInner(Float content) {
        return content;
    }

    /**
     * Returns the immutable float value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Float deepCopyInner(Float content) {
        return content;
    }
}
