package com.lowdragmc.mbd2.api.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Defines which directions a machine block may face and which block-state property stores that facing.
 *
 * <p>The business goal is to map machine rotation settings to vanilla block-state properties. Each enum value acts as
 * a predicate over candidate directions, exposes a default facing, and optionally provides the matching
 * {@link DirectionProperty} for block registration.</p>
 */
public enum RotationState implements Predicate<Direction> {
    /**
     * All six directions are valid.
     */
    ALL(dir -> true, Direction.NORTH, BlockStateProperties.FACING),
    /**
     * No direction is valid; the machine is orientation-independent.
     */
    NONE(dir -> false, Direction.NORTH, null),
    /**
     * Only vertical directions are valid.
     */
    Y_AXIS(dir -> dir.getAxis() == Direction.Axis.Y, Direction.UP, DirectionProperty.create("facing", Direction.Plane.VERTICAL)),
    /**
     * Only horizontal directions are valid.
     */
    NON_Y_AXIS(dir -> dir.getAxis() != Direction.Axis.Y, Direction.NORTH, BlockStateProperties.HORIZONTAL_FACING);

    final Predicate<Direction> predicate;
    public final Direction defaultDirection;
    public final Optional<DirectionProperty> property;

    RotationState(Predicate<Direction> predicate, Direction defaultDirection, DirectionProperty property) {
        this.predicate = predicate;
        this.defaultDirection = defaultDirection;
        this.property = Optional.ofNullable(property);
    }

    /**
     * Tests whether a direction is allowed by this rotation mode.
     *
     * @param dir candidate facing
     * @return {@code true} when the direction is valid for this mode
     */
    @Override
    public boolean test(Direction dir) {
        return predicate.test(dir);
    }
}
