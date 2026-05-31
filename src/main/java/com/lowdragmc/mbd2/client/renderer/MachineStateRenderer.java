package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record MachineStateRenderer(IRenderer blockRenderer, IRenderer frontRenderer,
                                   Direction frontFacing) implements IRenderer {

    private static final float FRONT_MODEL_OFFSET = 1 / 1024f;
    private static final int VERTEX_STRIDE = 8;

    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        blockRenderer.renderItem(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
        frontRenderer.renderItem(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
    }

    @Override
    public List<BakedQuad> renderModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        if (side == frontFacing) {
            var blockQuads = renderBlockFacingQuads(level, pos, state, rand);
            var frontQuads = frontRenderer.renderModel(level, pos, state, side, rand);
            if (frontQuads.isEmpty()) {
                frontQuads = onlyFacingQuads(frontRenderer.renderModel(level, pos, state, null, rand), frontFacing);
            }
            if (blockQuads.isEmpty()) {
                return offsetQuads(frontQuads, frontFacing, FRONT_MODEL_OFFSET);
            }
            if (frontQuads.isEmpty()) {
                return blockQuads;
            }
            var quads = new java.util.ArrayList<BakedQuad>(blockQuads.size() + frontQuads.size());
            quads.addAll(blockQuads);
            quads.addAll(offsetQuads(frontQuads, frontFacing, FRONT_MODEL_OFFSET));
            return quads;
        }
        if (side == null) {
            return withoutFrontFacingQuads(renderBlockModelWithFrontSuppressed(level, pos, state, rand));
        }
        return blockRenderer.renderModel(level, pos, state, side, rand);
    }

    private List<BakedQuad> offsetQuads(List<BakedQuad> quads, Direction direction, float offset) {
        if (quads.isEmpty() || offset == 0) {
            return quads;
        }
        var shifted = new java.util.ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            shifted.add(offsetQuad(quad, direction, offset));
        }
        return shifted;
    }

    private BakedQuad offsetQuad(BakedQuad quad, Direction direction, float offset) {
        var vertices = quad.getVertices().clone();
        var xOffset = direction.getStepX() * offset;
        var yOffset = direction.getStepY() * offset;
        var zOffset = direction.getStepZ() * offset;
        for (var vertex = 0; vertex < 4; vertex++) {
            var base = vertex * VERTEX_STRIDE;
            vertices[base] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base]) + xOffset);
            vertices[base + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + 1]) + yOffset);
            vertices[base + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + 2]) + zOffset);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private List<BakedQuad> renderBlockFacingQuads(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, RandomSource rand) {
        var blockQuads = blockRenderer.renderModel(level, pos, state, frontFacing, rand);
        if (!blockQuads.isEmpty()) {
            return blockQuads;
        }
        return onlyFacingQuads(blockRenderer.renderModel(level, pos, state, null, rand), frontFacing);
    }

    private List<BakedQuad> onlyFacingQuads(List<BakedQuad> quads, Direction facing) {
        if (quads.isEmpty()) {
            return quads;
        }
        var filtered = new java.util.ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            if (quad.getDirection() == facing) {
                filtered.add(quad);
            }
        }
        return filtered.size() == quads.size() ? quads : filtered;
    }

    private List<BakedQuad> renderBlockModelWithFrontSuppressed(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, RandomSource rand) {
        try (var ignored = FusionModelDataHelper.suppressFace(frontFacing)) {
            return blockRenderer.renderModel(level, pos, state, null, rand);
        }
    }

    private List<BakedQuad> withoutFrontFacingQuads(List<BakedQuad> quads) {
        if (quads.isEmpty()) {
            return quads;
        }
        var filtered = new java.util.ArrayList<BakedQuad>(quads.size());
        for (var quad : quads) {
            if (quad.getDirection() != frontFacing) {
                filtered.add(quad);
            }
        }
        return filtered.size() == quads.size() ? quads : filtered;
    }

    @Override
    public boolean hasTESR(BlockEntity blockEntity) {
        return blockRenderer.hasTESR(blockEntity);
    }

    @Override
    public boolean isGlobalRenderer(BlockEntity blockEntity) {
        return blockRenderer.isGlobalRenderer(blockEntity);
    }

    @Override
    public int getViewDistance() {
        return blockRenderer.getViewDistance();
    }

    @Override
    public boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
        return blockRenderer.shouldRender(blockEntity, cameraPos);
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack stack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        blockRenderer.render(blockEntity, partialTicks, stack, buffer, combinedLight, combinedOverlay);
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return blockRenderer.getParticleTexture();
    }

    @Override
    public boolean useAO() {
        return blockRenderer.useAO();
    }

    @Override
    public boolean useAO(BlockState state) {
        return blockRenderer.useAO(state);
    }

    @Override
    public boolean useBlockLight(ItemStack stack) {
        return blockRenderer.useBlockLight(stack);
    }

    @Override
    public boolean reBakeCustomQuads() {
        return blockRenderer.reBakeCustomQuads();
    }

    @Override
    public float reBakeCustomQuadsOffset() {
        return blockRenderer.reBakeCustomQuadsOffset();
    }

    @Override
    public boolean isGui3d() {
        return blockRenderer.isGui3d();
    }
}
