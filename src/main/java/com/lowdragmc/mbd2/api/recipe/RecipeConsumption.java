package com.lowdragmc.mbd2.api.recipe;

import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable snapshot of the concrete inputs consumed while a recipe was
 * handled.
 *
 * <p>The business goal is to expose what was actually drained, extracted, or
 * spent by capability traits so downstream hooks can inspect recipe cost by
 * capability or by registered capability name. Instances copy incoming entries
 * and expose unmodifiable views. The class has no mutation side effects after
 * construction and is thread-safe as long as the recorded content objects are
 * treated as immutable snapshots by their producers.</p>
 */
public class RecipeConsumption {
    /**
     * Shared empty snapshot used when no capability recorded consumption.
     */
    public static final RecipeConsumption EMPTY = new RecipeConsumption(List.of());

    @Getter
    private final List<Entry> entries;
    private final Map<RecipeCapability<?>, List<Object>> byCapability;
    private final Map<String, List<Object>> byCapabilityName;

    /**
     * Builds an immutable consumption snapshot and its lookup indexes.
     *
     * <p>Preconditions: {@code entries} must be non-null and must not contain
     * null entries. Each entry should provide a non-null capability, capability
     * name, and consumed object. Side effects: copies the collection and builds
     * unmodifiable capability indexes.</p>
     *
     * @param entries consumed inputs in the order they were recorded
     */
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

    /**
     * Indicates whether any consumed content was recorded.
     *
     * @return {@code true} when this snapshot has no entries
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns consumed content recorded for a capability instance.
     *
     * <p>Preconditions: {@code capability} should be a registered recipe
     * capability. Side effects: none.</p>
     *
     * @param capability capability key to look up
     * @return immutable list of consumed content objects, or an empty immutable
     * list when the capability did not consume anything
     */
    public List<Object> get(RecipeCapability<?> capability) {
        return byCapability.getOrDefault(capability, Collections.emptyList());
    }

    /**
     * Returns consumed content recorded for a capability registry name.
     *
     * @param capabilityName registered capability name such as {@code "item"}
     * @return immutable list of consumed content objects, or an empty immutable
     * list when no entries were recorded under that name
     */
    public List<Object> get(String capabilityName) {
        return byCapabilityName.getOrDefault(capabilityName, Collections.emptyList());
    }

    /**
     * Returns all consumed content grouped by capability instance.
     *
     * @return immutable map whose values are immutable lists
     */
    public Map<RecipeCapability<?>, List<Object>> byCapability() {
        return byCapability;
    }

    /**
     * Returns all consumed content grouped by capability registry name.
     *
     * @return immutable map whose values are immutable lists
     */
    public Map<String, List<Object>> byCapabilityName() {
        return byCapabilityName;
    }

    /**
     * One consumed content object and the capability slot that produced it.
     *
     * @param capability     capability that consumed the content; must be non-null
     * @param capabilityName registry key used for name-based lookup
     * @param content        consumed object snapshot; expected type is defined by the
     *                       capability
     * @param slotName       optional trait slot name that performed the consumption
     */
    public static record Entry(
            RecipeCapability<?> capability,
            String capabilityName,
            Object content,
            @Nullable String slotName) {

        /**
         * Creates an entry and resolves the capability's registered name.
         *
         * <p>Preconditions: {@code capability} and {@code content} must be
         * non-null. Side effects: reads the recipe capability registry.</p>
         *
         * @param capability capability that consumed the content
         * @param content    consumed object snapshot
         * @param slotName   optional trait slot name that performed consumption
         */
        public Entry(RecipeCapability<?> capability, Object content, @Nullable String slotName) {
            this(capability, MBDRegistries.RECIPE_CAPABILITIES.getOrDefaultKey(capability, capability.name), content, slotName);
        }

        /**
         * Converts this consumed entry back into a recipe content wrapper.
         *
         * <p>Business goal: allow consumers that understand {@link Content} to
         * reuse recorded actual costs. Side effects: none.</p>
         *
         * @return content wrapper with non-chanced, unmodified cost semantics
         */
        public Content asContent() {
            return new Content(content, false, 1, 0, slotName, "");
        }
    }
}
