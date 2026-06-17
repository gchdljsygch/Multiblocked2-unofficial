package com.lowdragmc.mbd2.utils;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.UIResourceRenderer;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;

/**
 * Scoped context that restores the previous UI renderer resource when closed.
 */
public final class UIResourceRendererContext implements AutoCloseable {
    private final Resource<IRenderer> previousResource;
    private final boolean previousIsProject;
    private boolean closed;

    private UIResourceRendererContext(Resource<IRenderer> resource, boolean isProject) {
        this.previousResource = UIResourceRenderer.getProjectResource();
        this.previousIsProject = UIResourceRenderer.isProject();
        UIResourceRenderer.setCurrentResource(resource, isProject);
    }

    public static UIResourceRendererContext push(Resource<IRenderer> resource, boolean isProject) {
        return new UIResourceRendererContext(resource, isProject);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previousResource == null) {
            UIResourceRenderer.clearCurrentResource();
        } else {
            UIResourceRenderer.setCurrentResource(previousResource, previousIsProject);
        }
    }
}
