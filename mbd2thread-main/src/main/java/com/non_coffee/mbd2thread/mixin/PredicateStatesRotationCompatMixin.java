package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateStates;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.common.machine.definition.config.toggle.ToggleDirection;
import com.non_coffee.mbd2thread.duck.BlockPatternBaseFacingAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.function.Predicate;

@Mixin(value = PredicateStates.class, remap = false)
public class PredicateStatesRotationCompatMixin {
    @Shadow
    protected BlockState[] states;

    @Inject(method = "buildPredicate", at = @At("RETURN"), remap = false)
    private void mbd2thread$rotateStateMatchByControllerFacing(CallbackInfoReturnable<com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate> cir) {
        SimplePredicate self = (SimplePredicate) (Object) this;
        ToggleDirection controllerFront = self.controllerFront;
        if (controllerFront != null && controllerFront.isEnable()) return;
        Predicate<MultiblockState> basePredicate = Objects.requireNonNull(self.predicate);
        self.predicate = state -> {
            if (state == null) return false;
            IMultiController controller = state.getController();
            if (controller == null) return basePredicate.test(state);
            Direction currentFacing = controller.getFrontFacing().orElse(Direction.NORTH);
            BlockPattern pattern = controller.getPattern();
            if (!(pattern instanceof BlockPatternBaseFacingAccess access)) return basePredicate.test(state);
            Direction baseFacing = access.mbd2thread$getBaseFacing();
            Rotation rotation = horizontalRotation(baseFacing, currentFacing);
            if (rotation == Rotation.NONE) return basePredicate.test(state);
            BlockState actual = state.getBlockState();
            if (actual == null) return false;
            if (states == null) return false;
            for (BlockState expected : states) {
                if (expected == null) continue;
                BlockState rotated = expected.rotate(rotation);
                if (rotated.equals(actual)) return true;
            }
            return false;
        };
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
