package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.AllArgsConstructor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Item-renderer wrapper that delegates model drawing while supplying dynamic lighting flags.
 *
 * <p>The business goal is to let machine definitions decide whether an item should use block lighting and 3D GUI
 * rendering without creating a bespoke renderer class for every definition. The wrapped renderer supplier is evaluated
 * at render time, so callers can expose stateful or reload-aware renderers. Client-only; no game-state mutation.</p>
 */
@OnlyIn(Dist.CLIENT)
@AllArgsConstructor
public class MBDItemRenderer implements IRenderer {
    protected final BooleanSupplier useBlockLight;
    protected final BooleanSupplier isGui3d;
    protected final Supplier<IRenderer> renderer;

    /**
     * Reports whether the item renderer should use block lighting.
     *
     * @param stack stack being rendered
     * @return current block-light flag from the supplier
     */
    @Override
    public boolean useBlockLight(ItemStack stack) {
        return useBlockLight.getAsBoolean();
    }

    /**
     * Reports whether GUI rendering should use a 3D model.
     *
     * @return current GUI-3D flag from the supplier
     */
    @Override
    public boolean isGui3d() {
        return isGui3d.getAsBoolean();
    }

    /**
     * Delegates item rendering to the current wrapped renderer.
     *
     * @param stack           stack being rendered
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
        renderer.get().renderItem(stack, transformType, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
    }
}
