package com.lowdragmc.mbd2.integration.bloodmagic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.recipe.content.IContentSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.math.NumberUtils;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;

import java.util.Locale;

public record BloodMagicWill(EnumDemonWillType type, double amount, double maxOutput) {
    public static final double DEFAULT_MAX_OUTPUT = 100d;

    public static BloodMagicWill of(EnumDemonWillType type, double amount) {
        return new BloodMagicWill(type, amount, Math.max(amount, DEFAULT_MAX_OUTPUT));
    }

    public static EnumDemonWillType parseType(String type) {
        if (type == null || type.isBlank()) {
            return EnumDemonWillType.DEFAULT;
        }
        var parsed = EnumDemonWillType.getType(type.toLowerCase(Locale.ROOT));
        return parsed == null ? EnumDemonWillType.DEFAULT : parsed;
    }

    public BloodMagicWill {
        if (type == null) {
            type = EnumDemonWillType.DEFAULT;
        }
        amount = Math.max(0, amount);
        maxOutput = Math.max(0, maxOutput);
    }

    public static class SerializerBloodMagicWill implements IContentSerializer<BloodMagicWill> {
        public static final IContentSerializer<BloodMagicWill> INSTANCE = new SerializerBloodMagicWill();

        @Override
        public BloodMagicWill fromJson(JsonElement json) {
            if (json.isJsonPrimitive()) {
                return BloodMagicWill.of(EnumDemonWillType.DEFAULT, json.getAsDouble());
            }
            var obj = json.getAsJsonObject();
            var type = parseType(GsonHelper.getAsString(obj, "type", EnumDemonWillType.DEFAULT.name()));
            var amount = GsonHelper.getAsDouble(obj, "amount");
            var maxOutput = GsonHelper.getAsDouble(obj, "maxOutput", Math.max(amount, DEFAULT_MAX_OUTPUT));
            return new BloodMagicWill(type, amount, maxOutput);
        }

        @Override
        public JsonElement toJson(BloodMagicWill content) {
            var json = new JsonObject();
            json.addProperty("type", content.type().name().toLowerCase(Locale.ROOT));
            json.addProperty("amount", content.amount());
            json.addProperty("maxOutput", content.maxOutput());
            return json;
        }

        @Override
        public Tag toNBT(BloodMagicWill content) {
            var tag = new CompoundTag();
            tag.putString("type", content.type().name());
            tag.putDouble("amount", content.amount());
            tag.putDouble("maxOutput", content.maxOutput());
            return tag;
        }

        @Override
        public BloodMagicWill fromNBT(Tag nbt) {
            if (nbt instanceof CompoundTag tag) {
                return new BloodMagicWill(parseType(tag.getString("type")), tag.getDouble("amount"), tag.getDouble("maxOutput"));
            }
            return BloodMagicWill.of(EnumDemonWillType.DEFAULT, 0);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BloodMagicWill content) {
            buf.writeEnum(content.type());
            buf.writeDouble(content.amount());
            buf.writeDouble(content.maxOutput());
        }

        @Override
        public BloodMagicWill fromNetwork(FriendlyByteBuf buf) {
            return new BloodMagicWill(buf.readEnum(EnumDemonWillType.class), buf.readDouble(), buf.readDouble());
        }

        @Override
        public BloodMagicWill of(Object o) {
            if (o instanceof BloodMagicWill will) {
                return will;
            } else if (o instanceof Number number) {
                return BloodMagicWill.of(EnumDemonWillType.DEFAULT, number.doubleValue());
            } else if (o instanceof CharSequence sequence) {
                var text = sequence.toString();
                var parts = text.split(":");
                if (parts.length == 2 || parts.length == 3) {
                    var type = parseType(parts[0]);
                    var amount = NumberUtils.toDouble(parts[1], 0);
                    var maxOutput = parts.length == 3 ? NumberUtils.toDouble(parts[2], Math.max(amount, DEFAULT_MAX_OUTPUT)) : Math.max(amount, DEFAULT_MAX_OUTPUT);
                    return new BloodMagicWill(type, amount, maxOutput);
                }
                return BloodMagicWill.of(EnumDemonWillType.DEFAULT, NumberUtils.toDouble(text, 0));
            }
            return BloodMagicWill.of(EnumDemonWillType.DEFAULT, 0);
        }

        @Override
        public BloodMagicWill copyWithModifier(BloodMagicWill content, ContentModifier modifier) {
            return new BloodMagicWill(content.type(), modifier.apply(content.amount()).doubleValue(),
                    modifier.apply(content.maxOutput()).doubleValue());
        }

        @Override
        public BloodMagicWill copyInner(BloodMagicWill content) {
            return new BloodMagicWill(content.type(), content.amount(), content.maxOutput());
        }

        @Override
        public BloodMagicWill deepCopyInner(BloodMagicWill content) {
            return copyInner(content);
        }

    }
}
