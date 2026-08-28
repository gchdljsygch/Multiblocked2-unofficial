package com.lowdragmc.mbd2.integration.kubejs.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MBDRecipeSchemaEntityIngredientTest {

    @Test
    void plainEntityIdDefaultsToOneEntity() {
        var parsed = MBDRecipeSchema.EntityIngredientJS.parseEntityInput("minecraft:zombie");

        assertEquals(1, parsed.amount());
        assertEquals(ResourceLocation.parse("minecraft:zombie"), parsed.entityId());
    }

    @Test
    void quantityPrefixIsPreserved() {
        var parsed = MBDRecipeSchema.EntityIngredientJS.parseEntityInput("2x minecraft:zombie");

        assertEquals(2, parsed.amount());
        assertEquals(ResourceLocation.parse("minecraft:zombie"), parsed.entityId());
    }

    @Test
    void invalidEntityInputIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> MBDRecipeSchema.EntityIngredientJS.of("not an entity"));
    }
}
