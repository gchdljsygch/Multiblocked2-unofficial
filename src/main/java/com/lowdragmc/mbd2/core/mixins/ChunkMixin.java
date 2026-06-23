package com.lowdragmc.mbd2.core.mixins;

import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks server chunk block-state writes so cached multiblock structures can react to
 * changes inside their watched areas.
 *
 * <p>The injected callback runs during {@link LevelChunk#setBlockState}. It only acts on
 * server levels and only for controller structures already indexed at the changed position.
 * The structure update is scheduled on the server executor because the chunk mutation may
 * be in the middle of vanilla state replacement.</p>
 */
@Mixin(LevelChunk.class)
public class ChunkMixin {
    @Final
    @Shadow
    Level level;

    /**
     * Notifies matching multiblock structures that a cached block state changed.
     *
     * <p>This callback deliberately does minimal work before returning to vanilla chunk
     * mutation. It does not load chunks and ignores client worlds.</p>
     *
     * @param pos      changed block position
     * @param state    new state being placed into the chunk
     * @param isMoving vanilla movement flag passed through from {@code setBlockState}
     * @param cir      mixin callback info for the original return value
     */
    // We want to be as quick as possible here
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void onAddingBlock(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            if (level instanceof ServerLevel serverLevel) {
                for (var structure : MultiblockWorldSavedData.getOrCreate(serverLevel).getControllerInPos(pos)) {
                    if (structure.isPosInCache(pos)) {
                        server.tell(new TickTask(0, () -> structure.onBlockStateChanged(pos, state)));
                    }
                }
            }
        }
    }

}
