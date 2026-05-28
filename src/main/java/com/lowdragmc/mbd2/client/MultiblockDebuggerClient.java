package com.lowdragmc.mbd2.client;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.client.renderer.MultiblockInWorldPreviewRenderer;
import com.lowdragmc.mbd2.common.block.MBDMachineBlock;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MultiblockDebuggerClient {
    private MultiblockDebuggerClient() {
    }

    public static void showPreviewWithOccupiedMismatch(MBDMultiblockMachine controller, BlockPos controllerPos, int durationTicks) {
        MultiblockInWorldPreviewRenderer.showPreview(controllerPos, controller, durationTicks);
        var result = collectMismatches(controller, controllerPos);
        MultiblockDebugOverlay.show(result.occupiedWrong, durationTicks);
    }

    public static void showAllMismatches(MBDMultiblockMachine controller, BlockPos controllerPos, int durationTicks) {
        var result = collectMismatches(controller, controllerPos);
        Set<BlockPos> all = new HashSet<>(result.occupiedWrong.size() + result.missing.size());
        all.addAll(result.occupiedWrong);
        all.addAll(result.missing);
        MultiblockDebugOverlay.show(all, durationTicks);
    }

    private static MismatchResult collectMismatches(MBDMultiblockMachine controller, BlockPos controllerPos) {
        Map<BlockPos, BlockState> expected = collectExpectedStates(controller, controllerPos);
        Level level = controller.getLevel();
        Set<BlockPos> occupiedWrong = new HashSet<>();
        Set<BlockPos> missing = new HashSet<>();
        for (var entry : expected.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState expectedState = entry.getValue();
            BlockState actual = level.getBlockState(pos);

            if (actual.isAir()) {
                missing.add(pos);
                continue;
            }
            if (expectedState.getBlock() != actual.getBlock()) {
                occupiedWrong.add(pos);
            }
        }
        return new MismatchResult(occupiedWrong, missing);
    }

    private static Map<BlockPos, BlockState> collectExpectedStates(MBDMultiblockMachine controller, BlockPos controllerPos) {
        var front = controller.getFrontFacing().orElse(Direction.NORTH);
        MultiblockMachineDefinition definition = controller.getDefinition();
        var shapeInfos = definition.shapeInfoFactory().apply(definition);
        Map<BlockPos, BlockState> result = new HashMap<>();
        if (shapeInfos.length == 0) return result;
        var shapeInfo = shapeInfos[0];

        var blocks = shapeInfo.getBlocks();
        BlockPos controllerPatternPos = null;
        Direction controllerPatternFront = Direction.NORTH;
        int maxY = 0;

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            maxY = Math.max(maxY, aisle.length);
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    if (column[z] instanceof ControllerBlockInfo info) {
                        controllerPatternPos = new BlockPos(x, y, z);
                        controllerPatternFront = info.getFacing();
                        continue;
                    }
                    BlockState blockState = column[z].getBlockState();
                    if (blockState != null && blockState.getBlock() instanceof MBDMachineBlock machineBlock &&
                            machineBlock.getDefinition() instanceof MultiblockMachineDefinition def) {
                        controllerPatternPos = new BlockPos(x, y, z);
                        if (def.blockProperties().rotationState().property.isPresent()) {
                            controllerPatternFront = blockState.getValue(def.blockProperties().rotationState().property.get());
                        }
                    }
                }
            }
        }

        if (controllerPatternPos == null) {
            return result;
        }

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                if (y >= maxY) continue;
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    BlockState blockState = column[z].getBlockState();
                    if (blockState == null) continue;
                    Block block = blockState.getBlock();
                    if (block == null) continue;

                    BlockPos offset = new BlockPos(x, y, z).subtract(controllerPatternPos);
                    if (offset.equals(BlockPos.ZERO)) continue;

                    offset = switch (controllerPatternFront) {
                        case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
                        case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
                        case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
                        default -> offset.rotate(Rotation.NONE);
                    };
                    offset = switch (front) {
                        case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
                        case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
                        case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
                        default -> offset.rotate(Rotation.NONE);
                    };
                    offset = rotateByFrontAxis(offset, front, Rotation.NONE);

                    if (blockState.getBlock() instanceof MBDMachineBlock machineBlock) {
                        RotationState rotationState = machineBlock.getRotationState();
                        if (rotationState != RotationState.NONE && rotationState.property.isPresent()) {
                            Direction face = blockState.getValue(rotationState.property.get());
                            if (face.getAxis() != Direction.Axis.Y) {
                                face = switch (front) {
                                    case NORTH, UP, DOWN -> front;
                                    case SOUTH -> face.getOpposite();
                                    case WEST -> face.getCounterClockWise();
                                    case EAST -> face.getClockWise();
                                };
                            }
                            if (rotationState.test(face)) {
                                blockState = blockState.setValue(rotationState.property.get(), face);
                            }
                        }
                    }

                    BlockPos realPos = controllerPos.offset(offset);
                    result.put(realPos, blockState);
                }
            }
        }
        return result;
    }

    private static BlockPos rotateByFrontAxis(BlockPos pos, Direction front, Rotation rotation) {
        if (front.getAxis() == Direction.Axis.X) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(-pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * -pos.getY());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getX(), front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getY());
                default -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            };
        } else if (front.getAxis() == Direction.Axis.Y) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -front.getAxisDirection().getStep() * pos.getX());
                case CLOCKWISE_180 -> new BlockPos(front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        pos.getY());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getX());
                default -> new BlockPos(-front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -pos.getY());
            };
        } else if (front.getAxis() == Direction.Axis.Z) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * -pos.getY(),
                        front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                default -> pos;
            };
        }
        return pos;
    }

    private record MismatchResult(Set<BlockPos> occupiedWrong, Set<BlockPos> missing) {
    }
}
