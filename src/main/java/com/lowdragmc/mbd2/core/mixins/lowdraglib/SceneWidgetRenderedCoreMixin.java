package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(value = SceneWidget.class, remap = false)
public abstract class SceneWidgetRenderedCoreMixin {
    @Shadow
    protected WorldSceneRenderer renderer;

    @Unique
    private DummyWorld mbd2$strongSceneWorldReference;

    @Inject(method = "createScene(Lnet/minecraft/world/level/Level;Z)V", at = @At("HEAD"))
    private void mbd2$keepDummyWorldAlive(Level world, boolean useFBOSceneRenderer, CallbackInfo ci) {
        // SceneWidget stores the supplied world through a WeakReference; preview worlds are local objects.
        mbd2$strongSceneWorldReference = world instanceof DummyWorld dummyWorld ? dummyWorld : null;
    }

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
