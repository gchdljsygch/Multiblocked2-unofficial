package com.lowdragmc.mbd2.integration.kubejs.events;

import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineFixedTickEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineInteractEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineRemovedEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineSpawnedEvent;
import com.lowdragmc.mbd2.common.machine.definition.config.event.EntityMachineTickEvent;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.Extra;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.MapJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public interface MBDEntityMachineEventDispatcher {
    Map<Class<? extends EntityMachineEvent>, Function<EntityMachineEvent, EventResult>> entityMachineEventHandlers = new HashMap<>();

    EventHandler SPAWNED = registerEntityMachineEvent("spawned",
            EntityMachineSpawnedEvent.class,
            MBDEntityMachineEvents.EntityMachineSpawnedEventJS.class,
            MBDEntityMachineEvents.EntityMachineSpawnedEventJS::new);
    EventHandler REMOVED = registerEntityMachineEvent("removed",
            EntityMachineRemovedEvent.class,
            MBDEntityMachineEvents.EntityMachineRemovedEventJS.class,
            MBDEntityMachineEvents.EntityMachineRemovedEventJS::new);
    EventHandler INTERACT = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server("interact",
            () -> MBDEntityMachineEvents.EntityMachineInteractEventJS.class).extra(Extra.ID);
    EventHandler TICK = registerEntityMachineEvent("tick",
            EntityMachineTickEvent.class,
            MBDEntityMachineEvents.EntityMachineTickEventJS.class,
            MBDEntityMachineEvents.EntityMachineTickEventJS::new);
    EventHandler FIXED_TICK = registerEntityMachineEvent("fixedTick",
            EntityMachineFixedTickEvent.class,
            MBDEntityMachineEvents.EntityMachineFixedTickEventJS.class,
            MBDEntityMachineEvents.EntityMachineFixedTickEventJS::new);
    EventHandler FIXED_TICK_EVERY = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server("fixedTickEvery",
            () -> MBDEntityMachineEvents.EntityMachineFixedTickEventJS.class).extra(FixedTickKey.EXTRA);

    static void init() {
        // NO-OP
    }

    static void postSpawned(MBDEntityMachine machine, Entity entity) {
        postEvent(new EntityMachineSpawnedEvent(machine, entity));
    }

    static void postRemoved(MBDEntityMachine machine, Entity entity, Entity.RemovalReason reason) {
        postEvent(new EntityMachineRemovedEvent(machine, entity, reason));
    }

    static void postInteract(MBDEntityMachine machine, Entity entity, Player player, InteractionHand hand) {
        var machineId = machineId(machine);
        INTERACT.post(new MBDEntityMachineEvents.EntityMachineInteractEventJS(machine, entity, player, hand), machineId);
    }

    static ResourceLocation machineId(MBDEntityMachine machine) {
        return machine.getDefinition().id();
    }

    static <E extends EntityMachineEvent> EventHandler registerEntityMachineEvent(String name, Class<E> eventClass,
                                                                                  Class<? extends MBDEntityMachineEvents.EntityMachineEventJS> eventJSClass,
                                                                                  Function<E, MBDEntityMachineEvents.EntityMachineEventJS> eventJSFactory) {
        var handler = MBDEntityMachineEvents.MBD_ENTITY_MACHINE_EVENTS.server(name, () -> eventJSClass).extra(Extra.ID);
        entityMachineEventHandlers.put(eventClass, event -> handler.post(eventJSFactory.apply((E) event), machineId(event.getMachine())));
        return handler;
    }

    static EventResult postEvent(EntityMachineEvent event) {
        if (event instanceof EntityMachineInteractEvent interactEvent) {
            postInteract(interactEvent.getMachine(), interactEvent.entity, interactEvent.player, interactEvent.hand);
        }
        return Optional.ofNullable(entityMachineEventHandlers.get(event.getClass()))
                .map(handler -> handler.apply(event))
                .orElse(EventResult.PASS);
    }

    static EventResult postEntityMachineFixedTickEvery(MBDEntityMachine machine, Entity entity, long timer) {
        if (!FIXED_TICK_EVERY.hasListeners()) {
            return EventResult.PASS;
        }
        var machineId = machine.getDefinition().id();
        var result = EventResult.PASS;
        for (var object : collectFixedTickKeys()) {
            if (object instanceof FixedTickKey key && key.machineId.equals(machineId) && timer % key.interval == 0) {
                var event = new EntityMachineFixedTickEvent(machine, entity, key.interval, timer);
                var postResult = FIXED_TICK_EVERY.post(new MBDEntityMachineEvents.EntityMachineFixedTickEventJS(event), key);
                if (postResult != EventResult.PASS) {
                    result = postResult;
                }
            }
        }
        return result;
    }

    static Set<Object> collectFixedTickKeys() {
        var keys = new LinkedHashSet<>();
        keys.addAll(FIXED_TICK_EVERY.findUniqueExtraIds(ScriptType.SERVER));
        keys.addAll(FIXED_TICK_EVERY.findUniqueExtraIds(ScriptType.STARTUP));
        return keys;
    }

    record FixedTickKey(ResourceLocation machineId, int interval) {
        static final Extra EXTRA = new Extra()
                .transformer(FixedTickKey::of)
                .validator(FixedTickKey.class::isInstance)
                .toString(value -> value.toString())
                .required();

        static FixedTickKey of(Object value) {
            if (value instanceof FixedTickKey key) {
                return key;
            }
            if (value instanceof CharSequence chars) {
                var raw = chars.toString();
                var at = raw.lastIndexOf('@');
                if (at > 0 && at < raw.length() - 1) {
                    return new FixedTickKey(parseMachineId(raw.substring(0, at)), parseInterval(raw.substring(at + 1)));
                }
            }
            var map = MapJS.of(value);
            if (map != null && !map.isEmpty()) {
                var machineId = firstPresent(map.get("id"), map.get("machine"), map.get("machineId"))
                        .map(FixedTickKey::parseMachineId)
                        .orElseThrow(() -> new IllegalArgumentException("Missing entity machine id for fixed tick listener"));
                var interval = firstPresent(map.get("interval"), map.get("ticks"), map.get("step"))
                        .map(FixedTickKey::parseInterval)
                        .orElseThrow(() -> new IllegalArgumentException("Missing interval for entity machine fixed tick listener"));
                return new FixedTickKey(machineId, interval);
            }
            throw new IllegalArgumentException("Use { id: 'namespace:machine', interval: ticks } or 'namespace:machine@ticks'");
        }

        static Optional<Object> firstPresent(Object... values) {
            for (var value : values) {
                if (value != null) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        static ResourceLocation parseMachineId(Object id) {
            if (id instanceof ResourceLocation resourceLocation) {
                return resourceLocation;
            }
            var location = ResourceLocation.tryParse(String.valueOf(id));
            if (location == null) {
                throw new IllegalArgumentException("Invalid entity machine id: " + id);
            }
            return location;
        }

        static int parseInterval(Object interval) {
            var value = interval instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(interval));
            if (value < 1) {
                throw new IllegalArgumentException("Fixed tick interval must be at least 1");
            }
            return value;
        }

        @Override
        public String toString() {
            return machineId + "@" + interval;
        }
    }
}
