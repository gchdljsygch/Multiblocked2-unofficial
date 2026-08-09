package com.lowdragmc.mbd2.client;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.common.block.MBDMachineBlock;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Shared coordinate and block-state transforms for in-world multiblock previews.
 */
@OnlyIn(Dist.CLIENT)
public final class MultiblockPreviewLayout {
    private MultiblockPreviewLayout() {
    }

    /**
     * Finds the explicit controller marker, falling back to the first multiblock controller state when a legacy shape
     * has no marker. An explicit marker always wins, regardless of its position in the shape.
     */
    @Nullable
    public static ControllerMarker findControllerMarker(BlockInfo[][][] blocks) {
        ControllerMarker fallback = null;
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    BlockInfo blockInfo = column[z];
                    if (blockInfo == null) continue;
                    if (blockInfo instanceof ControllerBlockInfo controllerInfo) {
                        Direction facing = controllerInfo.getFacing();
                        return new ControllerMarker(new BlockPos(x, y, z), facing == null ? Direction.NORTH : facing);
                    }
                    if (fallback != null) continue;

                    BlockState blockState = blockInfo.getBlockState();
                    if (blockState != null && blockState.getBlock() instanceof MBDMachineBlock machineBlock &&
                            machineBlock.getDefinition() instanceof MultiblockMachineDefinition definition) {
                        Direction facing = Direction.NORTH;
                        if (definition.blockProperties().rotationState().property.isPresent()) {
                            facing = blockState.getValue(definition.blockProperties().rotationState().property.get());
                        }
                        fallback = new ControllerMarker(new BlockPos(x, y, z), facing);
                    }
                }
            }
        }
        return fallback;
    }

    /**
     * Maps one pattern-grid coordinate into a controller-relative world offset.
     */
    public static BlockPos offsetFromController(int x, int y, int z, ControllerMarker marker,
                                                Direction controllerFacing) {
        BlockPos controllerPatternPos = marker.patternPosition();
        BlockPos offset = new BlockPos(x - controllerPatternPos.getX(), y - controllerPatternPos.getY(),
                z - controllerPatternPos.getZ());
        return rotateForFacing(rotateForFacing(offset, marker.patternFacing()), controllerFacing);
    }

    /**
     * Rotates machine block state properties so their facing matches the placed controller.
     */
    public static BlockState orientMachineState(BlockState blockState, Direction controllerFacing) {
        if (!(blockState.getBlock() instanceof MBDMachineBlock machineBlock)) {
            return blockState;
        }
        RotationState rotationState = machineBlock.getRotationState();
        if (rotationState == RotationState.NONE || rotationState.property.isEmpty()) {
            return blockState;
        }

        Direction face = blockState.getValue(rotationState.property.get());
        if (face.getAxis() != Direction.Axis.Y) {
            face = switch (controllerFacing) {
                case NORTH, UP, DOWN -> controllerFacing;
                case SOUTH -> face.getOpposite();
                case WEST -> face.getCounterClockWise();
                case EAST -> face.getClockWise();
            };
        }
        return rotationState.test(face) ? blockState.setValue(rotationState.property.get(), face) : blockState;
    }

    private static BlockPos rotateForFacing(BlockPos offset, Direction facing) {
        return switch (facing) {
            case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
            case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
            case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
            default -> offset;
        };
    }

    /**
     * Controller location and facing encoded by a preview shape.
     */
    public record ControllerMarker(BlockPos patternPosition, Direction patternFacing) {
        public ControllerMarker {
            Objects.requireNonNull(patternPosition, "patternPosition");
            Objects.requireNonNull(patternFacing, "patternFacing");
        }
    }
}
