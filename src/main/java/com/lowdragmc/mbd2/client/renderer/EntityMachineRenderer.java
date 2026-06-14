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

@OnlyIn(Dist.CLIENT)
public class EntityMachineRenderer extends EntityRenderer<Entity> {
    public EntityMachineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity instanceof IMachineEntity machineEntity && machineEntity.getMetaMachine() instanceof MBDEntityMachine machine) {
            var holder = machine.entityHolder();
            var renderer = machine.getRealRenderer(machine.getFrontFacing().orElse(Direction.NORTH));
            poseStack.pushPose();
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            var consumer = buffer.getBuffer(RenderType.cutout());
            var pose = poseStack.last();
            var random = RandomSource.create(machine.getOffset());
            for (var side : Direction.values()) {
                for (var quad : renderer.renderModel(entity.level(), entity.blockPosition(), holder.getBlockState(), side, random)) {
                    consumer.putBulkData(pose, quad, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
                }
            }
            for (var quad : renderer.renderModel(entity.level(), entity.blockPosition(), holder.getBlockState(), null, random)) {
                consumer.putBulkData(pose, quad, 1, 1, 1, packedLight, OverlayTexture.NO_OVERLAY);
            }
            renderer.render(holder, partialTick, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(Entity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
