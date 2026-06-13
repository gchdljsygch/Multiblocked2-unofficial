package com.lowdragmc.mbd2.common.capability.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.recipe.content.IContentSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerializerRedstoneSignal implements IContentSerializer<RedstoneSignal> {
    public static final SerializerRedstoneSignal INSTANCE = new SerializerRedstoneSignal();
    private static final Pattern INPUT_RANGE = Pattern.compile("^\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)$");

    private SerializerRedstoneSignal() {
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, RedstoneSignal content) {
        buf.writeVarInt(content.strength());
        buf.writeVarInt(content.maxStrength());
        buf.writeVarInt(content.duration());
    }

    @Override
    public RedstoneSignal fromNetwork(FriendlyByteBuf buf) {
        return new RedstoneSignal(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Tag toNBT(RedstoneSignal content) {
        if (!content.isOutput() && content.isExactInput()) {
            return IntTag.valueOf(content.strength());
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("strength", content.strength());
        if (content.isOutput()) {
            tag.putInt("duration", content.duration());
        } else {
            tag.putInt("maxStrength", content.maxStrength());
        }
        return tag;
    }

    @Override
    public RedstoneSignal fromNBT(Tag nbt) {
        if (nbt instanceof IntTag intTag) {
            return RedstoneSignal.input(intTag.getAsInt());
        }
        if (nbt instanceof CompoundTag tag) {
            if (tag.contains("maxStrength") || tag.contains("max") || tag.contains("to")) {
                int minStrength = getAsInt(tag, 0, "minStrength", "min", "from", "strength", "signal");
                int maxStrength = getAsInt(tag, minStrength + 1, "maxStrength", "max", "to");
                return RedstoneSignal.input(minStrength, maxStrength);
            }
            return RedstoneSignal.output(tag.getInt("strength"), tag.getInt("duration"));
        }
        return RedstoneSignal.input(0);
    }

    @Override
    public RedstoneSignal fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return RedstoneSignal.input(0);
        }
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            return primitive.isNumber() ? RedstoneSignal.input(primitive.getAsInt()) : parseString(primitive.getAsString());
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("minStrength") || object.has("min") || object.has("from") ||
                object.has("maxStrength") || object.has("max") || object.has("to")) {
            int minStrength = getAsInt(object, 0, "minStrength", "min", "from", "strength", "signal");
            int maxStrength = getAsInt(object, minStrength + 1, "maxStrength", "max", "to");
            return RedstoneSignal.input(minStrength, maxStrength);
        }
        RedstoneSignal signal = parseSignalValue(object, "strength", "signal");
        if (signal != null && !signal.isOutput()) {
            return signal;
        }
        int strength = getAsInt(object, 0, "strength", "signal");
        int duration = getAsInt(object, 0, "duration", "time", "ticks");
        return new RedstoneSignal(strength, duration);
    }

    @Override
    public JsonElement toJson(RedstoneSignal content) {
        if (!content.isOutput() && content.isExactInput()) {
            return new JsonPrimitive(content.strength());
        }
        if (!content.isOutput()) {
            return new JsonPrimitive(content.inputDisplay());
        }
        JsonObject object = new JsonObject();
        object.addProperty("strength", content.strength());
        object.addProperty("duration", content.duration());
        return object;
    }

    @Override
    public RedstoneSignal of(Object o) {
        if (o instanceof RedstoneSignal signal) {
            return signal;
        } else if (o instanceof Number number) {
            return RedstoneSignal.input(number.intValue());
        } else if (o instanceof JsonElement json) {
            return fromJson(json);
        } else if (o instanceof CharSequence chars) {
            return parseString(chars.toString());
        } else if (o instanceof Map<?, ?> map) {
            return parseMap(map);
        } else if (o instanceof Iterable<?> iterable) {
            return parseIterator(iterable.iterator());
        } else if (o instanceof Object[] array) {
            return parseArray(array);
        } else if (o instanceof int[] array) {
            return array.length > 1 ? RedstoneSignal.output(array[0], array[1]) :
                    array.length == 1 ? RedstoneSignal.input(array[0]) : RedstoneSignal.input(0);
        }
        return RedstoneSignal.input(0);
    }

    @Override
    public RedstoneSignal copyWithModifier(RedstoneSignal content, ContentModifier modifier) {
        return content;
    }

    @Override
    public RedstoneSignal copyInner(RedstoneSignal content) {
        return content;
    }

    @Override
    public RedstoneSignal deepCopyInner(RedstoneSignal content) {
        return content;
    }

    private RedstoneSignal parseString(String text) {
        Matcher matcher = INPUT_RANGE.matcher(text.trim());
        if (matcher.matches()) {
            return RedstoneSignal.input(NumberUtils.toInt(matcher.group(1), 0), NumberUtils.toInt(matcher.group(2), 1));
        }
        String[] parts = text.split("[,: ]+");
        int strength = parts.length > 0 ? NumberUtils.toInt(parts[0], 0) : 0;
        int duration = parts.length > 1 ? NumberUtils.toInt(parts[1], 0) : 0;
        return new RedstoneSignal(strength, duration);
    }

    private RedstoneSignal parseMap(Map<?, ?> map) {
        if (hasAnyKey(map, "minStrength", "min", "from", "maxStrength", "max", "to")) {
            int minStrength = getAsInt(map, 0, "minStrength", "min", "from", "strength", "signal");
            int maxStrength = getAsInt(map, minStrength + 1, "maxStrength", "max", "to");
            return RedstoneSignal.input(minStrength, maxStrength);
        }
        RedstoneSignal signal = parseSignalValue(map, "strength", "signal");
        if (signal != null && !signal.isOutput()) {
            return signal;
        }
        int strength = getAsInt(map, 0, "strength", "signal");
        int duration = getAsInt(map, 0, "duration", "time", "ticks");
        return new RedstoneSignal(strength, duration);
    }

    private RedstoneSignal parseArray(Object[] array) {
        int strength = array.length > 0 && array[0] instanceof Number number ? number.intValue() : 0;
        int duration = array.length > 1 && array[1] instanceof Number number ? number.intValue() : 0;
        return new RedstoneSignal(strength, duration);
    }

    private RedstoneSignal parseIterator(Iterator<?> iterator) {
        Object first = iterator.hasNext() ? iterator.next() : 0;
        Object second = iterator.hasNext() ? iterator.next() : 0;
        int strength = first instanceof Number number ? number.intValue() : NumberUtils.toInt(String.valueOf(first), 0);
        int duration = second instanceof Number number ? number.intValue() : NumberUtils.toInt(String.valueOf(second), 0);
        return new RedstoneSignal(strength, duration);
    }

    private int getAsInt(JsonObject object, int defaultValue, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.get(key).getAsInt();
            }
        }
        return defaultValue;
    }

    private int getAsInt(Map<?, ?> map, int defaultValue, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                return value instanceof Number number ? number.intValue() : NumberUtils.toInt(String.valueOf(value), defaultValue);
            }
        }
        return defaultValue;
    }

    private int getAsInt(CompoundTag tag, int defaultValue, String... keys) {
        for (String key : keys) {
            if (tag.contains(key)) {
                return tag.getInt(key);
            }
        }
        return defaultValue;
    }

    private RedstoneSignal parseSignalValue(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                JsonElement value = object.get(key);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    return parseString(value.getAsString());
                }
            }
        }
        return null;
    }

    private RedstoneSignal parseSignalValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value instanceof CharSequence chars) {
                    return parseString(chars.toString());
                }
            }
        }
        return null;
    }

    private boolean hasAnyKey(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
