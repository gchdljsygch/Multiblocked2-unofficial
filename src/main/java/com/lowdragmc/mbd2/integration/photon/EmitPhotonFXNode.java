package com.lowdragmc.mbd2.integration.photon;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.trigger.LinearTriggerNode;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Graph node that starts a Photon FX instance attached to an MBD machine.
 */
@LDLRegister(name = "emit photon fx", group = "graph_processor.node.mbd2.machine.photon", modID = "photon")
public class EmitPhotonFXNode extends LinearTriggerNode {
    @InputPort
    public MBDMachine machine;
    @InputPort(name = "identifier", tips = "graph_processor.node.mbd2.machine.photon.identifier")
    public String identifier;
    @InputPort(name = "fx location")
    public String fxLocation;
    @InputPort
    public Vector3f offset;
    @InputPort
    public Vector3f rotation;
    @InputPort
    public int delay;
    @InputPort(name = "forced death", tips = {"graph_processor.node.mbd2.machine.photon.force_death.tips.0",
            "graph_processor.node.mbd2.machine.photon.force_death.tips.1", "graph_processor.node.mbd2.machine.photon.force_death.tips.2"})
    public boolean forcedDeath;
    @InputPort(name = "replace existing", tips = {
            "graph_processor.node.mbd2.machine.photon.replace_existing.0",
            "graph_processor.node.mbd2.machine.photon.replace_existing.1"
    })
    public boolean replaceExisting;

    @Override
    protected void process() {
        var fxId = fxLocation == null ? null : ResourceLocation.tryParse(fxLocation);
        if (machine != null && identifier != null && fxId != null) {
            machine.emitPhotonFx(identifier, fxId,
                    offset == null ? new Vector3f() : offset,
                    rotation == null ? new Vector3f() : rotation, delay, forcedDeath, replaceExisting);
        }
    }
}
