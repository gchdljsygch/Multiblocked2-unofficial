package com.lowdragmc.mbd2.api.pattern.util;

import net.minecraft.core.Direction;

import java.util.function.Function;

/**
 * Pattern-axis direction relative to a horizontally facing controller.
 *
 * <p>The business goal is to let pattern definitions describe rows, columns,
 * and aisles in author-facing terms, then resolve them to absolute Minecraft
 * directions during pattern matching, preview generation, and auto-build. The
 * helper methods accept only horizontal controller faces; vertical controller
 * faces are invalid because left/right/front/back are ambiguous.</p>
 */
public enum RelativeDirection {
    UP(f -> Direction.UP, Direction.Axis.Y),
    DOWN(f -> Direction.DOWN, Direction.Axis.Y),
    LEFT(Direction::getCounterClockWise, Direction.Axis.X),
    RIGHT(Direction::getClockWise, Direction.Axis.X),
    FRONT(Function.identity(), Direction.Axis.Z),
    BACK(Direction::getOpposite, Direction.Axis.Z);

    final Function<Direction, Direction> actualFacing;
    public final Direction.Axis axis;

    /**
     * Creates a relative direction.
     *
     * @param actualFacing resolver from controller facing to world direction
     * @param axis         logical pattern axis represented by this direction
     */
    RelativeDirection(Function<Direction, Direction> actualFacing, Direction.Axis axis) {
        this.actualFacing = actualFacing;
        this.axis = axis;
    }

    /**
     * Chooses the aisle/depth direction for an editor layer axis and controller
     * front.
     *
     * @param layerAxis      world axis perpendicular to preview slices
     * @param controllerFace horizontal controller front direction
     * @return relative direction used when advancing between aisles
     * @throws IllegalArgumentException when the controller face is vertical
     */
    public static RelativeDirection getAisleDirection(Direction.Axis layerAxis, Direction controllerFace) {
        return switch (controllerFace) {
            case NORTH -> switch (layerAxis) {
                case X -> RIGHT;
                case Y -> UP;
                case Z -> BACK;
            };
            case SOUTH -> switch (layerAxis) {
                case X -> LEFT;
                case Y -> UP;
                case Z -> FRONT;
            };
            case WEST -> switch (layerAxis) {
                case X -> BACK;
                case Y -> UP;
                case Z -> LEFT;
            };
            case EAST -> switch (layerAxis) {
                case X -> FRONT;
                case Y -> UP;
                case Z -> RIGHT;
            };
            default -> throw new IllegalArgumentException("Invalid controller face: " + controllerFace);
        };
    }

    /**
     * Chooses the relative direction for the x coordinate inside a preview
     * slice.
     *
     * @param layerAxis      world axis perpendicular to preview slices
     * @param controllerFace horizontal controller front direction
     * @return relative direction for increasing slice x
     * @throws IllegalArgumentException when the controller face is vertical
     */
    public static RelativeDirection getSliceXDirection(Direction.Axis layerAxis, Direction controllerFace) {
        return switch (controllerFace) {
            case NORTH -> switch (layerAxis) {
                case X -> UP;
                case Y -> RIGHT;
                case Z -> RIGHT;
            };
            case SOUTH -> switch (layerAxis) {
                case X -> UP;
                case Y -> LEFT;
                case Z -> LEFT;
            };
            case WEST -> switch (layerAxis) {
                case X -> UP;
                case Y -> BACK;
                case Z -> BACK;
            };
            case EAST -> switch (layerAxis) {
                case X -> UP;
                case Y -> FRONT;
                case Z -> FRONT;
            };
            default -> throw new IllegalArgumentException("Invalid controller face: " + controllerFace);
        };
    }

    /**
     * Chooses the relative direction for the y coordinate inside a preview
     * slice.
     *
     * @param layerAxis      world axis perpendicular to preview slices
     * @param controllerFace horizontal controller front direction
     * @return relative direction for increasing slice y
     * @throws IllegalArgumentException when the controller face is vertical
     */
    public static RelativeDirection getSliceYDirection(Direction.Axis layerAxis, Direction controllerFace) {
        return switch (controllerFace) {
            case NORTH -> switch (layerAxis) {
                case X -> BACK;
                case Y -> BACK;
                case Z -> UP;
            };
            case SOUTH -> switch (layerAxis) {
                case X -> FRONT;
                case Y -> FRONT;
                case Z -> UP;
            };
            case WEST -> switch (layerAxis) {
                case X -> LEFT;
                case Y -> LEFT;
                case Z -> UP;
            };
            case EAST -> switch (layerAxis) {
                case X -> RIGHT;
                case Y -> RIGHT;
                case Z -> UP;
            };
            default -> throw new IllegalArgumentException("Invalid controller face: " + controllerFace);
        };
    }

    /**
     * Resolves this relative direction to a world direction.
     *
     * @param facing horizontal controller facing
     * @return actual world direction for this relative direction
     */
    public Direction getActualFacing(Direction facing) {
        return actualFacing.apply(facing);
    }

    /**
     * Checks whether two relative directions use the same logical axis.
     *
     * @param dir direction to compare
     * @return {@code true} when both directions are on the same pattern axis
     */
    public boolean isSameAxis(RelativeDirection dir) {
        return this.axis == dir.axis;
    }

}
