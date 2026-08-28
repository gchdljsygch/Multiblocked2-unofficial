package com.lowdragmc.mbd2.common.capability.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.mbd2.api.recipe.ingredient.SizedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ItemRecipeCapabilityTest {

    @Test
    void candidateUpdateReplacesCachedSizedIngredient() {
        SizedIngredient previous = SizedIngredient.create(new EmptyIngredient(), 4);
        previous.getItems();
        Ingredient changedInner = new EmptyIngredient();
        AtomicReference<Ingredient> updated = new AtomicReference<>();

        ItemRecipeCapability.publishUpdatedIngredient(changedInner, () -> previous, updated::set);

        SizedIngredient replacement = (SizedIngredient) updated.get();
        assertNotSame(previous, replacement);
        assertSame(changedInner, replacement.getInner());
        assertEquals(4, replacement.getAmount());
        assertEquals(0, replacement.getItems().length);
    }

    @Test
    void vanillaCandidateUpdateCreatesANewInnerIngredient() {
        Ingredient.Value originalValue = new MarkerValue("original");
        Ingredient.Value replacementValue = new MarkerValue("replacement");
        SizedIngredient previous = SizedIngredient.create(Ingredient.fromValues(Stream.of(originalValue)), 2);
        previous.getItems();
        AtomicReference<Ingredient> updated = new AtomicReference<>();

        ItemRecipeCapability.publishVanillaIngredientValues(new Ingredient.Value[]{replacementValue}, () -> previous, updated::set);

        SizedIngredient replacement = (SizedIngredient) updated.get();
        assertNotSame(previous, replacement);
        assertNotSame(previous.getInner(), replacement.getInner());
        assertEquals(2, replacement.getAmount());
        assertEquals("replacement", replacement.getInner().toJson().getAsJsonObject().get("marker").getAsString());
    }

    private static final class EmptyIngredient extends Ingredient {
        private EmptyIngredient() {
            super(Stream.empty());
        }
    }

    private record MarkerValue(String marker) implements Ingredient.Value {
        @Override
        public Collection<ItemStack> getItems() {
            return List.of();
        }

        @Override
        public JsonObject serialize() {
            JsonObject result = new JsonObject();
            result.addProperty("marker", marker);
            return result;
        }
    }
}
