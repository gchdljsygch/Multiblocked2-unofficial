package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.IRendererResourceContainer;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.mbd2.client.renderer.StableUIResourceRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps LDLib renderer-resource previews bound to the live resource entry.
 *
 * <p>Without this hook, preview widgets can render a stale renderer copy after the user edits a
 * renderer resource. The replacement supplier wraps the resource key in
 * {@link StableUIResourceRenderer}, so each preview resolves the current renderer from the
 * resource container.</p>
 */
@Mixin(value = IRendererResourceContainer.class, remap = false)
public abstract class IRendererResourceContainerMixin extends ResourceContainer<IRenderer, Widget> {

    private IRendererResourceContainerMixin(Resource<IRenderer> resource, ResourcePanel panel) {
        super(resource, panel);
    }

    @Shadow
    protected abstract SceneWidget createPreview(IRenderer renderer);

    /**
     * Replaces LDLib's preview supplier after the resource container is constructed.
     *
     * @param resource renderer resource backing this container
     * @param panel    owner resource panel
     * @param ci       mixin callback info
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mbd2$useLiveRendererResourceForPreview(Resource<IRenderer> resource, ResourcePanel panel, CallbackInfo ci) {
        setWidgetSupplier(key -> createPreview(new StableUIResourceRenderer(resource, key)));
    }
}
