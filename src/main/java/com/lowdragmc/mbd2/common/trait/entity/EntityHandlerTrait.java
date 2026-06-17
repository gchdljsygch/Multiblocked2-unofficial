package com.lowdragmc.mbd2.common.trait.entity;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.api.recipe.ingredient.EntityIngredient;
import com.lowdragmc.mbd2.common.capability.recipe.EntityRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.RecipeHandlerTrait;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recipe capability trait that treats nearby living entities as recipe contents.
 *
 * <p>Input handlers periodically scan the configured world-space area and keep a live snapshot of matching
 * entities. Output handlers spawn entities inside the same area when a recipe commits. All world interaction is
 * intended to run on the logical server thread; {@link #entitiesLock} only protects the cached entity list while
 * recipe matching takes a snapshot or removes consumed entries.</p>
 */
public class EntityHandlerTrait extends RecipeCapabilityTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(EntityHandlerTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private final List<Entity> entities = new ArrayList<>();
    private final Lock entitiesLock = new ReentrantLock();
    private final EntityRecipeHandler handler = new EntityRecipeHandler();

    /**
     * Creates the runtime entity handler for one machine instance.
     *
     * @param machine    the owning machine; must have a server level before recipes can scan or spawn entities
     * @param definition the entity handler definition that supplies the scan/spawn area and recipe IO settings
     */
    public EntityHandlerTrait(MBDMachine machine, EntityHandlerTraitDefinition definition) {
        super(machine, definition);
    }

    /**
     * Returns the concrete definition so callers can access entity-area settings without casting.
     *
     * @return this trait's {@link EntityHandlerTraitDefinition}
     */
    @Override
    public EntityHandlerTraitDefinition getDefinition() {
        return (EntityHandlerTraitDefinition) super.getDefinition();
    }

    /**
     * Refreshes the input entity cache once per second while this handler accepts recipe input.
     *
     * <p>The configured area is rotated by the machine front, moved to the machine position, and queried for living
     * entities. A listener notification is fired only when the detected entity set changes. The method performs no
     * work for output-only handlers.</p>
     */
    @Override
    public void serverTick() {
        if (getHandlerIO() == IO.IN && getMachine().getOffsetTimer() % 20 == 0) {
            if (entitiesLock.tryLock()) {
                var area = getDefinition().getArea(getMachine().getFrontFacing().orElse(null));
                area = area.move(getMachine().getPos());
                var detected = getMachine().getLevel().getEntities((Entity) null, area, Entity::isAlive);
                if (detected.size() != entities.size() || !new HashSet<>(detected).containsAll(entities)) {
                    entities.clear();
                    entities.addAll(detected);
                    notifyListeners();
                }
                entitiesLock.unlock();
            }
        }
    }

    /**
     * Exposes the single recipe handler that consumes or produces {@link EntityIngredient} contents.
     *
     * @return an immutable singleton list owned by this trait
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(handler);
    }

    /**
     * Recipe handler implementation for entity ingredients.
     *
     * <p>The handler mutates the supplied remainder list in the same style as other recipe capabilities: a
     * {@code null} result means the requested contents were fully handled, while a non-null list contains the still
     * unmatched ingredients.</p>
     */
    public class EntityRecipeHandler extends RecipeHandlerTrait<EntityIngredient> {

        protected EntityRecipeHandler() {
            super(EntityHandlerTrait.this, EntityRecipeCapability.CAP);
        }

        /**
         * Matches, removes, or spawns entities for one recipe capability pass.
         *
         * <p>For {@link IO#IN}, matching uses the current cached entity list. Simulation copies that list and never
         * removes world entities; a committed pass records consumed entity types and discards the matched entities.
         * For {@link IO#OUT}, simulation reports success without spawning, while a committed pass spawns every
         * requested entity type/count combination in the configured area. The input {@code left} list and contained
         * ingredient counts are expected to be mutable and may be reduced in-place.</p>
         *
         * @param io       requested recipe direction
         * @param recipe   recipe currently being checked or executed
         * @param left     mutable remaining entity ingredients to satisfy
         * @param slotName optional logical slot name used for consumption tracking
         * @param simulate {@code true} to check availability without changing the world
         * @return {@code null} when fully handled, the remaining list when partially handled or incompatible
         */
        @Override
        public List<EntityIngredient> handleRecipeInner(IO io, MBDRecipe recipe, List<EntityIngredient> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            if (io == IO.OUT) {
                if (!simulate && getMachine().getLevel() instanceof ServerLevel serverLevel) {
                    // spawn entities
                    var area = getDefinition().getArea(getMachine().getFrontFacing().orElse(null));
                    area = area.move(getMachine().getPos());
                    for (EntityIngredient entityIngredient : left) {
                        for (EntityType<?> type : entityIngredient.getTypes()) {
                            for (int i = 0; i < entityIngredient.getCount(); i++) {
                                spawnEntity(serverLevel, area, entityIngredient, type);
                            }
                        }
                    }
                }
                return null;
            } else if (io == IO.IN) {
                entitiesLock.lock();
                var entityList = simulate ? new ArrayList<>(entities) : entities;
                entitiesLock.unlock();
                var iterator = left.iterator();
                while (iterator.hasNext()) {
                    var ingredient = iterator.next();
                    var types = Arrays.stream(ingredient.getTypes()).collect(Collectors.toSet());
                    var toKilled = new ArrayList<Entity>();
                    var matchCount = 0;
                    for (var entity : entityList) {
                        if (matchCount >= ingredient.getCount()) {
                            break;
                        }
                        if (entity.isAlive() && types.contains(entity.getType())) {
                            var nbt = ingredient.getNbt();
                            if (nbt != null && !nbt.isEmpty()) {
                                var held = entity.serializeNBT();
                                if (!held.copy().merge(nbt).equals(held)) {
                                    continue;
                                }
                            }
                            matchCount++;
                            toKilled.add(entity);
                        }
                    }
                    ingredient.setCount(ingredient.getCount() - matchCount);
                    if (ingredient.getCount() <= 0) {
                        iterator.remove();
                    }
                    if (!simulate) {
                        Map<EntityType<?>, Integer> consumed = new HashMap<>();
                        toKilled.forEach(entity -> consumed.merge(entity.getType(), 1, Integer::sum));
                        consumed.forEach((type, count) -> RecipeConsumptionTracker.record(EntityRecipeCapability.CAP,
                                EntityIngredient.of(Stream.of(type), count, null), slotName));
                        toKilled.forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED));
                    }
                    entityList.removeAll(toKilled);
                }
            }
            return left.isEmpty() ? null : left;
        }

        /**
         * Spawns one entity at a random point inside the configured area and applies ingredient NBT afterward.
         *
         * <p>The spawn is server-side only. If the entity type refuses to spawn, the method silently leaves the
         * recipe output missing; this matches vanilla {@link EntityType#spawn} behavior and avoids creating a dummy
         * entity.</p>
         *
         * @param serverLevel      target server level
         * @param area             world-space spawn volume
         * @param entityIngredient ingredient that may provide NBT overrides
         * @param type             entity type to spawn
         */
        private void spawnEntity(ServerLevel serverLevel, net.minecraft.world.phys.AABB area, EntityIngredient entityIngredient, EntityType<?> type) {
            var pos = new Vec3((area.minX + Math.random() * area.getXsize()),
                    (area.minY + Math.random() * area.getYsize()),
                    (area.minZ + Math.random() * area.getZsize()));
            var entity = type.spawn(serverLevel, new BlockPos((int) pos.x, (int) pos.y, (int) pos.z), MobSpawnType.SPAWNER);
            if (entity != null) {
                if (entityIngredient.getNbt() != null) {
                    var tag = entity.serializeNBT();
                    tag.merge(entityIngredient.getNbt());
                    entity.load(tag);
                }
                entity.moveTo(pos);
            }
        }
    }
}
