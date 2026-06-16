package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Serializer for integer recipe content.
 *
 * <p>The business goal is to support compact numeric recipe capabilities and
 * script inputs. Conversion from unsupported objects falls back to {@code 0};
 * string parsing falls back to {@code 1} when the string is not numeric.</p>
 */
public class SerializerInteger implements IContentSerializer<Integer> {

    public static SerializerInteger INSTANCE = new SerializerInteger();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerInteger() {
    }

    /**
     * Writes the integer as a fixed-width network value.
     *
     * @param buf     destination buffer
     * @param content integer value
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, Integer content) {
        buf.writeInt(content);
    }

    /**
     * Reads the integer from a fixed-width network value.
     *
     * @param buf source buffer
     * @return decoded integer
     */
    @Override
    public Integer fromNetwork(FriendlyByteBuf buf) {
        return buf.readInt();
    }

    /**
     * Converts the integer to an NBT int tag.
     *
     * @param content integer value
     * @return int tag
     */
    @Override
    public Tag toNBT(Integer content) {
        return IntTag.valueOf(content);
    }

    /**
     * Reads an integer from NBT.
     *
     * @param nbt source tag
     * @return tag value, or {@code 0} when the tag is not an {@link IntTag}
     */
    @Override
    public Integer fromNBT(Tag nbt) {
        if (nbt instanceof IntTag intTag) {
            return intTag.getAsInt();
        }
        return 0;
    }

    /**
     * Reads an integer from JSON.
     *
     * @param json numeric JSON element
     * @return decoded integer
     */
    @Override
    public Integer fromJson(JsonElement json) {
        return json.getAsInt();
    }

    /**
     * Writes an integer to JSON.
     *
     * @param content integer value
     * @return JSON primitive
     */
    @Override
    public JsonElement toJson(Integer content) {
        return new JsonPrimitive(content);
    }

    /**
     * Converts builder/script input to an integer.
     *
     * @param o integer, number, or numeric string
     * @return converted value; unsupported input returns {@code 0}
     */
    @Override
    public Integer of(Object o) {
        if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof Number) {
            return ((Number) o).intValue();
        } else if (o instanceof CharSequence) {
            return NumberUtils.toInt(o.toString(), 1);
        }
        return 0;
    }

    /**
     * Applies a content modifier and truncates the result to int.
     *
     * @param content  source value
     * @param modifier modifier to apply
     * @return modified integer
     */
    @Override
    public Integer copyWithModifier(Integer content, ContentModifier modifier) {
        return modifier.apply(content).intValue();
    }

    /**
     * Returns the immutable integer value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Integer copyInner(Integer content) {
        return content;
    }

    /**
     * Returns the immutable integer value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Integer deepCopyInner(Integer content) {
        return content;
    }
}
