package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MBDClientRenderers {
    private MBDClientRenderers() {
    }

    public static IRenderer createFusionModelRenderer(ResourceLocation modelLocation) {
        return new FusionModelRenderer(modelLocation);
    }

    public static IRenderer createMachineStateRenderer(IRenderer blockRenderer, IRenderer frontRenderer, Direction frontFacing) {
        return new MachineStateRenderer(blockRenderer, frontRenderer, frontFacing);
    }
}
