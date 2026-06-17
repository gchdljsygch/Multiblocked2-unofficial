package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.Optional;

/**
 * Fired when a machine's persisted custom NBT data changes.
 * <p>
 * The tags are the old and new managed-field values supplied by LowDragLib's
 * update listener. Treat them as snapshots for event logic unless the caller
 * explicitly owns the mutation.
 */
@LDLRegister(name = "MachineCustomDataUpdateEvent", group = "MachineEvent")
public class MachineCustomDataUpdateEvent extends MachineEvent {
    /**
     * Previous custom data tag.
     */
    @GraphParameterGet(displayName = "old data")
    public final CompoundTag oldValue;
    /**
     * New custom data tag.
     */
    @GraphParameterGet(displayName = "new data")
    public final CompoundTag newValue;

    /**
     * Creates an event for a custom-data NBT update.
     * <p>
     * The tags are references supplied by the sync/data listener. Treat them as snapshots for event logic; mutating them
     * can affect caller-owned state if the caller reuses the same tag instances.
     *
     * @param machine  machine whose custom data changed
     * @param newValue new custom data tag
     * @param oldValue previous custom data tag
     */
    public MachineCustomDataUpdateEvent(MBDMachine machine, CompoundTag newValue, CompoundTag oldValue) {
        super(machine);
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("oldValue")).ifPresent(p -> p.setValue(oldValue));
        Optional.ofNullable(exposedParameters.get("newValue")).ifPresent(p -> p.setValue(newValue));
    }
}
