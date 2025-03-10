package com.lowdragmc.mbd2.integration.kubejs.events;

import com.lowdragmc.mbd2.common.machine.definition.config.event.*;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.Extra;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface MBDServerEvents {
    Map<Class<? extends MachineEvent>, Consumer<MachineEvent>> eventHandlers = new HashMap<>();

    // Server events
    EventHandler AFTER_RECIPE_WORKING = registerMachineEvent("onAfterRecipeWorking",
            MachineAfterRecipeWorkingEvent.class,
            MBDMachineEvents.MachineAfterRecipeWorkingEventJS.class,
            MBDMachineEvents.MachineAfterRecipeWorkingEventJS::new);

    EventHandler BEFORE_RECIPE_WORKING = registerMachineEvent("onBeforeRecipeWorking",
            MachineBeforeRecipeWorkingEvent.class,
            MBDMachineEvents.MachineBeforeRecipeWorkingEventJS.class,
            MBDMachineEvents.MachineBeforeRecipeWorkingEventJS::new);

    EventHandler DROPS = registerMachineEvent("onDrops",
            MachineDropsEvent.class,
            MBDMachineEvents.MachineDropsEventJS.class,
            MBDMachineEvents.MachineDropsEventJS::new);

    EventHandler NEIGHBOR_CHANGED = registerMachineEvent("onNeighborChanged",
            MachineNeighborChangedEvent.class,
            MBDMachineEvents.MachineNeighborChangedEventJS.class,
            MBDMachineEvents.MachineNeighborChangedEventJS::new);

    EventHandler ON_LOAD = registerMachineEvent("onLoad",
            MachineOnLoadEvent.class,
            MBDMachineEvents.MachineOnLoadEventJS.class,
            MBDMachineEvents.MachineOnLoadEventJS::new);

    EventHandler ON_RECIPE_WORKING = registerMachineEvent("onRecipeWorking",
            MachineOnRecipeWorkingEvent.class,
            MBDMachineEvents.MachineOnRecipeWorkingEventJS.class,
            MBDMachineEvents.MachineOnRecipeWorkingEventJS::new);

    EventHandler ON_RECIPE_WAITING = registerMachineEvent("onRecipeWaiting",
            MachineOnRecipeWaitingEvent.class,
            MBDMachineEvents.MachineOnRecipeWaitingEventJS.class,
            MBDMachineEvents.MachineOnRecipeWaitingEventJS::new);

    EventHandler OPEN_UI = registerMachineEvent("onOpenUI",
            MachineOpenUIEvent.class,
            MBDMachineEvents.MachineOpenUIEventJS.class,
            MBDMachineEvents.MachineOpenUIEventJS::new);

    EventHandler PLACED = registerMachineEvent("onPlaced",
            MachinePlacedEvent.class,
            MBDMachineEvents.MachinePlacedEventJS.class,
            MBDMachineEvents.MachinePlacedEventJS::new);

    EventHandler FUEL_RECIPE_MODIFY = registerMachineEvent("onFuelRecipeModify",
            MachineFuelRecipeModifyEvent.class,
            MBDMachineEvents.MachineFuelRecipeModifyEventJS.class,
            MBDMachineEvents.MachineFuelRecipeModifyEventJS::new);

    EventHandler FUEL_BURNING_FINISH = registerMachineEvent("onFuelBurningFinish",
            MachineFuelBurningFinishEvent.class,
            MBDMachineEvents.MachineFuelBurningFinishEventJS.class,
            MBDMachineEvents.MachineFuelBurningFinishEventJS::new);

    EventHandler BEFORE_RECIPE_MODIFY = registerMachineEvent("onBeforeRecipeModify",
            MachineRecipeModifyEvent.Before.class,
            MBDMachineEvents.MachineRecipeModifyEventBeforeJS.class,
            MBDMachineEvents.MachineRecipeModifyEventBeforeJS::new);

    EventHandler AFTER_RECIPE_MODIFY = registerMachineEvent("onAfterRecipeModify",
            MachineRecipeModifyEvent.After.class,
            MBDMachineEvents.MachineRecipeModifyEventAfterJS.class,
            MBDMachineEvents.MachineRecipeModifyEventAfterJS::new);

    EventHandler RECIPE_STATUS_CHANGED = registerMachineEvent("onRecipeStatusChanged",
            MachineRecipeStatusChangedEvent.class,
            MBDMachineEvents.MachineRecipeStatusChangedEventJS.class,
            MBDMachineEvents.MachineRecipeStatusChangedEventJS::new);

    EventHandler REMOVED = registerMachineEvent("onRemoved",
            MachineRemovedEvent.class,
            MBDMachineEvents.MachineRemovedEventJS.class,
            MBDMachineEvents.MachineRemovedEventJS::new);

    EventHandler RIGHT_CLICK = registerMachineEvent("onRightClick",
            MachineRightClickEvent.class,
            MBDMachineEvents.MachineRightClickEventJS.class,
            MBDMachineEvents.MachineRightClickEventJS::new);

    EventHandler STATE_CHANGED = registerMachineEvent("onStateChanged",
            MachineStateChangedEvent.class,
            MBDMachineEvents.MachineStateChangedEventJS.class,
            MBDMachineEvents.MachineStateChangedEventJS::new);

    EventHandler STRUCTURE_FORMED = registerMachineEvent("onStructureFormed",
            MachineStructureFormedEvent.class,
            MBDMachineEvents.MachineStructureFormedEventJS.class,
            MBDMachineEvents.MachineStructureFormedEventJS::new);

    EventHandler STRUCTURE_INVALID = registerMachineEvent("onStructureInvalid",
            MachineStructureInvalidEvent.class,
            MBDMachineEvents.MachineStructureInvalidEventJS.class,
            MBDMachineEvents.MachineStructureInvalidEventJS::new);

    EventHandler TICK = registerMachineEvent("onTick",
            MachineTickEvent.class,
            MBDMachineEvents.MachineTickEventJS.class,
            MBDMachineEvents.MachineTickEventJS::new);

    EventHandler USE_CATALYST = registerMachineEvent("onUseCatalyst",
            MachineUseCatalystEvent.class,
            MBDMachineEvents.MachineUseCatalystEventJS.class,
            MBDMachineEvents.MachineUseCatalystEventJS::new);

    EventHandler MACHINE_UI = registerMachineEvent("onUI",
            MachineUIEvent.class,
            MBDMachineEvents.MachineUIEventJS.class,
            MBDMachineEvents.MachineUIEventJS::new);

    EventHandler CUSTOM_KEYFRAME = registerMachineEvent("onCustomKeyframeTrigger",
            MachineCustomKeyframeEvent.class,
            MBDMachineEvents.MachineCustomKeyframeEventJS.class,
            MBDMachineEvents.MachineCustomKeyframeEventJS::new);

    static void init() {
        // NO-OP
    }

    static <E extends MachineEvent> EventHandler registerMachineEvent(String name, Class<E> eventClass,
                                                                      Class<? extends MBDMachineEvents.MachineEventJS<E>> eventJSClass,
                                                                      Function<E, MBDMachineEvents.MachineEventJS<E>> eventJSFactory) {
        var handler = MBDMachineEvents.MBD_MACHINE_EVENTS.server(name, () -> eventJSClass).extra(Extra.ID);
        eventHandlers.put(eventClass, event -> handler.post(eventJSFactory.apply((E) event), event.machine.getDefinition().id()));
        return handler;
    }
    
    static void postMachineEvent(MachineEvent machineEvent) {
        Optional.ofNullable(eventHandlers.get(machineEvent.getClass())).ifPresent(handler -> handler.accept(machineEvent));
    }
}
