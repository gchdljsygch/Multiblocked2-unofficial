package com.lowdragmc.mbd2.core.mixins;

import com.lowdragmc.lowdraglib.async.AsyncThreadData;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows MBD2's controlled worker threads to read already-loaded level data without going
 * through main-thread-only chunk lookup paths.
 *
 * <p>The injected reads are server-side only and are restricted to MBD2/LDLib thread
 * services. They use {@code getChunkNow} and {@link #isLoaded(BlockPos)} so they never
 * request chunk loads. The mixin returns cached block entities and block states for
 * multiblock pattern checks and async recipe work that need read-only world access.</p>
 */
@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor {

    @Shadow @Final public boolean isClientSide;

    @Shadow @Final private Thread thread;

    @Shadow public abstract boolean isLoaded(BlockPos pPos);

    @Unique
    private ChunkAccess mbd2$getChunkNow(int pX, int pZ) {
        return this.getChunkSource().getChunkNow(pX, pZ);
    }

    /**
     * Serves block-entity reads from loaded chunks for approved worker threads.
     *
     * @param pos requested block position
     * @param cir cancellable callback receiving the cached block entity, or falling through
     *            to vanilla when the request is not an approved off-thread read
     */
    @Inject(method = "getBlockEntity", at = @At(value = "HEAD"), cancellable = true)
    private void getTileEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (!this.isClientSide && Thread.currentThread() != this.thread && (MultiblockWorldSavedData.isThreadService() || AsyncThreadData.isThreadService()) && isLoaded(pos)) {
            ChunkAccess chunk = this.mbd2$getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk instanceof LevelChunk levelChunk) {
                cir.setReturnValue(levelChunk.getBlockEntities().get(pos));
            }
        }
    }

    /**
     * Serves block-state reads from loaded chunks for approved worker threads.
     *
     * @param pos requested block position
     * @param cir cancellable callback receiving the cached block state, or falling through
     *            to vanilla when the request is not an approved off-thread read
     */
    @Inject(method = "getBlockState", at = @At(value = "HEAD"), cancellable = true)
    private void getBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!this.isClientSide && Thread.currentThread() != this.thread && (MultiblockWorldSavedData.isThreadService() || AsyncThreadData.isThreadService()) && isLoaded(pos)) {
            ChunkAccess chunk = this.mbd2$getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                cir.setReturnValue(chunk.getBlockState(pos));
            }
        }
    }

}
