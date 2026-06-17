package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.mbd2.common.gui.editor.texture.IRendererSlotTexture;
import com.lowdragmc.mbd2.common.machine.definition.config.toggle.ToggleRenderer;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Persisted renderer settings for entity-backed machine definitions.
 * <p>
 * Entity machines can use one renderer for the in-world entity and an optional
 * separate renderer for their item form. When the item renderer is disabled,
 * the entity renderer is reused before falling back to the caller-provided
 * renderer.
 */
@Getter
@Accessors(fluent = true)
public class EntityMachineModelSettings implements IConfigurable, IPersistedSerializable {

    @Configurable(name = "config.entity_machine.model.renderer", subConfigurable = true, tips = {
            "config.entity_machine.model.renderer.tooltip.0",
            "config.entity_machine.model.renderer.tooltip.1"
    }, collapse = false)
    private final ToggleRenderer renderer = new ToggleRenderer(true);

    @Configurable(name = "config.entity_machine.model.item_renderer", subConfigurable = true, tips = {
            "config.entity_machine.model.item_renderer.tooltip.0",
            "config.entity_machine.model.item_renderer.tooltip.1"
    })
    private final ToggleRenderer itemRenderer = new ToggleRenderer();

    /**
     * Creates model settings with a default entity renderer.
     *
     * @param defaultRenderer renderer copied from the definition state, or
     *                        {@link IRenderer#EMPTY} when {@code null}
     */
    public EntityMachineModelSettings(IRenderer defaultRenderer) {
        renderer.setValue(defaultRenderer == null ? IRenderer.EMPTY : defaultRenderer);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        IConfigurable.super.buildConfigurator(father);
        father.addConfigurators(new WrapperConfigurator("config.entity_machine.model.preview",
                new ImageWidget(0, 0, 18, 18, new IRendererSlotTexture(this::getRenderer))));
    }

    /**
     * Returns the renderer used for the entity in world.
     *
     * @return configured renderer, or {@link IRenderer#EMPTY} when disabled
     */
    public IRenderer getRenderer() {
        return renderer.isEnable() && renderer.getValue() != null ? renderer.getValue() : IRenderer.EMPTY;
    }

    /**
     * Returns the renderer used for the entity machine item.
     *
     * @param fallback renderer to use when both item and entity renderers are
     *                 disabled
     * @return item renderer, entity renderer, or fallback in that order
     */
    public IRenderer getItemRenderer(IRenderer fallback) {
        if (itemRenderer.isEnable() && itemRenderer.getValue() != null) {
            return itemRenderer.getValue();
        }
        return getRenderer() == IRenderer.EMPTY ? fallback : getRenderer();
    }
}
