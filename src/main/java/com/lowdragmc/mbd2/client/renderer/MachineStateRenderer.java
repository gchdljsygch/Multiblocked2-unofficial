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

/**
 * Composite renderer that overlays a front-face renderer onto a machine block renderer.
 *
 * <p>The business goal is to support machine states with a distinct front indicator or face model while preserving the
 * base block renderer everywhere else. Front-facing quads from the overlay renderer are nudged outward by a tiny offset
 * to avoid z-fighting with the base model. All operations are client-side rendering only and do not mutate world or
 * machine state.</p>
 *
 * @param blockRenderer base renderer used for the machine body and delegated render metadata
 * @param frontRenderer renderer used for the configured front face
 * @param frontFacing   direction that receives the overlay renderer
 */
public record MachineStateRenderer(IRenderer blockRenderer, IRenderer frontRenderer,
                                   Direction frontFacing) implements IRenderer {

    private static final float FRONT_MODEL_OFFSET = 1 / 1024f;
    private static final int VERTEX_STRIDE = 8;

    /**
     * Renders both base and front item renderers for item-stack previews.
     *
     * @param stack           item stack being rendered
     * @param transformType   vanilla display transform
     * @param leftHand        whether the render is for the left hand
     * @param poseStack       pose stack for transforms
     * @param buffer          render buffer source
     * @param combinedLight   packed light value
     * @param combinedOverlay packed overlay value
     * @param model           baked model being rendered
     */
    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        blockRenderer.renderItem(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
        frontRenderer.renderItem(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
    }

    /**
     * Renders base quads plus front overlay quads for the configured front side.
     *
     * <p>For side-specific front-face requests, the base face is combined with the overlay face and overlay vertices are
     * offset outward. For general {@code side == null} model requests, the base renderer is asked to suppress the front
     * face through {@link FusionModelDataHelper} and any remaining front-facing quads are filtered out.</p>
     *
     * @param level optional world used by model renderers
     * @param pos   optional block position
     * @param state optional block state
     * @param side  side being baked, or {@code null} for general quads
     * @param rand  random source for model baking
     * @return baked quads for the requested side/general pass
     */
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

    /**
     * Moves quads slightly outward along a direction.
     *
     * @param quads     quads to offset
     * @param direction outward direction
     * @param offset    distance in model units; {@code 0} returns the original list
     * @return shifted quad list, or the original list when no work is required
     */
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

    /**
     * Returns a copy of one quad with its four position vertices shifted.
     *
     * @param quad      source quad
     * @param direction outward direction
     * @param offset    distance in model units
     * @return shifted baked quad preserving tint, face, sprite, shade, and ambient occlusion
     */
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

    /**
     * Renders the base renderer's front-facing quads.
     *
     * @param level optional world
     * @param pos   optional block position
     * @param state optional block state
     * @param rand  random source
     * @return side-specific front quads, falling back to filtering general quads by front direction
     */
    private List<BakedQuad> renderBlockFacingQuads(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, RandomSource rand) {
        var blockQuads = blockRenderer.renderModel(level, pos, state, frontFacing, rand);
        if (!blockQuads.isEmpty()) {
            return blockQuads;
        }
        return onlyFacingQuads(blockRenderer.renderModel(level, pos, state, null, rand), frontFacing);
    }

    /**
     * Filters a quad list down to one face direction.
     *
     * @param quads  source quads
     * @param facing direction to keep
     * @return filtered list, or original list when every quad already matches
     */
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

    /**
     * Renders the base model while asking Fusion model data to suppress the front face.
     *
     * @param level optional world
     * @param pos   optional block position
     * @param state optional block state
     * @param rand  random source
     * @return base renderer general quads with front-face suppression active
     */
    private List<BakedQuad> renderBlockModelWithFrontSuppressed(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, RandomSource rand) {
        try (var ignored = FusionModelDataHelper.suppressFace(frontFacing)) {
            return blockRenderer.renderModel(level, pos, state, null, rand);
        }
    }

    /**
     * Removes front-facing quads from a general quad list.
     *
     * @param quads source quads
     * @return filtered list, or original list when no front-facing quads are present
     */
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

    /**
     * Delegates block-entity renderer availability to the base block renderer.
     *
     * @param blockEntity queried block entity
     * @return base renderer result
     */
    @Override
    public boolean hasTESR(BlockEntity blockEntity) {
        return blockRenderer.hasTESR(blockEntity);
    }

    /**
     * Delegates global-renderer policy to the base block renderer.
     *
     * @param blockEntity queried block entity
     * @return base renderer result
     */
    @Override
    public boolean isGlobalRenderer(BlockEntity blockEntity) {
        return blockRenderer.isGlobalRenderer(blockEntity);
    }

    /**
     * Delegates view distance to the base block renderer.
     *
     * @return render view distance
     */
    @Override
    public int getViewDistance() {
        return blockRenderer.getViewDistance();
    }

    /**
     * Delegates culling policy to the base block renderer.
     *
     * @param blockEntity queried block entity
     * @param cameraPos   camera position
     * @return base renderer result
     */
    @Override
    public boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
        return blockRenderer.shouldRender(blockEntity, cameraPos);
    }

    /**
     * Delegates dynamic block-entity rendering to the base block renderer.
     *
     * @param blockEntity     block entity being rendered
     * @param partialTicks    partial tick interpolation value
     * @param stack           pose stack
     * @param buffer          render buffer source
     * @param combinedLight   packed light value
     * @param combinedOverlay packed overlay value
     */
    @Override
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack stack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        blockRenderer.render(blockEntity, partialTicks, stack, buffer, combinedLight, combinedOverlay);
    }

    /**
     * Delegates particle texture to the base block renderer.
     *
     * @return particle sprite
     */
    @NotNull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return blockRenderer.getParticleTexture();
    }

    /**
     * Delegates ambient-occlusion policy to the base block renderer.
     *
     * @return base renderer result
     */
    @Override
    public boolean useAO() {
        return blockRenderer.useAO();
    }

    /**
     * Delegates state-specific ambient-occlusion policy to the base block renderer.
     *
     * @param state queried state
     * @return base renderer result
     */
    @Override
    public boolean useAO(BlockState state) {
        return blockRenderer.useAO(state);
    }

    /**
     * Delegates item block-light policy to the base block renderer.
     *
     * @param stack stack being rendered
     * @return base renderer result
     */
    @Override
    public boolean useBlockLight(ItemStack stack) {
        return blockRenderer.useBlockLight(stack);
    }

    /**
     * Delegates custom-quad rebake policy to the base block renderer.
     *
     * @return base renderer result
     */
    @Override
    public boolean reBakeCustomQuads() {
        return blockRenderer.reBakeCustomQuads();
    }

    /**
     * Delegates custom-quad rebake offset to the base block renderer.
     *
     * @return base renderer result
     */
    @Override
    public float reBakeCustomQuadsOffset() {
        return blockRenderer.reBakeCustomQuadsOffset();
    }

    /**
     * Delegates GUI 3D policy to the base block renderer.
     *
     * @return base renderer result
     */
    @Override
    public boolean isGui3d() {
        return blockRenderer.isGui3d();
    }
}
