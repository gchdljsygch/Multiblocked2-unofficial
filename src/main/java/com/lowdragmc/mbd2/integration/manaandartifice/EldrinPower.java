package com.lowdragmc.mbd2.integration.manaandartifice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.recipe.content.IContentSerializer;
import com.mna.api.affinity.Affinity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Locale;

/**
 * Mana and Artifice Eldrin power amount tagged with an affinity.
 */
public record EldrinPower(Affinity affinity, float amount) {
    public EldrinPower {
        if (affinity == null) {
            affinity = Affinity.ARCANE;
        }
        amount = Math.max(0, amount);
    }

    public static Affinity parseAffinity(String affinity) {
        if (affinity == null || affinity.isBlank()) {
            return Affinity.ARCANE;
        }
        try {
            return Affinity.valueOf(affinity.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Affinity.ARCANE;
        }
    }

    /**
     * Content serializer for Eldrin power recipe values.
     */
    public static class SerializerEldrinPower implements IContentSerializer<EldrinPower> {
        public static final IContentSerializer<EldrinPower> INSTANCE = new SerializerEldrinPower();

        @Override
        public EldrinPower fromJson(JsonElement json) {
            if (json.isJsonPrimitive()) {
                return new EldrinPower(Affinity.ARCANE, json.getAsFloat());
            }
            var obj = json.getAsJsonObject();
            return new EldrinPower(parseAffinity(GsonHelper.getAsString(obj, "affinity", Affinity.ARCANE.name())),
                    GsonHelper.getAsFloat(obj, "amount"));
        }

        @Override
        public JsonElement toJson(EldrinPower content) {
            var json = new JsonObject();
            json.addProperty("affinity", content.affinity().name().toLowerCase(Locale.ROOT));
            json.addProperty("amount", content.amount());
            return json;
        }

        @Override
        public Tag toNBT(EldrinPower content) {
            var tag = new CompoundTag();
            tag.putString("affinity", content.affinity().name());
            tag.putFloat("amount", content.amount());
            return tag;
        }

        @Override
        public EldrinPower fromNBT(Tag nbt) {
            if (nbt instanceof CompoundTag tag) {
                return new EldrinPower(parseAffinity(tag.getString("affinity")), tag.getFloat("amount"));
            }
            return new EldrinPower(Affinity.ARCANE, 0);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, EldrinPower content) {
            buf.writeEnum(content.affinity());
            buf.writeFloat(content.amount());
        }

        @Override
        public EldrinPower fromNetwork(FriendlyByteBuf buf) {
            return new EldrinPower(buf.readEnum(Affinity.class), buf.readFloat());
        }

        @Override
        public EldrinPower of(Object o) {
            if (o instanceof EldrinPower power) {
                return power;
            } else if (o instanceof Number number) {
                return new EldrinPower(Affinity.ARCANE, number.floatValue());
            } else if (o instanceof CharSequence sequence) {
                var text = sequence.toString();
                var parts = text.split(":");
                if (parts.length == 2) {
                    return new EldrinPower(parseAffinity(parts[0]), NumberUtils.toFloat(parts[1], 0));
                }
                return new EldrinPower(Affinity.ARCANE, NumberUtils.toFloat(text, 0));
            }
            return new EldrinPower(Affinity.ARCANE, 0);
        }

        @Override
        public EldrinPower copyWithModifier(EldrinPower content, ContentModifier modifier) {
            return new EldrinPower(content.affinity(), modifier.apply(content.amount()).floatValue());
        }

        @Override
        public EldrinPower copyInner(EldrinPower content) {
            return new EldrinPower(content.affinity(), content.amount());
        }

        @Override
        public EldrinPower deepCopyInner(EldrinPower content) {
            return copyInner(content);
        }
    }
}
