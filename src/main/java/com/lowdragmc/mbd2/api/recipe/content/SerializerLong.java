package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Serializer for long recipe content.
 *
 * <p>Conversion from unsupported objects falls back to {@code 0L}; string
 * parsing falls back to {@code 1L} when the string is not numeric.</p>
 */
public class SerializerLong implements IContentSerializer<Long> {

    public static SerializerLong INSTANCE = new SerializerLong();

    /**
     * Singleton serializer; use {@link #INSTANCE}.
     */
    private SerializerLong() {
    }

    /**
     * Writes the value as a VarLong.
     *
     * @param buf     destination buffer
     * @param content long value
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf, Long content) {
        buf.writeVarLong(content);
    }

    /**
     * Reads a VarLong value.
     *
     * @param buf source buffer
     * @return decoded long
     */
    @Override
    public Long fromNetwork(FriendlyByteBuf buf) {
        return buf.readVarLong();
    }

    /**
     * Converts the value to an NBT long tag.
     *
     * @param content long value
     * @return long tag
     */
    @Override
    public Tag toNBT(Long content) {
        return LongTag.valueOf(content);
    }

    /**
     * Reads a long from NBT.
     *
     * @param nbt source tag
     * @return tag value, or {@code 0L} when the tag is not a {@link LongTag}
     */
    @Override
    public Long fromNBT(Tag nbt) {
        if (nbt instanceof LongTag longTag) {
            return longTag.getAsLong();
        }
        return 0L;
    }

    /**
     * Reads a long from JSON.
     *
     * @param json numeric JSON element
     * @return decoded long
     */
    @Override
    public Long fromJson(JsonElement json) {
        return json.getAsLong();
    }

    /**
     * Writes a long to JSON.
     *
     * @param content long value
     * @return JSON primitive
     */
    @Override
    public JsonElement toJson(Long content) {
        return new JsonPrimitive(content);
    }

    /**
     * Converts builder/script input to a long.
     *
     * @param o long, number, or numeric string
     * @return converted value; unsupported input returns {@code 0L}
     */
    @Override
    public Long of(Object o) {
        if (o instanceof Long) {
            return (Long) o;
        } else if (o instanceof Number) {
            return ((Number) o).longValue();
        } else if (o instanceof CharSequence) {
            return NumberUtils.toLong(o.toString(), 1);
        }
        return 0L;
    }

    /**
     * Applies a content modifier and truncates the result to long.
     *
     * @param content  source value
     * @param modifier modifier to apply
     * @return modified long
     */
    @Override
    public Long copyWithModifier(Long content, ContentModifier modifier) {
        return modifier.apply(content).longValue();
    }

    /**
     * Returns the immutable long value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Long copyInner(Long content) {
        return content;
    }

    /**
     * Returns the immutable long value.
     *
     * @param content source value
     * @return same value
     */
    @Override
    public Long deepCopyInner(Long content) {
        return content;
    }
}
