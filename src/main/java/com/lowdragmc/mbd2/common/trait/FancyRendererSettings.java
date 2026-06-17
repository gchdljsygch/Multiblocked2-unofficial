package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared configuration and cache for optional in-world trait renderers.
 *
 * <p>Subclasses create the actual renderer for a capability type. This base class gates renderer creation by the
 * enable flag and optional machine-state filters, then caches the renderer instance for reuse. Configuration vectors
 * are client-render transforms in block-local coordinates; consumers apply them when rendering.</p>
 */
public abstract class FancyRendererSettings implements IToggleConfigurable {
    @Getter
    @Setter
    @Persisted
    private boolean enable;

    @Configurable(name = "config.definition.trait.filter.whitelist")
    protected boolean isWhiteList;
    @Configurable(name = "config.definition.trait.fancy_renderer.filters", collapse = false, tips = "config.definition.trait.fancy_renderer.filters.tooltip")
    protected List<String> filters = new ArrayList<>();

    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.position", tips = "config.definition.trait.fancy_renderer.position.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    protected Vector3f position = new Vector3f(0, 0, 0);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.rotation", tips = "config.definition.trait.fancy_renderer.rotation.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    protected Vector3f rotation = new Vector3f(0, 0, 0);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.scale", tips = "config.definition.trait.fancy_renderer.scale.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    protected Vector3f scale = new Vector3f(1, 1, 1);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.rotate_orientation", tips = "config.definition.trait.fancy_renderer.rotate_orientation.tooltip")
    protected boolean rotateOrientation = true;

    // run-time;
    private Set<String> filterSet;
    private IRenderer renderer;

    /**
     * Returns the renderer that should be used for the supplied machine.
     *
     * <p>Disabled settings and filtered-out runtime machine states return {@link IRenderer#EMPTY}. Dummy-world
     * editor previews bypass state filtering so the configured renderer can be inspected even without a real machine
     * state. The created renderer is cached until {@link #clearCache()} is called.</p>
     *
     * @param machine machine requesting a block entity renderer
     * @return cached renderer, newly created renderer, or {@link IRenderer#EMPTY}
     */
    public IRenderer getFancyRenderer(IMachine machine) {
        if (!enable) return IRenderer.EMPTY;
        if (filterSet == null) filterSet = new HashSet<>(filters);
        if (!(machine.getLevel() instanceof DummyWorld)) {
            var state = ((MBDMachine) machine).getMachineStateName();
            if (isWhiteList) {
                if (!filterSet.contains(state)) return IRenderer.EMPTY;
            } else {
                if (filterSet.contains(state)) return IRenderer.EMPTY;
            }
        }
        if (renderer == null) renderer = createFancyRenderer();
        return renderer;
    }

    /**
     * Drops the cached renderer so subsequent calls recreate it from current subclass settings.
     */
    public void clearCache() {
        renderer = null;
    }

    /**
     * Creates the capability-specific renderer.
     *
     * <p>Called lazily by {@link #getFancyRenderer(IMachine)} only after enable/filter checks pass.</p>
     *
     * @return renderer instance for this settings object
     */
    public abstract IRenderer createFancyRenderer();
}
