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

@OnlyIn(Dist.CLIENT)
public class FusionModelRenderer extends IModelRenderer {

    public FusionModelRenderer(ResourceLocation modelLocation) {
        super(modelLocation);
    }

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
