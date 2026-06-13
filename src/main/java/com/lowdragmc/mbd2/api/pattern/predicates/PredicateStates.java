package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.util.PatternStateRotation;
import lombok.NoArgsConstructor;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

@LDLRegister(name = "blockstates", group = "predicate")
@NoArgsConstructor
public class PredicateStates extends SimplePredicate {
    @Configurable(name = "config.predicate.blockstates", tips = "config.predicate.blockstates.tooltip", collapse = false)
    protected BlockState[] states = new BlockState[] {Blocks.RAIL.defaultBlockState()};

    public PredicateStates(BlockState... states) {
        this.states = states;
        buildPredicate();
    }

    @ConfigSetter(field = "states")
    public void setStates(BlockState[] states) {
        this.states = states;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        states = Arrays.stream(states).filter(Objects::nonNull).toArray(BlockState[]::new);
        if (states.length == 0) states = new BlockState[]{Blocks.BARRIER.defaultBlockState()};
        predicate = state -> ArrayUtils.contains(states, state.getBlockState());
        Predicate<MultiblockState> basePredicate = predicate;
        if (controllerFront == null || !controllerFront.isEnable()) {
            predicate = state -> {
                if (state == null) return false;
                Direction currentFacing = state.getPatternFacing();
                Direction baseFacing = state.getPatternBaseFacing();
                Rotation rotation = PatternStateRotation.horizontalRotation(baseFacing, currentFacing);
                if (rotation == Rotation.NONE) return basePredicate.test(state);
                BlockState actual = state.getBlockState();
                if (actual == null || states == null) return false;
                for (BlockState expected : states) {
                    if (expected == null) continue;
                    BlockState rotated = PatternStateRotation.rotate(expected, rotation);
                    if (rotated.equals(actual)) return true;
                }
                return false;
            };
        }
        candidates = Suppliers.memoize(() -> Arrays.stream(states).map(BlockInfo::fromBlockState).toArray(BlockInfo[]::new));
        return super.buildPredicate();
    }
}
