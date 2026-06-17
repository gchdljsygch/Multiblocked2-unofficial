package com.lowdragmc.mbd2.api.pattern.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Untyped scratch context shared by predicates during one pattern check.
 *
 * <p>The business goal is to let independent predicates contribute data such as
 * slots, IO maps, render masks, matched parts, and preview diagnostics without a
 * hard dependency between each predicate implementation. Values are keyed by
 * stable string constants used by callers. This container is not thread-safe;
 * it is owned by one {@code MultiblockState} check and cleared before reuse.</p>
 */
public class PatternMatchContext {

    private final Map<String, Object> data = new HashMap<>();

    /**
     * Removes all values from this context.
     *
     * <p>Side effects: invalidates references stored only in this map. Objects
     * retrieved earlier remain mutable if a caller still holds them.</p>
     */
    public void reset() {
        this.data.clear();
    }

    /**
     * Stores a value under a context key.
     *
     * @param key   logical key shared by producers and consumers
     * @param value value to store; {@code null} is allowed and behaves like a
     *              normal map value
     */
    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * Reads an integer value.
     *
     * <p>Preconditions: if the key exists, its value must be an {@link Integer}
     * or a {@link ClassCastException} will be thrown.</p>
     *
     * @param key key to read
     * @return stored integer, or {@code 0} when absent
     */
    public int getInt(String key) {
        return data.containsKey(key) ? (int) data.get(key) : 0;
    }

    /**
     * Adds to an integer context value.
     *
     * <p>Side effects: writes the incremented value back to the context.</p>
     *
     * @param key   key to increment
     * @param value amount to add; may be negative
     */
    public void increment(String key, int value) {
        set(key, getOrDefault(key, 0) + value);
    }

    /**
     * Reads a typed value or a fallback.
     *
     * <p>Preconditions: callers must request the same type that producers stored
     * for the key. Type safety is enforced only by the cast at runtime.</p>
     *
     * @param key          key to read
     * @param defaultValue value returned when the key is absent
     * @param <T>          expected value type
     * @return stored value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    /**
     * Reads a typed value.
     *
     * @param key key to read
     * @param <T> expected value type
     * @return stored value, or {@code null} when absent
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * Reads an existing value or creates and stores one.
     *
     * <p>Side effects: invokes {@code creator} and stores its result only when no
     * value exists. A stored {@code null} is treated as absent.</p>
     *
     * @param key     key to read or initialize
     * @param creator supplier for the initial value
     * @param <T>     expected value type
     * @return existing or newly stored value
     */
    public <T> T getOrCreate(String key, Supplier<T> creator) {
        T result = get(key);
        if (result == null) {
            result = creator.get();
            set(key, result);
        }
        return result;
    }

    /**
     * Reads an existing value or stores a provided initial value.
     *
     * @param key          key to read or initialize
     * @param initialValue value stored when absent
     * @param <T>          expected value type
     * @return existing or newly stored value
     */
    public <T> T getOrPut(String key, T initialValue) {
        T result = get(key);
        if (result == null) {
            result = initialValue;
            set(key, result);
        }
        return result;
    }

    /**
     * Checks whether a context key is present.
     *
     * @param key key to query
     * @return {@code true} when the key exists, even if its value is null
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * Returns the live entry set.
     *
     * <p>Side effects: callers may mutate entries through the returned set.</p>
     *
     * @return mutable backing entry set
     */
    public Set<Map.Entry<String, Object>> entrySet() {
        return data.entrySet();
    }
}
