package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Renders a model-backed machine block while preserving Forge model-data context for Fusion-style connected
 * models.
 *
 * <p>The renderer is client-only and stateless apart from the inherited model location. Rendering must happen on
 * Minecraft's render/model thread because it queries the client {@link net.minecraft.client.resources.model.ModelManager}
 * and uses render-thread model-data thread locals. Its business goal is to let machine block models decide connection
 * state from the actual model resource rather than only from block state identity.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FusionModelRenderer extends IModelRenderer {

    /**
     * Creates a renderer for the baked block model at {@code modelLocation}.
     *
     * @param modelLocation model resource to render; expected to resolve to a baked block model on the client
     */
    public FusionModelRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

    /**
     * Resolves the baked model from the live client model manager before falling back to LDL's default lookup.
     *
     * @param level optional world context supplied by the caller
     * @param pos   optional block position being rendered
     * @param state optional block state being rendered
     * @return a baked model when the resource is available; otherwise the superclass fallback result
     */
    @Override
    protected BakedModel getBlockBakedModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state) {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getModelManager() != null) {
            var modelManager = minecraft.getModelManager();
            var model = modelManager.getModel(getModelLocation());
            if (model != modelManager.getMissingModel()) {
                FusionModelDataHelper.debugOnce("fusion-model-manager-" + getModelLocation() + "-" + model.getClass().getName(),
                        "FusionModelRenderer using ModelManager model={}, bakedModel={}",
                        getModelLocation(), model.getClass().getName());
                return model;
            }
        }
        return super.getBlockBakedModel(level, pos, state);
    }

    /**
     * Builds the quads for the requested side using the current LDL model-data context.
     *
     * <p>When a full world, position, and state are provided, the method temporarily pushes this renderer's model
     * context and wraps the level so connected model loaders can compare neighboring blocks by effective model path.
     * The temporary context is always restored before returning.</p>
     *
     * @param level world/tint context, or {@code null} for inventory-style rendering
     * @param pos   rendered block position, or {@code null} when no world position exists
     * @param state rendered block state, or {@code null} for non-world calls
     * @param side  side to render, or {@code null} for unculled quads
     * @param rand  caller-owned random source used by the baked model
     * @return immutable or mutable quad list supplied by the baked model; empty when the model cannot be resolved
     */
    @Override
    public List<BakedQuad> renderModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        var bakedModel = getBlockBakedModel(level, pos, state);
        if (bakedModel == null) {
            return Collections.emptyList();
        }
        FusionModelDataHelper.debugOnce("fusion-renderer-" + getModelLocation() + "-" + bakedModel.getClass().getName(),
                "FusionModelRenderer rendering model={}, bakedModel={}, side={}, pos={}",
                getModelLocation(), bakedModel.getClass().getName(), side, pos);

        var modelData = FusionModelDataHelper.getCurrentModelData();
        if (level != null && pos != null && state != null) {
            try (var ignored = FusionModelDataHelper.pushModelContext(pos, getModelLocation(), bakedModel)) {
                modelData = bakedModel.getModelData(FusionModelDataHelper.wrapLevel(level, pos, state, getModelLocation()), pos, state, modelData);
            }
        }
        return bakedModel.getQuads(state, side, rand, modelData, LDLRendererModel.RendererBakedModel.CURRENT_RENDER_TYPE.get());
    }
}
