package com.lowdragmc.mbd2.common.gui.editor.multiblock;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.common.gui.editor.PredicateResource;
import com.mojang.datafixers.util.Either;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.*;

/**
 * Mutable pattern cell used by the multiblock editor to describe one block position.
 *
 * <p>The placeholder stores ordered predicate references rather than resolved predicate
 * objects. Built-in predicates are kept as registry-like strings, while project-local
 * predicates are kept as files inside the project resource set. The associated
 * {@link PredicateResource} is runtime context and is not written to NBT.</p>
 *
 * <p>Instances are edited directly by GUI widgets and are intended for client-editor use
 * on the render/UI thread. They are not thread-safe.</p>
 */
@Accessors(chain = true)
@EqualsAndHashCode
public class BlockPlaceholder implements IConfigurable, ITagSerializable<CompoundTag> {
    @EqualsAndHashCode.Exclude
    @Getter
    protected final PredicateResource predicateResource;
    @Getter
    protected Set<Either<String, File>> predicates = new LinkedHashSet<>() {
        @Override
        public boolean remove(Object o) {
            return super.remove(o);
        }

        @Override
        public void clear() {
            super.clear();
        }
    };
    @Getter
    @Setter
    protected boolean isController;
    @Getter
    @Setter
    protected Direction facing = Direction.NORTH;

    /**
     * Creates an empty placeholder bound to a predicate resource table.
     * <p>
     * The constructor is protected so callers use {@link #create(PredicateResource, Either[])} or
     * {@link #controller(PredicateResource, Either[])} and make the controller role explicit. The supplied resource table
     * is retained as runtime context and is not serialized. Instances are mutable editor objects and are not thread-safe.
     *
     * @param predicateResource resource table used to resolve predicate references later
     */
    protected BlockPlaceholder(PredicateResource predicateResource) {
        this.predicateResource = predicateResource;
    }

    /**
     * Creates a non-controller placeholder with the supplied predicate references.
     *
     * @param predicateResource resource table used later to resolve the predicate keys
     * @param predicates        zero or more built-in or project predicate references; order is
     *                          preserved and later candidates are evaluated by editor consumers
     * @return a mutable placeholder bound to {@code predicateResource}
     */
    @SafeVarargs
    public static BlockPlaceholder create(PredicateResource predicateResource, Either<String, File>... predicates) {
        var holder = new BlockPlaceholder(predicateResource);
        holder.predicates.addAll(Arrays.asList(predicates));
        return holder;
    }

    /**
     * Creates a controller placeholder.
     *
     * <p>The caller is responsible for ensuring that only one controller placeholder is
     * active in a pattern. The facing defaults to north until explicitly changed.</p>
     *
     * @param predicateResource resource table used later to resolve any supplied predicates
     * @param predicates        optional predicate references to keep with the controller cell
     * @return a mutable controller placeholder
     */
    @SafeVarargs
    public static BlockPlaceholder controller(PredicateResource predicateResource, Either<String, File>... predicates) {
        var holder = create(predicateResource, predicates);
        holder.isController = true;
        return holder;
    }

    /**
     * Recreates a placeholder from serialized project data.
     *
     * @param predicateResource runtime resource table that resolves predicate keys after load
     * @param tag               serialized placeholder state produced by {@link #serializeNBT()} or the
     *                          legacy string-list format accepted by {@link #deserializeNBT(CompoundTag)}
     * @return a mutable placeholder populated from {@code tag}
     */
    public static BlockPlaceholder fromTag(PredicateResource predicateResource, CompoundTag tag) {
        var holder = new BlockPlaceholder(predicateResource);
        holder.deserializeNBT(tag);
        return holder;
    }

    /**
     * Serializes predicate references and controller metadata.
     *
     * @return a compound containing the predicate list, controller flag, and facing data value
     */
    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        var predicatesTag = new ListTag();
        for (var predicate : predicates) {
            predicatesTag.add(predicate.map(
                    l -> {
                        var key = new CompoundTag();
                        key.putString("key", l);
                        key.putString("type", "builtin");
                        return key;
                    }, r -> {
                        var key = new CompoundTag();
                        key.putString("key", r.getPath());
                        key.putString("type", "project");
                        return key;
                    }
            ));
        }
        tag.put("predicates", predicatesTag);
        tag.putBoolean("isController", isController);
        tag.putInt("facing", facing.get3DDataValue());
        return tag;
    }

    /**
     * Replaces this placeholder with data from NBT.
     *
     * <p>The method clears existing predicate references before loading. It accepts both the
     * current compound-list format and the older string-list format, where every string is
     * interpreted as a built-in predicate key.</p>
     *
     * @param nbt serialized placeholder state; missing fields fall back to empty predicates,
     *            non-controller status, and {@link Direction#NORTH}
     */
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.predicates.clear();
        var predicatesTag = nbt.getList("predicates", Tag.TAG_STRING);
        if (predicatesTag.isEmpty()) {
            predicatesTag = nbt.getList("predicates", Tag.TAG_COMPOUND);
            for (var tag : predicatesTag) {
                var compoundTag = (CompoundTag) tag;
                var key = compoundTag.getString("key");
                var type = compoundTag.getString("type");
                if ("builtin".equals(type)) {
                    predicates.add(Either.left(key));
                } else if ("project".equals(type)) {
                    predicates.add(Either.right(new File(key)));
                }
            }
        } else {
            for (var tag : predicatesTag) {
                predicates.add(Either.left(tag.getAsString()));
            }
        }
        isController = nbt.getBoolean("isController");
        facing = Direction.from3DDataValue(nbt.getInt("facing"));
    }
}
