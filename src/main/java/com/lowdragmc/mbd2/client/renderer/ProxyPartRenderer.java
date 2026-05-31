package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.mbd2.api.blockentity.ProxyPartBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel.RendererBakedModel.POS;
import static com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel.RendererBakedModel.WORLD;

public class ProxyPartRenderer implements IRenderer {
    public static final ProxyPartRenderer INSTANCE = new ProxyPartRenderer();

    private ProxyPartRenderer() {
    }

    @Override
    public List<BakedQuad> renderModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getMachine(level, pos)
                .map(machine -> {
                    var renderer = machine.getRealRenderer(machine.getFrontFacing().orElse(Direction.NORTH));
                    FusionModelDataHelper.debugOnce("proxy-part-renderer-" + machine.getDefinition() + "-" + renderer.getClass().getName(),
                            "ProxyPartRenderer rendering machine={}, renderer={}, side={}, pos={}",
                            machine.getDefinition(), renderer.getClass().getName(), side, pos);
                    return renderer.renderModel(level, pos, state, side, rand);
                })
                .orElseGet(Collections::emptyList);
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack stack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if (!(blockEntity instanceof ProxyPartBlockEntity proxyPart) || proxyPart.getControllerPos() == null || blockEntity.getLevel() == null) {
            return;
        }
        var level = blockEntity.getLevel();
        IMachine.ofMachine(level, proxyPart.getControllerPos())
                .filter(MBDMachine.class::isInstance)
                .map(MBDMachine.class::cast)
                .filter(MBDMachine::hasDynamicRendererOverride)
                .ifPresent(machine -> {
                    var renderer = machine.getRealRenderer(machine.getFrontFacing().orElse(Direction.NORTH));
                    var pos = blockEntity.getBlockPos();
                    var state = blockEntity.getBlockState();
                    FusionModelDataHelper.debugOnce("proxy-part-ber-" + machine.getDefinition() + "-" + renderer.getClass().getName(),
                            "ProxyPartRenderer BE rendering machine={}, renderer={}, proxyPos={}, controllerPos={}",
                            machine.getDefinition(), renderer.getClass().getName(), pos, proxyPart.getControllerPos());
                    var random = RandomSource.create(machine.getOffset());
                    var consumer = buffer.getBuffer(RenderType.cutout());
                    var pose = stack.last();
                    for (var side : Direction.values()) {
                        for (var quad : renderer.renderModel(level, pos, state, side, random)) {
                            consumer.putBulkData(pose, quad, 1, 1, 1, combinedLight, combinedOverlay);
                        }
                    }
                    for (var quad : renderer.renderModel(level, pos, state, null, random)) {
                        consumer.putBulkData(pose, quad, 1, 1, 1, combinedLight, combinedOverlay);
                    }
                });
    }

    @NotNull
    @Override
    @OnlyIn(Dist.CLIENT)
    public TextureAtlasSprite getParticleTexture() {
        var modelData = LDLRendererModel.RendererBakedModel.CURRENT_MODEL_DATA.get();
        if (modelData != null) {
            var world = modelData.get(WORLD);
            var pos = modelData.get(POS);
            if (world != null && pos != null && world.getBlockEntity(pos) instanceof ProxyPartBlockEntity blockEntity && blockEntity.getControllerPos() != null) {
                return IMachine.ofMachine(world, blockEntity.getControllerPos())
                        .filter(MBDMachine.class::isInstance)
                        .map(MBDMachine.class::cast)
                        .map(machine -> machine.getRealRenderer().getParticleTexture())
                        .orElseGet(IRenderer.super::getParticleTexture);
            }
        }
        return IRenderer.super.getParticleTexture();
    }

    private java.util.Optional<MBDMachine> getMachine(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof ProxyPartBlockEntity blockEntity) || blockEntity.getControllerPos() == null) {
            return java.util.Optional.empty();
        }
        return IMachine.ofMachine(level, blockEntity.getControllerPos())
                .filter(MBDMachine.class::isInstance)
                .map(MBDMachine.class::cast);
    }
}
