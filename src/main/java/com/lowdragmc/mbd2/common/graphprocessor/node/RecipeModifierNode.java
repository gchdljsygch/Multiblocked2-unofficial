package com.lowdragmc.mbd2.common.graphprocessor.node;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.runtime.ConfiguratorParser;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.InputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.annotation.OutputPort;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.BaseNode;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;

import java.util.HashMap;

/**
 * Graph node that creates a {@link ContentModifier} from multiplier and addition values.
 *
 * <p>Wired inputs take priority over configured fallback values. The multiplier is intended to be non-negative, while
 * addition may be any finite float accepted by the editor range. The produced modifier can be fed into recipe/content
 * modification nodes.</p>
 */
@LDLRegister(name = "recipe modifier", group = "graph_processor.node.mbd2.machine.recipe")
public class RecipeModifierNode extends BaseNode {
    /**
     * Optional multiplier input; {@code null} uses {@link #internalMul}.
     */
    @InputPort
    public Float multiplier;
    /**
     * Optional additive offset input; {@code null} uses {@link #internalAdd}.
     */
    @InputPort
    public Float addition;
    /**
     * Resulting content modifier.
     */
    @OutputPort
    public ContentModifier modifier;

    /**
     * Configured multiplier fallback in the range {@code 0.0f..Float.MAX_VALUE}.
     */
    @Configurable(name = "multiplier")
    @NumberRange(range = {0, Float.MAX_VALUE})
    public float internalMul = 1;

    /**
     * Configured additive fallback in the editor's full float range.
     */
    @Configurable(name = "addition")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    public float internalAdd = 0;

    /**
     * Builds the modifier from wired values or configured fallbacks.
     */
    @Override
    protected void process() {
        var mul = multiplier == null ? internalMul : multiplier;
        var add = addition == null ? internalAdd : addition;
        modifier = ContentModifier.of(mul, add);
    }

    /**
     * Creates configurators only for inputs that are not wired to upstream nodes.
     *
     * @param father configurator group receiving this node's editor controls
     */
    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        var clazz = getClass();
        for (var port : getInputPorts()) {
            if (port.fieldName.equals("multiplier")) {
                if (port.getEdges().isEmpty()) {
                    try {
                        ConfiguratorParser.createFieldConfigurator(clazz.getField("internalMul"), father, clazz, new HashMap<>(), this);
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (port.fieldName.equals("addition")) {
                if (port.getEdges().isEmpty()) {
                    try {
                        ConfiguratorParser.createFieldConfigurator(clazz.getField("internalAdd"), father, clazz, new HashMap<>(), this);
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

}
