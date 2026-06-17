package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;

/**
 * Graph node that produces a modified copy of an {@link MBDRecipe}.
 *
 * <p>Content modifiers are applied to the selected IO side and duration modifiers are applied to recipe duration. The
 * original recipe is preserved unless no modifier is supplied, in which case {@link #out} references {@link #in}
 * directly. The node is graph-execution state and should only be used from the graph processor thread.</p>
 */
@LDLRegister(name = "modify recipe", group = "graph_processor.node.mbd2.machine.recipe")
public class ModifyRecipeNode extends BaseNode {
    /**
     * Recipe to copy and modify; {@code null} yields {@code null}.
     */
    @InputPort
    public MBDRecipe in;
    /**
     * Modifier for recipe contents.
     */
    @InputPort(name = "content modifier")
    public ContentModifier contentModifier;
    /**
     * IO side selected by an upstream node; when absent {@link #internalContentIO} is used.
     */
    @InputPort(name = "content side")
    public IO contentIO;
    /**
     * Modifier applied to recipe duration.
     */
    @InputPort(name = "duration modifier")
    public ContentModifier durationModifier;

    /**
     * Modified recipe output, or the input recipe when no copy is required.
     */
    @OutputPort
    public MBDRecipe out;

    /**
     * Configured IO side used when {@link #contentIO} is not connected.
     */
    @Configurable(name = "content side")
    public IO internalContentIO = IO.BOTH;

    /**
     * Applies non-identity modifiers while preserving the original recipe when possible.
     */
    @Override
    protected void process() {
        out = in;
        if (in != null) {
            var copied = false;
            var io = contentIO == null ? internalContentIO : contentIO;
            if (contentModifier != null && !contentModifier.isIdentity() && io != IO.NONE) {
                out = in.copy(contentModifier, false, io);
                copied = true;
            }
            if (durationModifier != null && !durationModifier.isIdentity()) {
                if (copied) {
                    out.duration = durationModifier.apply(out.duration).intValue();
                } else {
                    out = in.copy(durationModifier, true, IO.NONE);
                }
            }
        }
    }

    /**
     * Hides the internal IO configurator when the content-side input is wired.
     *
     * @param father configurator group receiving this node's editor controls
     */
    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        for (var port : getInputPorts()) {
            if (port.fieldName.equals("contentIO")) {
                if (!port.getEdges().isEmpty()) return;
            }
        }
        super.buildConfigurator(father);
    }

}
