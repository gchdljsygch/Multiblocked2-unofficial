package com.lowdragmc.mbd2.api.pattern.predicates;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-only scene widget factory for predicate previews.
 *
 * <p>The business goal is to render one candidate block at a time inside the
 * predicate editor and cycle through multiple candidates once per second. This
 * class must only be loaded on the client.</p>
 */
@OnlyIn(Dist.CLIENT)
final class SimplePredicateClientPreview {
    private SimplePredicateClientPreview() {
    }

    /**
     * Creates a non-interactive scene preview for a predicate.
     *
     * <p>Side effects: creates a dummy world, places the first candidate at the
     * origin, and returns a widget that periodically swaps the displayed block
     * when the predicate has multiple candidates.</p>
     *
     * @param predicate predicate whose candidates are displayed
     * @return scene widget for the editor preview
     */
    static Widget create(SimplePredicate predicate) {
        var level = new TrackedDummyWorld();
        var blockInfo = Optional.ofNullable(predicate.candidates).map(Supplier::get).filter(x -> x.length > 0).map(x -> x[0]).orElse(BlockInfo.EMPTY);
        level.addBlock(BlockPos.ZERO, blockInfo);
        var sceneWidget = new SceneWidget(0, 0, 100, 100, null) {
            @Override
            @OnlyIn(Dist.CLIENT)
            public void updateScreen() {
                super.updateScreen();
                if (gui.getTickCount() % 20 == 0) {
                    var blockInfo = Optional.ofNullable(predicate.candidates).map(Supplier::get)
                            .filter(x -> x.length > 0)
                            .map(x -> x[(int) ((gui.getTickCount() / 20L) % x.length)])
                            .orElse(BlockInfo.EMPTY);
                    level.addBlock(BlockPos.ZERO, blockInfo);
                }
            }
        };
        sceneWidget.setRenderFacing(false);
        sceneWidget.setRenderSelect(false);
        sceneWidget.setScalable(false);
        sceneWidget.setDraggable(false);
        sceneWidget.setIntractable(false);
        sceneWidget.createScene(level);
        sceneWidget.getRenderer().setOnLookingAt(null);
        sceneWidget.setRenderedCore(Collections.singleton(BlockPos.ZERO), null);
        return sceneWidget;
    }
}
