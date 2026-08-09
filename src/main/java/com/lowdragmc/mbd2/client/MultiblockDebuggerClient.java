package com.lowdragmc.mbd2.client;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.client.renderer.MultiblockInWorldPreviewRenderer;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side helpers for visualizing why a multiblock structure does not
 * match its pattern.
 *
 * <p>The business goal is to turn the active pattern preview into concrete
 * world positions that can be highlighted for the player. Methods in this
 * class read client world state and update client-only preview/overlay systems;
 * they should be called from the logical client thread.</p>
 */
public final class MultiblockDebuggerClient {
    private MultiblockDebuggerClient() {
    }

    /**
     * Shows the normal multiblock preview and highlights only occupied blocks
     * whose block type differs from the expected pattern.
     *
     * <p>Preconditions: {@code controller} and {@code controllerPos} must refer
     * to the clicked controller in the client level. Side effects: starts or
     * refreshes the in-world preview and replaces the debug overlay positions.
     * The method reads world blocks but does not mutate the level.</p>
     *
     * @param controller    multiblock controller being inspected
     * @param controllerPos world position of that controller
     * @param durationTicks preview and overlay lifetime in client ticks; values
     *                      less than or equal to {@code 0} clear the overlay through
     *                      {@link MultiblockDebugOverlay#show(Set, int)}
     */
    public static void showPreviewWithOccupiedMismatch(MBDMultiblockMachine controller, BlockPos controllerPos, int durationTicks) {
        MultiblockInWorldPreviewRenderer.showPreview(controllerPos, controller, durationTicks);
        var result = collectMismatches(controller, controllerPos);
        MultiblockDebugOverlay.show(result.occupiedWrong, durationTicks);
    }

    /**
     * Highlights every pattern position that is either missing or occupied by a
     * different block type.
     *
     * <p>Business goal: provide the debugger gadget with a broader mismatch
     * view than {@link #showPreviewWithOccupiedMismatch(MBDMultiblockMachine,
     * BlockPos, int)}. Preconditions and thread expectations are the same.
     * Side effects: replaces the debug overlay; it does not start the preview
     * renderer and does not mutate world blocks.</p>
     *
     * @param controller    multiblock controller being inspected
     * @param controllerPos world position of that controller
     * @param durationTicks overlay lifetime in client ticks; values less than or
     *                      equal to {@code 0} clear the overlay
     */
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
        int patternIndex = MultiblockInWorldPreviewRenderer.getCurrentPreviewPatternIndex(controllerPos, controller);
        var shapeInfos = definition.getPatternShapeInfos(controller, patternIndex);
        Map<BlockPos, BlockState> result = new HashMap<>();
        if (shapeInfos.length == 0) return result;
        var shapeInfo = shapeInfos[0];

        var blocks = shapeInfo.getBlocks();
        var controllerMarker = MultiblockPreviewLayout.findControllerMarker(blocks);
        if (controllerMarker == null) {
            return result;
        }

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    BlockInfo blockInfo = column[z];
                    if (blockInfo == null) continue;
                    BlockState blockState = blockInfo.getBlockState();
                    if (blockState == null) continue;

                    BlockPos offset = MultiblockPreviewLayout.offsetFromController(x, y, z, controllerMarker, front);
                    if (offset.equals(BlockPos.ZERO)) continue;
                    blockState = MultiblockPreviewLayout.orientMachineState(blockState, front);

                    BlockPos realPos = controllerPos.offset(offset);
                    result.put(realPos, blockState);
                }
            }
        }
        return result;
    }

    private record MismatchResult(Set<BlockPos> occupiedWrong, Set<BlockPos> missing) {
    }
}
