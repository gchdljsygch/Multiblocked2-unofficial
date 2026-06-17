package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * Client renderer for entity-backed MBD machines.
 *
 * <p>The renderer asks the entity's {@link MBDEntityMachine} for its current real renderer and draws both baked model
 * quads and block-entity-style dynamic rendering through the entity's virtual holder. Rendering is client-only and has
 * no game-state side effects; it reads machine state, entity level, and virtual block state for visual output.</p>
 */
@OnlyIn(Dist.CLIENT)
public class EntityMachineRenderer extends EntityRenderer<Entity> {

    /**
     * Creates the renderer with the default entity-machine shadow radius.
     *
     * @param context vanilla entity renderer context
     */
    public EntityMachineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    /**
     * Renders the machine model and then lets vanilla render any superclass entity effects.
     *
     * @param entity      entity being rendered
     * @param entityYaw   interpolated yaw
     * @param partialTick partial tick interpolation value
     * @param poseStack   pose stack for transforms
     * @param buffer      render buffer source
     * @param packedLight packed light value
     */
    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderMachine(entity, partialTick, poseStack, buffer, packedLight);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * Renders an entity's attached MBD machine when present.
     *
     * <p>The method translates the virtual block model by half a block so baked block quads are centered around the
     * entity, uses a stable random seed from the machine offset, emits cutout-layer model quads for all sides plus
     * general quads, and then invokes the renderer's dynamic render hook. Non-machine entities are ignored.</p>
     *
     * @param entity      entity that may implement {@link IMachineEntity}
     * @param partialTick partial tick interpolation value
     * @param poseStack   pose stack for transforms
     * @param buffer      render buffer source
     * @param packedLight packed light value
     */
    public static void renderMachine(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity instanceof IMachineEntity machineEntity && machineEntity.getMetaMachine() instanceof MBDEntityMachine machine) {
            var holder = machine.entityHolder();
            var renderer = machine.getRealRenderer();
            var pos = holder.getBlockPos();
            var state = holder.getBlockState();
            poseStack.pushPose();
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            var consumer = buffer.getBuffer(RenderType.cutout());
            var pose = poseStack.last();
            var random = RandomSource.create(machine.getOffset());
            try {
                for (var side : Direction.values()) {
                    for (var quad : renderer.renderModel(entity.level(), pos, state, side, random)) {
                        consumer.putBulkData(pose, quad, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
                    }
                }
                for (var quad : renderer.renderModel(entity.level(), pos, state, null, random)) {
                    consumer.putBulkData(pose, quad, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
                }
                renderer.render(holder, partialTick, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            } finally {
                poseStack.popPose();
            }
        }
    }

    /**
     * Uses the block atlas because entity machines render block-style quads.
     *
     * @param entity rendered entity
     * @return block texture atlas location
     */
    @Override
    public @NotNull ResourceLocation getTextureLocation(Entity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
