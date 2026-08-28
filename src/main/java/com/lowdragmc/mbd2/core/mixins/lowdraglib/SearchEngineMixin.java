package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.utils.SearchEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Transfers LowDragLib search-result UI updates from its worker thread to the
 * Minecraft client thread.
 *
 * <p>{@link SearchEngine} invokes result consumers on its search worker. Those
 * consumers create and resize widgets, which races with the render thread and
 * can leave a scroll group's child list in an invalid state. The redirect keeps
 * searching asynchronous while serializing the UI mutation onto the client
 * executor.</p>
 */
@Mixin(value = SearchEngine.class, remap = false)
public abstract class SearchEngineMixin {

    /**
     * Schedules one search result for UI processing on the client thread.
     *
     * @param consumer original LowDragLib result consumer
     * @param result search result produced on a background thread
     */
    @Redirect(
            method = "lambda$searchWord$0",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),
            remap = false,
            require = 0
    )
    private void mbd2$dispatchResultOnClientThread(Consumer<Object> consumer, Object result) {
        Minecraft.getInstance().execute(() -> consumer.accept(result));
    }
}
