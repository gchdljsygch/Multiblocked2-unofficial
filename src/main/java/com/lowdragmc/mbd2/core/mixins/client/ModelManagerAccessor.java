package com.lowdragmc.mbd2.core.mixins.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes Minecraft's baked-model registry for MBD2 client model refresh logic.
 *
 * <p>The returned map is the live ModelManager registry. Callers must treat it as client-thread
 * state and avoid mutating it outside model bake or reload handling.</p>
 */
@Mixin(ModelManager.class)
public interface ModelManagerAccessor {

    /**
     * Returns the live baked-model registry keyed by model resource location.
     */
    @Accessor("bakedRegistry")
    Map<ResourceLocation, BakedModel> mbd2$getBakedRegistry();
}
