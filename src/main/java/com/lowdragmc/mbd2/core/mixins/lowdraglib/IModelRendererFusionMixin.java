package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import com.lowdragmc.mbd2.client.renderer.FusionModelDataHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(IModelRenderer.class)
public abstract class IModelRendererFusionMixin {

    @Shadow(remap = false)
    @Nullable
    protected abstract BakedModel getBlockBakedModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state);

    @Shadow(remap = false)
    public abstract ResourceLocation getModelLocation();

    @Shadow(remap = false)
    @Nullable
    protected abstract BakedModel getItemBakedModel(ItemStack itemStack);

    /**
     * @author use finally to restore LDLib item-render recursion guard.
     * @reason LDLib leaves IItemRendererProvider.disabled enabled if item model baking/rendering throws.
     */
    @Overwrite(remap = false)
    public void renderItem(ItemStack stack,
                           ItemDisplayContext transformType,
                           boolean leftHand,
                           PoseStack poseStack,
                           MultiBufferSource buffer,
                           int combinedLight,
                           int combinedOverlay,
                           BakedModel model) {
        var previousDisabled = IItemRendererProvider.disabled.get();
        IItemRendererProvider.disabled.set(true);
        try {
            model = getItemBakedModel(stack);
            if (model != null) {
                Minecraft.getInstance().getItemRenderer().render(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
            }
        } finally {
            IItemRendererProvider.disabled.set(previousDisabled);
        }
    }

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$renderWithForgeModelData(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, @Nullable Direction side, RandomSource rand, CallbackInfoReturnable<List<BakedQuad>> cir) {
        var bakedModel = getBlockBakedModel(level, pos, state);
        if (bakedModel == null) {
            cir.setReturnValue(Collections.emptyList());
            return;
        }
        FusionModelDataHelper.debugOnce("imodel-renderer-" + getModelLocation() + "-" + bakedModel.getClass().getName(),
                "IModelRenderer rendering model={}, bakedModel={}, side={}, pos={}",
                getModelLocation(), bakedModel.getClass().getName(), side, pos);

        var modelData = FusionModelDataHelper.getCurrentModelData();
        if (level != null && pos != null && state != null) {
            try (var ignored = FusionModelDataHelper.pushModelContext(pos, getModelLocation(), bakedModel)) {
                modelData = bakedModel.getModelData(FusionModelDataHelper.wrapLevel(level, pos, state, getModelLocation()), pos, state, modelData);
            }
        }
        cir.setReturnValue(bakedModel.getQuads(state, side, rand, modelData, LDLRendererModel.RendererBakedModel.CURRENT_RENDER_TYPE.get()));
    }
}
