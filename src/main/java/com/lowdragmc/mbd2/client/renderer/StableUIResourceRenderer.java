package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.UIResourceRenderer;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.mojang.datafixers.util.Either;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@OnlyIn(Dist.CLIENT)
public class StableUIResourceRenderer extends UIResourceRenderer {
    private final Resource<IRenderer> liveResource;
    private IRenderer lastResolvedRenderer = IRenderer.EMPTY;

    public StableUIResourceRenderer(Resource<IRenderer> resource, Either<String, File> key) {
        super(resource, key);
        this.liveResource = resource;
    }

    @Override
    public IRenderer getRenderer() {
        var renderer = resolveRenderer();
        if (renderer != null) {
            lastResolvedRenderer = renderer;
            return renderer;
        }
        return lastResolvedRenderer;
    }

    @Nullable
    private IRenderer resolveRenderer() {
        if (liveResource == null || key == null || !liveResource.hasResource(key)) {
            return null;
        }
        var renderer = liveResource.getResourceOrDefault(key, IRenderer.EMPTY);
        return renderer == this ? IRenderer.EMPTY : renderer;
    }
}
