package com.lowdragmc.mbd2.api.registry;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.lowdragmc.mbd2.MBD2;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Mod-local bidirectional registry used for definitions that need stable ids before Forge registry lookup is enough.
 * <p>
 * Entries are keyed by a caller-defined key type and can be serialized by subclasses. The registry starts frozen; startup
 * bootstrap code must unfreeze it, register all entries, and freeze it again before runtime reads. Mutating a registry
 * after machines, recipes, or editor projects have cached entries can leave stale references.
 * <p>
 * Thread safety: this class is not synchronized. Mutations are expected on the Forge/mod-loading thread during startup,
 * while normal gameplay and UI code should only perform read-only access after the registry has been frozen.
 *
 * @param <K> key type used to identify entries; implementations normally use {@link java.lang.String} or
 *            {@link ResourceLocation}
 * @param <V> registered value type
 */
public abstract class MBDRegistry<K, V> implements Iterable<V> {
    public static final Map<ResourceLocation, MBDRegistry<?, ?>> REGISTERED = new LinkedHashMap<>();

    protected final BiMap<K, V> registry;
    @Getter
    protected final ResourceLocation registryName;
    @Getter
    protected boolean frozen = true;

    /**
     * Creates a registry and exposes it through the global registry map.
     * <p>
     * Side effects: allocates the backing bidirectional map and stores this instance in {@link #REGISTERED} under
     * {@code registryName}. If another registry already used that name it is replaced in the global map.
     *
     * @param registryName globally unique id used for diagnostics, serialization errors, and lookup from
     *                     {@link #REGISTERED}; must not be {@code null}
     */
    public MBDRegistry(ResourceLocation registryName) {
        registry = initRegistry();
        this.registryName = registryName;

        REGISTERED.put(registryName, this);
    }

    /**
     * Creates the backing bidirectional map for key/value lookups.
     * <p>
     * Subclasses may override this to customize map ordering or equality behavior. The returned map is used directly and
     * must support inverse lookup.
     *
     * @return empty mutable bidirectional map used for all registry entries
     */
    protected BiMap<K, V> initRegistry() {
        return HashBiMap.create();
    }

    /**
     * Checks whether an entry is registered for the supplied key.
     *
     * @param key key to test; follows the backing map's null-handling rules
     * @return {@code true} when {@code key} is currently present
     */
    public boolean containKey(K key) {
        return registry.containsKey(key);
    }

    /**
     * Checks whether a value is already registered under any key.
     *
     * @param value value to test; follows the backing map's null-handling rules
     * @return {@code true} when the value is currently present
     */
    public boolean containValue(V value) {
        return registry.containsValue(value);
    }

    /**
     * Locks the registry against further mutation.
     * <p>
     * Call this after startup registration and extension events finish. Side effect: flips the frozen flag to
     * {@code true}. This class does not create an immutable map, so callers with access to {@link #registry()} must still
     * treat the returned map as read-only.
     *
     * @throws IllegalStateException when the registry is already frozen
     */
    public void freeze() {
        if (frozen) {
            throw new IllegalStateException("Registry is already frozen!");
        }

        this.frozen = true;
    }

    /**
     * Allows controlled startup mutation.
     * <p>
     * Call before registering built-ins or handling extension events, and call {@link #freeze()} once registration is
     * complete. Runtime code should not unfreeze registries because existing machines, recipes, and editor resources may
     * already hold references to registered values.
     *
     * @throws IllegalStateException when the registry is already unfrozen
     */
    public void unfreeze() {
        if (!frozen) {
            throw new IllegalStateException("Registry is already unfrozen!");
        }

        this.frozen = false;
    }

    /**
     * Registers a new key/value pair.
     * <p>
     * The key must not already exist. Because the backing map is bidirectional, duplicate values are also rejected by the
     * map implementation. Side effect: mutates the registry.
     *
     * @param key   unique key that will identify {@code value}
     * @param value value to expose through this registry
     * @throws IllegalStateException    when the registry is frozen or {@code key} is already present
     * @throws IllegalArgumentException when the backing {@link BiMap} rejects a duplicate value
     */
    public void register(K key, V value) {
        if (frozen) {
            throw new IllegalStateException("[register] registry %s has been frozen".formatted(registryName));
        }
        if (containKey(key)) {
            throw new IllegalStateException("[register] registry %s contains key %s already".formatted(registryName, key));
        }
        registry.put(key, value);
    }

    /**
     * Replaces the value for a key, adding the key if it was absent.
     * <p>
     * Missing keys are allowed but logged because most callers intend to update an existing entry. Side effect: mutates
     * the registry.
     *
     * @param key   key to replace or create
     * @param value replacement value
     * @return previous value for {@code key}, or {@code null} if the key was not present
     * @throws IllegalStateException    when the registry is frozen
     * @throws IllegalArgumentException when the backing {@link BiMap} rejects a duplicate value
     */
    @Nullable
    public V replace(K key, V value) {
        if (frozen) {
            throw new IllegalStateException("[replace] registry %s has been frozen".formatted(registryName));
        }
        if (!containKey(key)) {
            MBD2.LOGGER.warn("[replace] couldn't find key %s in registry %s".formatted(registryName, key));
        }
        return registry.put(key, value);
    }

    /**
     * Registers a value or overwrites an existing mapping without treating replacement as exceptional.
     * <p>
     * Use this when extension code intentionally wants last-writer-wins behavior. Side effect: mutates the registry.
     *
     * @param key   key to create or overwrite
     * @param value value to associate with {@code key}
     * @return previous value for {@code key}, or {@code null} when a new mapping was created
     * @throws IllegalStateException    when the registry is frozen
     * @throws IllegalArgumentException when the backing {@link BiMap} rejects a duplicate value
     */
    public V registerOrOverride(K key, V value) {
        if (frozen) {
            throw new IllegalStateException("[register] registry %s has been frozen".formatted(registryName));
        }
        return registry.put(key, value);
    }

    @NotNull
    @Override
    public Iterator<V> iterator() {
        return registry.values().iterator();
    }

    /**
     * Returns the live value set.
     * <p>
     * The returned set is backed by the registry. Do not mutate it after registration has finished.
     *
     * @return live set of registered values in backing map iteration order
     */
    public Set<V> values() {
        return registry.values();
    }

    /**
     * Returns the live key set.
     * <p>
     * The returned set is backed by the registry. Do not mutate it after registration has finished.
     *
     * @return live set of registered keys in backing map iteration order
     */
    public Set<K> keys() {
        return registry.keySet();
    }

    /**
     * Returns the live registry entries.
     * <p>
     * The returned set is backed by the registry. Entry mutation has the same side effects as direct map mutation and is
     * not guarded by {@link #frozen}.
     *
     * @return live set of key/value entries
     */
    public Set<Map.Entry<K, V>> entries() {
        return registry.entrySet();
    }

    /**
     * Returns the live backing map.
     * <p>
     * This is intended for iteration and integration with APIs that need a map view. Mutating the returned map bypasses
     * the frozen checks in this class and should be limited to trusted bootstrap code.
     *
     * @return live bidirectional registry map
     */
    public Map<K, V> registry() {
        return registry;
    }

    /**
     * Looks up a value by key.
     *
     * @param key registry key to resolve
     * @return registered value, or {@code null} when the key is unknown
     */
    @Nullable
    public V get(K key) {
        return registry.get(key);
    }

    /**
     * Looks up a value by key with a caller-provided fallback.
     *
     * @param key          registry key to resolve
     * @param defaultValue value returned when {@code key} is not registered
     * @return registered value for {@code key}, or {@code defaultValue} when absent
     */
    public V getOrDefault(K key, V defaultValue) {
        return registry.getOrDefault(key, defaultValue);
    }

    /**
     * Resolves the key currently associated with a value.
     *
     * @param value registered value to reverse-resolve
     * @return key for {@code value}, or {@code null} when the value is not registered
     */
    public K getKey(V value) {
        return registry.inverse().get(value);
    }

    /**
     * Resolves a value to its key with a caller-provided fallback.
     *
     * @param key        value to reverse-resolve
     * @param defaultKey key returned when {@code key} is not registered as a value
     * @return registered key for the value, or {@code defaultKey} when absent
     */
    public K getOrDefaultKey(V key, K defaultKey) {
        return registry.inverse().getOrDefault(key, defaultKey);
    }

    /**
     * Writes a registry value reference to a network buffer.
     * <p>
     * Implementations should encode enough information to recover the same registered value through
     * {@link #readBuf(FriendlyByteBuf)}. Unknown values should be handled deterministically, usually by writing an absent
     * marker instead of throwing during normal sync.
     *
     * @param value value to serialize; may be absent from the registry depending on implementation policy
     * @param buf   target buffer positioned for writing
     */
    public abstract void writeBuf(V value, FriendlyByteBuf buf);

    /**
     * Reads a registry value reference from a network buffer.
     *
     * @param buf source buffer positioned at data produced by {@link #writeBuf(Object, FriendlyByteBuf)}
     * @return resolved registered value, or {@code null} when the buffer marks no value or references an unknown key
     */
    @Nullable
    public abstract V readBuf(FriendlyByteBuf buf);

    /**
     * Saves a registry value reference to NBT.
     *
     * @param value value to serialize; implementations decide how to represent values absent from the registry
     * @return NBT tag containing the serialized key or an implementation-defined empty/fallback tag
     */
    public abstract Tag saveToNBT(V value);

    /**
     * Loads a registry value reference from NBT.
     *
     * @param tag tag produced by {@link #saveToNBT(Object)} or compatible persisted data
     * @return resolved registered value, or {@code null} when the tag cannot be resolved
     */
    @Nullable
    public abstract V loadFromNBT(Tag tag);

    /**
     * Removes an entry by key.
     * <p>
     * Side effect: mutates the backing registry without checking {@link #frozen}. This method should only be used by
     * bootstrap or editor code that owns the registry lifecycle.
     *
     * @param name key to remove
     * @return {@code true} when an entry was removed, {@code false} when the key was absent
     */
    public boolean remove(K name) {
        return registry.remove(name) != null;
    }

    /**
     * Creates a codec that serializes values as registry keys.
     *
     * @return codec that resolves serialized keys against this registry and reports a data error for unknown keys or
     * values
     */
    public abstract Codec<V> codec();

    //************************ Built-in Registry ************************//

    /**
     * Registry variant keyed by plain strings.
     * <p>
     * Values are serialized by writing the string key. Missing or unregistered values are represented as absent network
     * values or an empty compound tag for NBT.
     *
     * @param <V> registered value type
     */
    public static class String<V> extends MBDRegistry<java.lang.String, V> {

        /**
         * Creates a string-keyed registry.
         *
         * @param registryName id of this registry for diagnostics and global lookup
         */
        public String(ResourceLocation registryName) {
            super(registryName);
        }

        @Override
        public void writeBuf(V value, FriendlyByteBuf buf) {
            buf.writeBoolean(containValue(value));
            if (containValue(value)) {
                buf.writeUtf(getKey(value));
            }
        }

        @Override
        public V readBuf(FriendlyByteBuf buf) {
            if (buf.readBoolean()) {
                return get(buf.readUtf());
            }
            return null;
        }

        @Override
        public Tag saveToNBT(V value) {
            if (containValue(value)) {
                return StringTag.valueOf(getKey(value));
            }
            return new CompoundTag();
        }

        @Override
        public V loadFromNBT(Tag tag) {
            return get(tag.getAsString());
        }

        @Override
        public Codec<V> codec() {
            return Codec.STRING.flatXmap(str -> Optional.ofNullable(this.get(str)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry key in " + this.registryName + ": " + str)), obj -> Optional.ofNullable(this.getKey(obj)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry element in " + this.registryName + ": " + obj)));
        }
    }

    /**
     * Registry variant keyed by {@link ResourceLocation}.
     * <p>
     * Values are serialized by writing the resource-location key. Invalid or unknown serialized keys resolve to
     * {@code null}.
     *
     * @param <V> registered value type
     */
    public static class RL<V> extends MBDRegistry<ResourceLocation, V> {

        /**
         * Creates a resource-location-keyed registry.
         *
         * @param registryName id of this registry for diagnostics and global lookup
         */
        public RL(ResourceLocation registryName) {
            super(registryName);
        }

        @Override
        public void writeBuf(V value, FriendlyByteBuf buf) {
            buf.writeBoolean(containValue(value));
            if (containValue(value)) {
                buf.writeUtf(getKey(value).toString());
            }
        }

        @Override
        public V readBuf(FriendlyByteBuf buf) {
            if (buf.readBoolean()) {
                var key = ResourceLocation.tryParse(buf.readUtf());
                return key == null ? null : get(key);
            }
            return null;
        }

        @Override
        public Tag saveToNBT(V value) {
            if (containValue(value)) {
                return StringTag.valueOf(getKey(value).toString());
            }
            return new CompoundTag();
        }

        @Override
        public V loadFromNBT(Tag tag) {
            var key = ResourceLocation.tryParse(tag.getAsString());
            return key == null ? null : get(key);
        }

        @Override
        public Codec<V> codec() {
            return ResourceLocation.CODEC.flatXmap(rl -> Optional.ofNullable(this.get(rl)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry key in " + this.registryName + ": " + rl)), obj -> Optional.ofNullable(this.getKey(obj)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry element in " + this.registryName + ": " + obj)));
        }
    }
}
