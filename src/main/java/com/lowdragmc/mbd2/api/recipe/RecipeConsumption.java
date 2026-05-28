package com.lowdragmc.mbd2.api.recipe;

import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Actual inputs consumed while handling a recipe.
 */
public class RecipeConsumption {
    public static final RecipeConsumption EMPTY = new RecipeConsumption(List.of());

    @Getter
    private final List<Entry> entries;
    private final Map<RecipeCapability<?>, List<Object>> byCapability;
    private final Map<String, List<Object>> byCapabilityName;

    public RecipeConsumption(Collection<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<RecipeCapability<?>, List<Object>> byCapability = new HashMap<>();
        Map<String, List<Object>> byCapabilityName = new HashMap<>();
        for (Entry entry : entries) {
            byCapability.computeIfAbsent(entry.capability(), ignored -> new ArrayList<>()).add(entry.content());
            byCapabilityName.computeIfAbsent(entry.capabilityName(), ignored -> new ArrayList<>()).add(entry.content());
        }
        this.byCapability = freeze(byCapability);
        this.byCapabilityName = freeze(byCapabilityName);
    }

    private static <K> Map<K, List<Object>> freeze(Map<K, List<Object>> map) {
        Map<K, List<Object>> result = new HashMap<>();
        map.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Object> get(RecipeCapability<?> capability) {
        return byCapability.getOrDefault(capability, Collections.emptyList());
    }

    public List<Object> get(String capabilityName) {
        return byCapabilityName.getOrDefault(capabilityName, Collections.emptyList());
    }

    public Map<RecipeCapability<?>, List<Object>> byCapability() {
        return byCapability;
    }

    public Map<String, List<Object>> byCapabilityName() {
        return byCapabilityName;
    }

    public static record Entry(
            RecipeCapability<?> capability,
            String capabilityName,
            Object content,
            @Nullable String slotName) {

        public Entry(RecipeCapability<?> capability, Object content, @Nullable String slotName) {
            this(capability, MBDRegistries.RECIPE_CAPABILITIES.getOrDefaultKey(capability, capability.name), content, slotName);
        }

        public Content asContent() {
            return new Content(content, false, 1, 0, slotName, "");
        }
    }
}
