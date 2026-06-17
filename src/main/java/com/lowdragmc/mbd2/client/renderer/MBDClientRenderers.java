package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only renderer factory methods exposed to common machine configuration code.
 *
 * <p>The business goal is to keep renderer construction behind a client-only boundary so definitions can request MBD
 * renderer wrappers without directly depending on implementation constructors.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MBDClientRenderers {

    /**
     * Utility class; not instantiable.
     */
    private MBDClientRenderers() {
    }

    /**
     * Creates a Fusion-compatible model renderer for a block model resource.
     *
     * @param modelLocation model resource location
     * @return renderer that delegates to Fusion model data helpers
     */
    public static IRenderer createFusionModelRenderer(ResourceLocation modelLocation) {
        return new FusionModelRenderer(modelLocation);
    }

    /**
     * Creates a renderer that overlays a front-face renderer onto a base block renderer.
     *
     * @param blockRenderer base renderer for the whole machine
     * @param frontRenderer renderer drawn on the configured front face
     * @param frontFacing   direction treated as the front face
     * @return composite machine-state renderer
     */
    public static IRenderer createMachineStateRenderer(IRenderer blockRenderer, IRenderer frontRenderer, Direction frontFacing) {
        return new MachineStateRenderer(blockRenderer, frontRenderer, frontFacing);
    }
}
