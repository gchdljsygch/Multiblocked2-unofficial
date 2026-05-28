package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
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
                Rotation rotation = horizontalRotation(baseFacing, currentFacing);
                if (rotation == Rotation.NONE) return basePredicate.test(state);
                BlockState actual = state.getBlockState();
                if (actual == null || states == null) return false;
                for (BlockState expected : states) {
                    if (expected == null) continue;
                    BlockState rotated = expected.rotate(rotation);
                    if (rotated.equals(actual)) return true;
                }
                return false;
            };
        }
        candidates = Suppliers.memoize(() -> Arrays.stream(states).map(BlockInfo::fromBlockState).toArray(BlockInfo[]::new));
        return super.buildPredicate();
    }

    private static Rotation horizontalRotation(Direction from, Direction to) {
        if (from == null || to == null) return Rotation.NONE;
        if (from.getAxis() == Direction.Axis.Y || to.getAxis() == Direction.Axis.Y) return Rotation.NONE;
        int fromIndex = horizontalIndex(from);
        int toIndex = horizontalIndex(to);
        int steps = (toIndex - fromIndex + 4) & 3;
        return switch (steps) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int horizontalIndex(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }
}
