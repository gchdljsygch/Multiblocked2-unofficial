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

/**
 * Delegates proxy-part block rendering to the multiblock controller machine.
 *
 * <p>Proxy blocks do not own their visual identity. This renderer locates the controller recorded by
 * {@link ProxyPartBlockEntity} and asks the controller's active machine renderer for baked quads, dynamic block-entity
 * rendering, and particle texture data. All methods are client-side render paths and must tolerate missing worlds,
 * unloaded controllers, and stale proxy metadata by returning empty/default rendering.</p>
 */
public class ProxyPartRenderer implements IRenderer {
    /**
     * Shared stateless renderer instance used by proxy-part block definitions.
     */
    public static final ProxyPartRenderer INSTANCE = new ProxyPartRenderer();

    private ProxyPartRenderer() {
    }

    /**
     * Renders the proxy block as the controller's current machine model.
     *
     * @param level world/tint context; may be {@code null} for item or fallback model calls
     * @param pos   proxy block position; may be {@code null}
     * @param state proxy block state supplied by the block renderer
     * @param side  side to render, or {@code null} for unculled quads
     * @param rand  random source forwarded to the controller renderer
     * @return controller-rendered quads, or an empty list when no controller machine is available
     */
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

    /**
     * Emits dynamic renderer override quads for proxy parts whose controller uses a dynamic renderer.
     *
     * <p>The method has no persistent side effects, but it writes vertices to {@code buffer} using the caller's current
     * pose. Missing controller information is treated as a no-op.</p>
     *
     * @param blockEntity     proxy part block entity being rendered
     * @param partialTicks    render interpolation fraction supplied by Minecraft
     * @param stack           active pose stack
     * @param buffer          target buffer source for emitted quads
     * @param combinedLight   packed light value
     * @param combinedOverlay packed overlay value
     */
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

    /**
     * Resolves the controller renderer's particle texture from the active model-data context.
     *
     * @return controller particle sprite when available; otherwise {@link IRenderer}'s default particle texture
     */
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

    /**
     * Looks up the controller machine for a proxy block.
     *
     * @param level world containing the proxy
     * @param pos   proxy block position
     * @return controller machine when the proxy metadata is complete and loaded
     */
    private java.util.Optional<MBDMachine> getMachine(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof ProxyPartBlockEntity blockEntity) || blockEntity.getControllerPos() == null) {
            return java.util.Optional.empty();
        }
        return IMachine.ofMachine(level, blockEntity.getControllerPos())
                .filter(MBDMachine.class::isInstance)
                .map(MBDMachine.class::cast);
    }
}
