package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.integration.geckolib.AnimatableMachine;
import lombok.Getter;
import lombok.Setter;
import software.bernie.geckolib.core.keyframe.event.CustomInstructionKeyframeEvent;

/**
 * Fired when Geckolib emits a custom instruction keyframe for a machine
 * animation.
 * <p>
 * Registered only when the Geckolib integration is available. The event is a
 * client animation hook and should be used for visuals/effects, not
 * authoritative server state.
 */
@Getter
@Setter
@LDLRegister(name = "MachineCustomKeyframeEvent", group = "MachineEvent", modID = "geckolib")
public class MachineCustomKeyframeEvent extends MachineEvent {
    /**
     * Original Geckolib keyframe event.
     */
    public CustomInstructionKeyframeEvent<AnimatableMachine> event;
    /**
     * Raw custom instruction string from the keyframe.
     */
    @GraphParameterGet
    public String instruction;

    /**
     * Creates an event for a Geckolib custom instruction keyframe.
     * <p>
     * Side effect: snapshots the keyframe instruction string into {@link #instruction} for graph handlers. This is a
     * client animation hook and should remain visual-only.
     *
     * @param machine machine whose animation emitted the keyframe
     * @param event   original Geckolib keyframe event
     */
    public MachineCustomKeyframeEvent(MBDMachine machine, CustomInstructionKeyframeEvent<AnimatableMachine> event) {
        super(machine);
        this.event = event;

        instruction = event.getKeyframeData().getInstructions();
    }

}
