package com.lowdragmc.mbd2.core.mixins;

import com.lowdragmc.lowdraglib.async.AsyncThreadData;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Provides safe, non-loading chunk lookup for MBD2/LDLib worker threads.
 *
 * <p>Vanilla {@link ServerChunkCache#getChunkNow(int, int)} is main-thread oriented. This
 * mixin intercepts approved off-thread calls from MBD2 multiblock or async recipe services
 * and reads already-visible full chunks directly. It maintains a tiny synchronized
 * four-entry cache to reduce repeated future lookups during pattern scans.</p>
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkProviderMixin {

    @Shadow @Final Thread mainThread;

    @Unique
    private final long[] mbd2$mbdLastChunkPos = new long[4];

    @Unique
    private final LevelChunk[] mbd2$mbdLastChunk = new LevelChunk[4];

    @Shadow @Nullable protected abstract ChunkHolder getVisibleChunkIfPresent(long p_217213_1_);

    /**
     * Stores a visible chunk in the small most-recently-used cache.
     *
     * @param pos packed chunk position
     * @param chunkAccess full level chunk to cache
     */
    @Unique
    private void mbd2$storeInCache(long pos, LevelChunk chunkAccess) {
        synchronized (this.mbd2$mbdLastChunkPos) {
            for(int i = 3; i > 0; --i) {
                this.mbd2$mbdLastChunkPos[i] = this.mbd2$mbdLastChunkPos[i - 1];
                this.mbd2$mbdLastChunk[i] = this.mbd2$mbdLastChunk[i - 1];
            }

            this.mbd2$mbdLastChunkPos[0] = pos;
            this.mbd2$mbdLastChunk[0] = chunkAccess;
        }
    }

    /**
     * Clears the off-thread chunk cache whenever vanilla clears its own cache.
     *
     * @param ci mixin callback info
     */
    @Inject(method = "clearCache", at = @At(value = "TAIL"))
    private void injectClearCache(CallbackInfo ci) {
        synchronized (this.mbd2$mbdLastChunkPos) {
            Arrays.fill(this.mbd2$mbdLastChunkPos, ChunkPos.INVALID_CHUNK_POS);
            Arrays.fill(this.mbd2$mbdLastChunk, null);
        }
    }

    /**
     * Handles approved off-thread {@code getChunkNow} calls without loading chunks.
     *
     * @param pChunkX chunk x coordinate
     * @param pChunkZ chunk z coordinate
     * @param cir cancellable callback receiving a visible full chunk or {@code null}
     */
    @Inject(method = "getChunkNow", at = @At(value = "HEAD"), cancellable = true)
    private void getTileEntity(int pChunkX, int pChunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread && (MultiblockWorldSavedData.isThreadService() || AsyncThreadData.isThreadService())) {
            long i = ChunkPos.asLong(pChunkX, pChunkZ);

            for(int j = 0; j < 4; ++j) {
                if (i == this.mbd2$mbdLastChunkPos[j]) {
                    cir.setReturnValue(this.mbd2$mbdLastChunk[j]);
                    return;
                }
            }

            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(i);
            if (chunkholder != null) {
                Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either = chunkholder.getFutureIfPresent(ChunkStatus.FULL).getNow(null);
                if (either != null) {
                    ChunkAccess chunk = either.left().orElse(null);
                    if (chunk instanceof LevelChunk levelChunk) {
                        mbd2$storeInCache(i, levelChunk);
                        cir.setReturnValue(levelChunk);
                        return;
                    }
                }
            }
            cir.setReturnValue(null);
        }
    }

}
