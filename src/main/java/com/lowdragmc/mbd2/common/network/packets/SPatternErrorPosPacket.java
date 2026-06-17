package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.mbd2.client.renderer.MultiblockInWorldPreviewRenderer;
import com.lowdragmc.mbd2.config.ConfigHolder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Server-to-client packet that highlights a multiblock pattern error position.
 *
 * <p>The multiblock debugger sends this packet after server-side validation fails with a concrete error position. The
 * client displays a temporary in-world marker for the configured duration.</p>
 */
@NoArgsConstructor
@AllArgsConstructor
public class SPatternErrorPosPacket implements IPacket {
    /**
     * World position of the pattern error to highlight.
     */
    public BlockPos pos;

    /**
     * Writes the error position to the network buffer.
     *
     * @param buf destination packet buffer
     */
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    /**
     * Reads the error position from the network buffer.
     *
     * @param buf source packet buffer
     */
    @Override
    public void decode(FriendlyByteBuf buf) {
        pos = buf.readBlockPos();
    }

    /**
     * Shows the error marker on the logical client.
     *
     * <p>Side effects: updates the client preview renderer. The duration is configured in seconds and converted to
     * ticks.</p>
     *
     * @param handler LowDragLib packet context
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void execute(IHandlerContext handler) {
        MultiblockInWorldPreviewRenderer.showPatternErrorPos(pos, ConfigHolder.multiblockPatternErrorPosDuration * 20);
    }
}
