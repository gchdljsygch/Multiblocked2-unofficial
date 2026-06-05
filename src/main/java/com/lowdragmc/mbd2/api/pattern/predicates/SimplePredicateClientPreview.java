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

@OnlyIn(Dist.CLIENT)
final class SimplePredicateClientPreview {
    private SimplePredicateClientPreview() {
    }

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
