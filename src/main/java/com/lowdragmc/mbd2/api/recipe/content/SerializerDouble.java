package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Serializer for double recipe content.
 *
 * <p>Conversion from unsupported objects falls back to {@code 0d}; string
 * parsing falls back to {@code 1d} when the string is not numeric.</p>
 */
public class SerializerDouble implements IContentSerializer<Double> {

    public static SerializerDouble INSTANCE = new SerializerDouble();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerDouble() {
    }

    /**
     * Writes the value as a network double.
     *
     * @param buf     destination buffer
     * @param content double value
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, Double content) {
        buf.writeDouble(content);
    }

    /**
     * Reads a network double.
     *
     * @param buf source buffer
     * @return decoded double
     */
    @Override
    public Double fromNetwork(FriendlyByteBuf buf) {
        return buf.readDouble();
    }

    /**
     * Converts the value to an NBT double tag.
     *
     * @param content double value
     * @return double tag
     */
    @Override
    public Tag toNBT(Double content) {
        return DoubleTag.valueOf(content);
    }

    /**
     * Reads a double from NBT.
     *
     * @param nbt source tag
     * @return tag value, or {@code 0d} when the tag is not a {@link DoubleTag}
     */
    @Override
    public Double fromNBT(Tag nbt) {
        if (nbt instanceof DoubleTag doubleTag) {
            return doubleTag.getAsDouble();
        }
        return 0d;
    }

    /**
     * Reads a double from JSON.
     *
     * @param json numeric JSON element
     * @return decoded double
     */
    @Override
    public Double fromJson(JsonElement json) {
        return json.getAsDouble();
    }

    /**
     * Writes a double to JSON.
     *
     * @param content double value
     * @return JSON primitive
     */
    @Override
    public JsonElement toJson(Double content) {
        return new JsonPrimitive(content);
    }

    /**
     * Converts builder/script input to a double.
     *
     * @param o double, number, or numeric string
     * @return converted value; unsupported input returns {@code 0d}
     */
    @Override
    public Double of(Object o) {
        if (o instanceof Double) {
            return (Double) o;
        } else if (o instanceof Number) {
            return ((Number) o).doubleValue();
        } else if (o instanceof CharSequence) {
            return NumberUtils.toDouble(o.toString(), 1);
        }
        return 0d;
    }

    /**
     * Applies a content modifier and returns the result as double.
     *
     * @param content  source value
     * @param modifier modifier to apply
     * @return modified double
     */
    @Override
    public Double copyWithModifier(Double content, ContentModifier modifier) {
        return modifier.apply(content).doubleValue();
    }

    /**
     * Returns the immutable double value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Double copyInner(Double content) {
        return content;
    }

    /**
     * Returns the immutable double value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Double deepCopyInner(Double content) {
        return content;
    }
}
