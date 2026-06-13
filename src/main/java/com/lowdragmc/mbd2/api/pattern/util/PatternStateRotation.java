package com.lowdragmc.mbd2.api.pattern.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Objects;

public final class PatternStateRotation {
    private PatternStateRotation() {
    }

    public static Rotation horizontalRotation(Direction from, Direction to) {
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

    public static BlockState rotate(BlockState state, Rotation rotation) {
        if (state == null || rotation == null || rotation == Rotation.NONE) {
            return state;
        }

        BlockState rotated = state.rotate(rotation);
        for (Property<?> property : state.getProperties()) {
            if (!rotated.hasProperty(property)) {
                continue;
            }

            Object originalValue = getValue(state, property);
            if (!Objects.equals(originalValue, getValue(rotated, property))) {
                continue;
            }

            if (originalValue instanceof Direction direction) {
                Direction rotatedDirection = rotation.rotate(direction);
                if (rotatedDirection != direction && property.getPossibleValues().contains(rotatedDirection)) {
                    rotated = setValue(rotated, property, rotatedDirection);
                }
            } else if (originalValue instanceof Direction.Axis axis) {
                Direction.Axis rotatedAxis = rotateAxis(axis, rotation);
                if (rotatedAxis != axis && property.getPossibleValues().contains(rotatedAxis)) {
                    rotated = setValue(rotated, property, rotatedAxis);
                }
            }
        }
        return rotateNamedBooleanProperties(state, rotated, rotation);
    }

    private static Direction.Axis rotateAxis(Direction.Axis axis, Rotation rotation) {
        if (axis == Direction.Axis.Y) {
            return axis;
        }
        return rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis();
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

    private static BlockState rotateNamedBooleanProperties(BlockState original, BlockState rotated, Rotation rotation) {
        for (Direction direction : Direction.values()) {
            Property<?> source = findBooleanProperty(original, direction.getSerializedName());
            Direction targetDirection = rotation.rotate(direction);
            Property<?> target = findBooleanProperty(rotated, targetDirection.getSerializedName());
            if (source != null && target != null) {
                rotated = setValue(rotated, target, getValue(original, source));
            }
        }

        for (Direction.Axis axis : Direction.Axis.values()) {
            Property<?> source = findBooleanProperty(original, axis.getName());
            Direction.Axis targetAxis = rotateAxis(axis, rotation);
            Property<?> target = findBooleanProperty(rotated, targetAxis.getName());
            if (source != null && target != null) {
                rotated = setValue(rotated, target, getValue(original, source));
            }
        }

        return rotated;
    }

    private static Property<?> findBooleanProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name) && isBooleanProperty(property)) {
                return property;
            }
        }
        return null;
    }

    private static boolean isBooleanProperty(Property<?> property) {
        return property.getPossibleValues().contains(Boolean.TRUE)
                && property.getPossibleValues().contains(Boolean.FALSE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getValue(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setValue(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property) property, (Comparable) value);
    }
}
