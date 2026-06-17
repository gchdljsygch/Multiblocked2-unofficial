package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the client {@link LevelRenderer}'s render buffer set.
 *
 * <p>MBD2 render helpers use this to submit custom preview and overlay geometry through the
 * same {@link RenderBuffers} instance as vanilla world rendering.</p>
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    /**
     * Returns the render buffers owned by the active level renderer.
     */
    @Accessor
    RenderBuffers getRenderBuffers();
}
