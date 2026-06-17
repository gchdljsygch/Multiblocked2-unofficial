package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fired after a multiblock controller invalidates its structure.
 * <p>
 * The supplied {@link #parts} list is a snapshot of parts that were attached
 * before invalidation. Controller part membership, proxy blocks, recipe logic,
 * and capability routing have already been reset.
 */
@Getter
@LDLRegister(name = "MachineStructureInvalidEvent", group = "MachineEvent.Multiblock")
public class MachineStructureInvalidEvent extends MachineEvent {
    /**
     * Parts that belonged to the structure before it was invalidated.
     */
    @GraphParameterGet
    public final List<IMultiPart> parts;

    /**
     * Creates an event for a multiblock structure invalidation.
     * <p>
     * The supplied part list is a caller-owned snapshot of parts that belonged to the structure before invalidation.
     * Treat it as read-only unless the caller explicitly documents otherwise.
     *
     * @param machine controller machine whose structure was invalidated
     * @param parts   parts formerly attached to the structure
     */
    public MachineStructureInvalidEvent(MBDMachine machine, List<IMultiPart> parts) {
        super(machine);
        this.parts = parts;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("parts")).ifPresent(p -> p.setValue(parts));
    }
}
