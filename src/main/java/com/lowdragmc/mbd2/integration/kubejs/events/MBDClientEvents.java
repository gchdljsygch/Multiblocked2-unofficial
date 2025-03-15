package com.lowdragmc.mbd2.integration.kubejs.events;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.machine.definition.config.event.*;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.Extra;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.lowdragmc.mbd2.integration.kubejs.events.MBDMachineEvents.MBD_MACHINE_EVENTS;

public interface MBDClientEvents {
    Map<Class<? extends MachineEvent>, Consumer<MachineEvent>> eventHandlers = new HashMap<>();

    // Client events
    EventHandler CLIENT_TICK = registerMachineEvent("onClientTick",
            MachineClientTickEvent.class,
            MBDMachineEvents.MachineClientTickEventJS.class,
            MBDMachineEvents.MachineClientTickEventJS::new);

    EventHandler CUSTOM_DATA_UPDATE = registerMachineEvent("onCustomDataUpdate",
            MachineCustomDataUpdateEvent.class,
            MBDMachineEvents.MachineCustomDataUpdateEventJS.class,
            MBDMachineEvents.MachineCustomDataUpdateEventJS::new);

    @Nullable
    EventHandler CUSTOM_KEYFRAME = createCustomKeyframeEvent();

    static EventHandler createCustomKeyframeEvent() {
        if (MBD2.isGeckolibLoaded()) {
            return registerMachineEvent("onCustomKeyframe",
                    MachineCustomKeyframeEvent.class,
                    MBDMachineEvents.MachineCustomKeyframeEventJS.class,
                    MBDMachineEvents.MachineCustomKeyframeEventJS::new);
        }
        return null;
    }

    static void init() {
        // NO-OP
    }

    static <E extends MachineEvent> EventHandler registerMachineEvent(String name, Class<E> eventClass,
                                                                      Class<? extends MBDMachineEvents.MachineEventJS<E>> eventJSClass,
                                                                      Function<E, MBDMachineEvents.MachineEventJS<E>> eventJSFactory) {
        var handler = MBD_MACHINE_EVENTS.client(name, () -> eventJSClass).extra(Extra.ID);
        eventHandlers.put(eventClass, event -> handler.post(eventJSFactory.apply((E) event), event.machine.getDefinition().id()));
        return handler;
    }
    
    static void postMachineEvent(MachineEvent machineEvent) {
        Optional.ofNullable(eventHandlers.get(machineEvent.getClass())).ifPresent(handler -> handler.accept(machineEvent));
    }
}
