package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(value = SceneWidget.class, remap = false)
public abstract class SceneWidgetRenderedCoreMixin {
    @Shadow
    protected WorldSceneRenderer renderer;

    @Inject(
            method = "setRenderedCore(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneBlockRenderHook;)Lcom/lowdragmc/lowdraglib/gui/widget/SceneWidget;",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;addRenderedBlocks(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneBlockRenderHook;)Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;")
    )
    private void mbd2$clearOldRenderedCore(Collection<BlockPos> core,
                                           ISceneBlockRenderHook renderHook,
                                           CallbackInfoReturnable<SceneWidget> cir) {
        if (renderer != null) {
            renderer.renderedBlocksMap.clear();
        }
    }
}
