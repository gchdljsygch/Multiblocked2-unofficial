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
        String slotName = null;
        if (buf.readBoolean()) {
            slotName = buf.readUtf();
        }
        String uiName = null;
        if (buf.readBoolean()) {
            uiName = buf.readUtf();
        }
        return new Content(inner, perTick, chance, tierChanceBoost, slotName, uiName);
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
        String slotName = jsonObject.has("slotName") ? jsonObject.get("slotName").getAsString() : null;
        String uiName = jsonObject.has("uiName") ? jsonObject.get("uiName").getAsString() : null;
        return new Content(inner, perTick, chance, tierChanceBoost, slotName, uiName);
    }

    /**
     * Converts a legacy/editor NBT content wrapper to runtime content.
     *
     * @param tag compound containing {@code content}, {@code per_tick},
     *            {@code chance}, {@code tier_chance_boost}, {@code slot_name}, and
     *            {@code ui_name}
     * @return decoded content wrapper
     */
    default Content fromNBT(CompoundTag tag) {
        T content = fromNBT(tag.get("content"));
        boolean perTick = tag.getBoolean("per_tick");
        float chance = tag.getFloat("chance");
        float tierChanceBoost = tag.getFloat("tier_chance_boost");
        String slotName = tag.getString("slot_name");
        String uiName = tag.getString("ui_name");
        return new Content(content, perTick, chance, tierChanceBoost, slotName, uiName);
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
        tag.putString("slot_name", content.slotName);
        tag.putString("ui_name", content.uiName);
        return tag;
    }
}
