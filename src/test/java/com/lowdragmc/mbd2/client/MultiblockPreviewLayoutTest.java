package com.lowdragmc.mbd2.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockPreviewLayoutTest {

    @Test
    void rotatesOffsetsOnlyByThePatternAndPlacedHorizontalFacings() {
        var marker = new MultiblockPreviewLayout.ControllerMarker(new BlockPos(4, 2, 5), Direction.NORTH);
        BlockPos relative = new BlockPos(2, 1, -3);

        BlockPos actual = MultiblockPreviewLayout.offsetFromController(6, 3, 2, marker, Direction.EAST);

        assertEquals(relative.rotate(Rotation.COUNTERCLOCKWISE_90), actual);
    }

    @Test
    void leavesVerticalControllerFacingAsAnIdentityTransform() {
        var marker = new MultiblockPreviewLayout.ControllerMarker(new BlockPos(4, 2, 5), Direction.NORTH);
        BlockPos relative = new BlockPos(2, 1, -3);

        BlockPos actual = MultiblockPreviewLayout.offsetFromController(6, 3, 2, marker, Direction.UP);

        assertEquals(relative, actual);
    }
}
