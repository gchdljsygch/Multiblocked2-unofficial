package com.lowdragmc.mbd2.integration.kubejs.events;

import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import dev.latvian.mods.kubejs.entity.EntityEventJS;
import dev.latvian.mods.kubejs.event.EventGroup;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class MBDEntityMachineEvents {
    public static EventGroup MBD_ENTITY_MACHINE_EVENTS = EventGroup.of("MBDEntityMachineEvents");

    @Getter
    public static class EntityMachineEventJS extends EntityEventJS {
        public final MBDEntityMachine machine;
        public final Entity entity;

        public EntityMachineEventJS(MBDEntityMachine machine, Entity entity) {
            this.machine = machine;
            this.entity = entity;
        }

        @Override
        public Entity getEntity() {
            return entity;
        }

        public EntityMachineDefinition getDefinition() {
            return machine.getDefinition();
        }
    }

    public static class EntityMachineSpawnedEventJS extends EntityMachineEventJS {
        public EntityMachineSpawnedEventJS(MBDEntityMachine machine, Entity entity) {
            super(machine, entity);
        }
    }

    @Getter
    public static class EntityMachineRemovedEventJS extends EntityMachineEventJS {
        public final Entity.RemovalReason reason;

        public EntityMachineRemovedEventJS(MBDEntityMachine machine, Entity entity, Entity.RemovalReason reason) {
            super(machine, entity);
            this.reason = reason;
        }
    }

    @Getter
    public static class EntityMachineInteractEventJS extends EntityMachineEventJS {
        public final Player player;
        public final InteractionHand hand;

        public EntityMachineInteractEventJS(MBDEntityMachine machine, Entity entity, Player player, InteractionHand hand) {
            super(machine, entity);
            this.player = player;
            this.hand = hand;
        }

        public ItemStack getItem() {
            return player.getItemInHand(hand);
        }
    }
}
