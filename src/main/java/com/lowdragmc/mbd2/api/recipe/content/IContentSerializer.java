package com.lowdragmc.mbd2.api.recipe.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.utils.NBTToJsonConverter;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * Converts a capability-specific content value between JSON, NBT, network, and
 * builder/script representations.
 *
 * <p>The business goal is to give {@link RecipeCapability} one consistent
 * serializer for recipe persistence, sync, copying, and KubeJS/editor input.
 * Implementations should keep conversion methods deterministic and side-effect
 * free. Network methods must read values in the same order they write them.</p>
 */
public interface IContentSerializer<T> {

    /**
     * Returns the largest non-negative integral amount that this serializer can
     * represent exactly when an output multiplier selects it.
     *
     * <p>Output ranges are stored as {@code long} bounds, but the primary amount
     * carried by a capability may be narrower (for example an {@code int}) or
     * may be a floating-point value.  The default keeps the historical
     * long-valued behavior; serializers with a narrower or inexact amount type
     * should override this value.  For floating-point serializers this is the
     * largest integer for which every value in the interval {@code [0, max]}
     * remains exact.</p>
     *
     * @return inclusive maximum exactly representable output amount
     */
    default long getMaxOutputAmount() {
        return Long.MAX_VALUE;
    }

    /**
     * Tests whether one non-negative integral output amount is representable
     * without overflow or loss of precision.
     *
     * @param amount candidate output amount
     * @return {@code true} when the serializer can carry the amount exactly
     */
    default boolean supportsOutputAmount(long amount) {
        return amount >= 0 && amount <= getMaxOutputAmount();
    }

    /**
     * Tests whether this content can be multiplied by an output multiplier
     * without overflowing or losing a representable value. The default accepts
     * non-negative factors; serializers with narrower amount limits should
     * override this method.
     *
     * @param content source output content
     * @param multiplier requested non-negative multiplier
     * @return {@code true} when the multiplied content is representable
     */
    default boolean supportsOutputMultiplier(T content, long multiplier) {
        return multiplier >= 0;
    }

    /**
     * Checks a non-negative integral multiplication against an inclusive upper
     * limit without overflowing an intermediate {@code long}.
     */
    static boolean supportsOutputMultiplier(long amount, long multiplier, long maximum) {
        return amount >= 0 && multiplier >= 0 && maximum >= 0
                && (amount == 0 || multiplier <= maximum / amount);
    }

    /**
     * Writes one content value to a network buffer.
     *
     * <p>Default side effects: serializes the JSON form as a UTF string.</p>
     *
     * @param buf     destination buffer
     * @param content value to encode
     */
    default void toNetwork(FriendlyByteBuf buf, T content) {
        buf.writeUtf(LDLib.GSON.toJson(toJson(content)));
    }

    /**
     * Reads one content value from a network buffer.
     *
     * @param buf source buffer positioned at a value written by
     *            {@link #toNetwork(FriendlyByteBuf, Object)}
     * @return decoded content value
     */
    default T fromNetwork(FriendlyByteBuf buf) {
        return fromJson(LDLib.GSON.fromJson(buf.readUtf(), JsonElement.class));
    }

    /**
     * Converts one content value to NBT.
     *
     * @param content value to encode
     * @return NBT representation of the JSON form
     */
    default Tag toNBT(T content) {
        return CraftingHelper.getNBT(toJson(content));
    }

    /**
     * Converts one content value from NBT.
     *
     * @param nbt NBT representation accepted by this serializer
     * @return decoded content value
     */
    default T fromNBT(Tag nbt) {
        return fromJson(NBTToJsonConverter.getObject(nbt));
    }

    /**
     * Parses one content value from JSON.
     *
     * @param json JSON representation
     * @return decoded content value
     */
    T fromJson(JsonElement json);

    /**
     * Converts one content value to JSON.
     *
     * @param content value to encode
     * @return JSON representation
     */
    JsonElement toJson(T content);

    /**
     * Converts arbitrary builder or script input into this content type.
     *
     * @param o source object; supported types are serializer-specific
     * @return normalized content value
     */
    T of(Object o);

    /**
     * deep copy and modify the size attribute for those Content that have the size attribute.
     *
     * @param content  source content value
     * @param modifier modifier applied to amount-like fields
     * @return copied and modified content value
     */
    T copyWithModifier(T content, ContentModifier modifier);

    /**
     * deep copy of this content. recipe need it for searching and such things.
     * The returned content is a new instance but may not be deep copied.
     *
     * @param content source content value
     * @return copied content value
     */
    T copyInner(T content);

    /**
     * deep copy of this content.
     *
     * <p>Default side effects: allocates a temporary buffer, writes the content
     * through {@link #toNetwork(FriendlyByteBuf, Object)}, then reads it back.</p>
     *
     * @param content source content value
     * @return deep-copied content value
     */
    default T deepCopyInner(T content) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        toNetwork(buf, content);
        return fromNetwork(buf);
    }

    /**
     * Writes a {@link Content} wrapper and its inner value to a network buffer.
     *
     * @param buf     destination buffer
     * @param content wrapper to encode
     */
    default void toNetworkContent(FriendlyByteBuf buf, Content content) {
        T inner = (T) content.getContent();
        toNetwork(buf, inner);
        buf.writeBoolean(content.perTick);
        buf.writeFloat(content.chance);
        buf.writeFloat(content.tierChanceBoost);
        buf.writeLong(content.minOutput);
        buf.writeLong(content.maxOutput);
        buf.writeBoolean(!content.slotName.isEmpty());
        if (!content.slotName.isEmpty()) {
            buf.writeUtf(content.slotName);
        }
        buf.writeBoolean(!content.uiName.isEmpty());
        if (!content.uiName.isEmpty()) {
            buf.writeUtf(content.uiName);
        }
    }

    /**
     * Reads a {@link Content} wrapper from a network buffer.
     *
     * @param buf source buffer positioned at a wrapper written by
     *            {@link #toNetworkContent(FriendlyByteBuf, Content)}
     * @return decoded content wrapper
     */
    default Content fromNetworkContent(FriendlyByteBuf buf) {
        T inner = fromNetwork(buf);
        var perTick = buf.readBoolean();
        float chance = buf.readFloat();
        float tierChanceBoost = buf.readFloat();
        long minOutput = buf.readLong();
        long maxOutput = buf.readLong();
        String slotName = null;
        if (buf.readBoolean()) {
            slotName = buf.readUtf();
        }
        String uiName = null;
        if (buf.readBoolean()) {
            uiName = buf.readUtf();
        }
        return new Content(inner, perTick, chance, tierChanceBoost, minOutput, maxOutput, slotName, uiName);
    }

    /**
     * Converts a {@link Content} wrapper to JSON.
     *
     * @param content wrapper to encode
     * @return JSON object containing inner content and metadata
     */
    @SuppressWarnings("unchecked")
    default JsonElement toJsonContent(Content content) {
        JsonObject json = new JsonObject();
        json.add("content", toJson((T) content.getContent()));
        json.addProperty("perTick", content.perTick);
        json.addProperty("chance", content.chance);
        json.addProperty("tierChanceBoost", content.tierChanceBoost);
        if (content.minOutput != Content.OUTPUT_RANGE_DISABLED) {
            json.addProperty("minOutput", content.minOutput);
        }
        if (content.maxOutput != Content.OUTPUT_RANGE_DISABLED) {
            json.addProperty("maxOutput", content.maxOutput);
        }
        if (!content.slotName.isEmpty())
            json.addProperty("slotName", content.slotName);
        if (!content.uiName.isEmpty())
            json.addProperty("uiName", content.uiName);
        return json;
    }

    /**
     * Converts a {@link Content} wrapper from JSON.
     *
     * @param json JSON object produced by {@link #toJsonContent(Content)}
     * @return decoded content wrapper
     */
    default Content fromJsonContent(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        T inner = fromJson(jsonObject.get("content"));
        var perTick = jsonObject.has("perTick") && jsonObject.get("perTick").getAsBoolean();
        float chance = jsonObject.has("chance") ? jsonObject.get("chance").getAsFloat() : 1;
        float tierChanceBoost = jsonObject.has("tierChanceBoost") ? jsonObject.get("tierChanceBoost").getAsFloat() : 0;
        long minOutput = jsonObject.has("minOutput")
                ? jsonObject.get("minOutput").getAsLong()
                : jsonObject.has("min_output")
                ? jsonObject.get("min_output").getAsLong()
                : jsonObject.has("min") ? jsonObject.get("min").getAsLong() : Content.OUTPUT_RANGE_DISABLED;
        long maxOutput = jsonObject.has("maxOutput")
                ? jsonObject.get("maxOutput").getAsLong()
                : jsonObject.has("max_output")
                ? jsonObject.get("max_output").getAsLong()
                : jsonObject.has("max") ? jsonObject.get("max").getAsLong() : Content.OUTPUT_RANGE_DISABLED;
        String slotName = jsonObject.has("slotName") ? jsonObject.get("slotName").getAsString() : null;
        String uiName = jsonObject.has("uiName") ? jsonObject.get("uiName").getAsString() : null;
        return new Content(inner, perTick, chance, tierChanceBoost, minOutput, maxOutput, slotName, uiName);
    }

    /**
     * Converts a legacy/editor NBT content wrapper to runtime content.
     *
     * @param tag compound containing {@code content}, {@code per_tick},
     *            {@code chance}, {@code tier_chance_boost}, {@code min_output},
     *            {@code max_output}, {@code slot_name}, and {@code ui_name}
     * @return decoded content wrapper
     */
    default Content fromNBT(CompoundTag tag) {
        T content = fromNBT(tag.get("content"));
        boolean perTick = tag.getBoolean("per_tick");
        float chance = tag.getFloat("chance");
        float tierChanceBoost = tag.getFloat("tier_chance_boost");
        // Missing range fields identify legacy fixed-output content. CompoundTag#getLong
        // returns zero for a missing key, so check presence before reading it.
        long minOutput = tag.contains("min_output")
                ? tag.getLong("min_output")
                : tag.contains("minOutput") ? tag.getLong("minOutput") : Content.OUTPUT_RANGE_DISABLED;
        long maxOutput = tag.contains("max_output")
                ? tag.getLong("max_output")
                : tag.contains("maxOutput") ? tag.getLong("maxOutput") : Content.OUTPUT_RANGE_DISABLED;
        String slotName = tag.getString("slot_name");
        String uiName = tag.getString("ui_name");
        return new Content(content, perTick, chance, tierChanceBoost, minOutput, maxOutput, slotName, uiName);
    }

    /**
     * Converts a runtime content wrapper to legacy/editor NBT.
     *
     * @param content wrapper to encode
     * @return compound containing inner content and metadata
     */
    default CompoundTag toNBT(Content content) {
        CompoundTag tag = new CompoundTag();
        tag.put("content", toNBT(of(content.content)));
        tag.putBoolean("per_tick", content.perTick);
        tag.putFloat("chance", content.chance);
        tag.putFloat("tier_chance_boost", content.tierChanceBoost);
        if (content.minOutput != Content.OUTPUT_RANGE_DISABLED) {
            tag.putLong("min_output", content.minOutput);
        }
        if (content.maxOutput != Content.OUTPUT_RANGE_DISABLED) {
            tag.putLong("max_output", content.maxOutput);
        }
        tag.putString("slot_name", content.slotName);
        tag.putString("ui_name", content.uiName);
        return tag;
    }
}
