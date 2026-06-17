package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import lombok.*;
import lombok.experimental.Accessors;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted general settings for a block-backed machine definition.
 * <p>
 * This config controls machine level, UI availability, block drop behavior,
 * redstone connection defaults, and the list of trait definitions that will be
 * instantiated by {@link com.lowdragmc.mbd2.common.machine.MBDMachine}.
 */
@Accessors(fluent = true)
@Builder
public class ConfigMachineSettings implements IPersistedSerializable, IConfigurable {
    /**
     * Side-relative redstone connection settings.
     * <p>
     * Horizontal sides are interpreted relative to the machine front facing;
     * top and bottom are absolute world directions.
     */
    @Getter
    @Setter
    public static class SignalConnection {
        @Configurable(name = "config.machine_settings.signal_connection.front")
        private boolean frontConnection = false;
        @Configurable(name = "config.machine_settings.signal_connection.back")
        private boolean backConnection = false;
        @Configurable(name = "config.machine_settings.signal_connection.left")
        private boolean leftConnection = false;
        @Configurable(name = "config.machine_settings.signal_connection.right")
        private boolean rightConnection = false;
        @Configurable(name = "config.machine_settings.signal_connection.top")
        private boolean topConnection = false;
        @Configurable(name = "config.machine_settings.signal_connection.bottom")
        private boolean bottomConnection = false;

        /**
         * Resolves whether redstone can connect on a world side.
         *
         * @param front current machine front direction
         * @param side  queried world side
         * @return {@code true} when this config allows a connection
         */
        public boolean getConnection(Direction front, Direction side) {
            if (side == Direction.UP) {
                return topConnection;
            } else if (side == Direction.DOWN) {
                return bottomConnection;
            } else if (side == front) {
                return frontConnection;
            } else if (side == front.getOpposite()) {
                return backConnection;
            } else if (side == front.getClockWise()) {
                return rightConnection;
            } else if (side == front.getCounterClockWise()) {
                return leftConnection;
            }
            return false;
        }
    }

    @Getter
    @Builder.Default
    @Configurable(name = "config.machine_settings.machine_level", tips = "config.machine_settings.machine_level.tooltip")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int machineLevel = 0;
    @Getter
    @Builder.Default
    @Configurable(name = "config.machine_settings.has_ui", tips = "config.machine_settings.has_ui.tooltip")
    private boolean hasUI = true;
    @Getter
    @Builder.Default
    @Configurable(name = "config.machine_settings.drop_machine_item", tips = {
            "config.machine_settings.drop_machine_item.tooltip.0",
            "config.machine_settings.drop_machine_item.tooltip.1",
            "config.machine_settings.drop_machine_item.tooltip.2",
    })
    private boolean dropMachineItem = true;
    @Getter
    @Builder.Default
    @Configurable(name = "config.machine_settings.signal_connection", subConfigurable = true,
            tips = {"config.machine_settings.signal_connection.tooltip.0", "config.machine_settings.signal_connection.tooltip.1"})
    private final SignalConnection signalConnection = new SignalConnection();
    @Singular
    @NonNull
    @Getter
    private List<TraitDefinition> traitDefinitions;

    @Override
    public CompoundTag serializeNBT() {
        var tag = IPersistedSerializable.super.serializeNBT();
        var traits = new ListTag();
        for (var definition : traitDefinitions) {
            traits.add(TraitDefinition.serializeDefinition(definition));
        }
        tag.put("traitDefinitions", traits);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        IPersistedSerializable.super.deserializeNBT(tag);
        var traits = tag.getList("traitDefinitions", 10);
        traitDefinitions = new ArrayList<>();
        for (var i = 0; i < traits.size(); i++) {
            var trait = traits.getCompound(i);
            var definition = TraitDefinition.deserializeDefinition(trait);
            if (definition != null) {
                traitDefinitions.add(definition);
            }
        }
    }

    /**
     * Adds a trait definition to this machine config.
     *
     * @param definition trait definition to instantiate for new machine
     *                   runtimes
     */
    public void addTraitDefinition(TraitDefinition definition) {
        traitDefinitions = new ArrayList<>(traitDefinitions);
        traitDefinitions.add(definition);
    }

    /**
     * Removes a trait definition by object identity.
     *
     * @param definition trait definition instance to remove
     */
    public void removeTraitDefinition(TraitDefinition definition) {
        traitDefinitions = this.traitDefinitions.stream().filter(s -> s != definition).toList();
    }

}
