package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Map;

@Mixin(value = ResourceContainer.class, remap = false)
public abstract class ResourceContainerMixin<C extends Widget> {

    @Shadow
    @Final
    protected Map<Either<String, File>, C> widgets;

    @Inject(method = "reBuild", at = @At("HEAD"))
    private void mbd2$clearOldWidgetIndex(CallbackInfo ci) {
        widgets.values().forEach(this::mbd2$releaseSceneWidget);
        widgets.clear();
    }

    @Unique
    private void mbd2$releaseSceneWidget(Widget widget) {
        if (widget instanceof SceneWidget sceneWidget && sceneWidget.getRenderer() != null) {
            sceneWidget.getRenderer().deleteCacheBuffer();
        }
    }
}
