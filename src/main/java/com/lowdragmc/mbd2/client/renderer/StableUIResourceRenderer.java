package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.UIResourceRenderer;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.mojang.datafixers.util.Either;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * UI resource renderer that keeps the last usable renderer while editor resources are being refreshed.
 *
 * <p>LowDragLib resource lookups can briefly return no renderer while a live resource is replaced. This wrapper avoids
 * visible flicker in editor widgets by caching the last resolved renderer and by refusing to return itself when the
 * backing resource points back to this instance. It is client/UI-thread state and is not intended for cross-thread use.</p>
 */
@OnlyIn(Dist.CLIENT)
public class StableUIResourceRenderer extends UIResourceRenderer {
    private final Resource<IRenderer> liveResource;
    private IRenderer lastResolvedRenderer = IRenderer.EMPTY;

    /**
     * Creates a stable view over a live renderer resource.
     *
     * @param resource live resource registry queried on each render pass
     * @param key      string or file key used by the resource registry
     */
    public StableUIResourceRenderer(Resource<IRenderer> resource, Either<String, File> key) {
        super(resource, key);
        this.liveResource = resource;
    }

    /**
     * Returns the currently resolved renderer or the last resolved fallback.
     *
     * @return non-null renderer; {@link IRenderer#EMPTY} is used until a real resource is available
     */
    @Override
    public IRenderer getRenderer() {
        var renderer = resolveRenderer();
        if (renderer != null) {
            lastResolvedRenderer = renderer;
            return renderer;
        }
        return lastResolvedRenderer;
    }

    /**
     * Reads the live resource without changing the cached fallback.
     *
     * @return resolved renderer, {@link IRenderer#EMPTY} for self-references, or {@code null} when the key is absent
     */
    @Nullable
    private IRenderer resolveRenderer() {
        if (liveResource == null || key == null || !liveResource.hasResource(key)) {
            return null;
        }
        var renderer = liveResource.getResourceOrDefault(key, IRenderer.EMPTY);
        return renderer == this ? IRenderer.EMPTY : renderer;
    }
}
