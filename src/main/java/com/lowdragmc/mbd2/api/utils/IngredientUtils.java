package com.lowdragmc.mbd2.api.utils;

import dev.latvian.mods.kubejs.item.InputItem;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngredientUtils {
    public static List<InputItem> deduplicateElements(List<Ingredient> list) {
        Map<String, InputItem> deduplicated = new LinkedHashMap<>();
        for (Ingredient ingredient : list) {
            if (ingredient == null) {
                continue;
            }
            var key = ingredient.toJson().toString();
            deduplicated.compute(key, (ignored, existing) -> existing == null
                    ? InputItem.of(ingredient)
                    : existing.withCount(existing.count + 1));
        }
        return new ArrayList<>(deduplicated.values());
    }
}
