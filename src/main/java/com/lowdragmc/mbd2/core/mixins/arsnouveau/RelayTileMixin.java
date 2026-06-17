package com.lowdragmc.mbd2.core.mixins.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.block.tile.RelayTile;
import com.hollingsworth.arsnouveau.common.items.DominionWand;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import com.lowdragmc.mbd2.integration.arsnouveau.ArsNouveauSourceRelayCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teaches Ars Nouveau relays how to connect to MBD Source capability traits.
 *
 * <p>Ars Nouveau's relay tile only knows about its own source machines. This mixin intercepts
 * Dominion Wand connection completion and relay ticking so MBD Source providers can be used as
 * either the input or output endpoint. Server-side ticks are cancelled only while at least one
 * endpoint points to an MBD Source provider.</p>
 */
@Mixin(value = RelayTile.class, remap = false)
public abstract class RelayTileMixin extends AbstractSourceMachine {
    @Shadow
    private @Nullable BlockPos toPos;
    @Shadow
    private @Nullable BlockPos fromPos;
    @Shadow
    public boolean disabled;

    protected RelayTileMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Shadow
    public abstract int getMaxDistance();

    /**
     * Connects the selected MBD Source provider as the relay's send target.
     *
     * @param storedPos    position selected by the Dominion Wand
     * @param storedEntity selected entity, unused for block source providers
     * @param player       player completing the connection
     * @param ci           callback cancelled when MBD handles the connection attempt
     */
    @Inject(method = "onFinishedConnectionFirst", at = @At("HEAD"), cancellable = true)
    private void mbd2$connectMBDSourceAsTarget(@Nullable BlockPos storedPos, @Nullable LivingEntity storedEntity, Player player, CallbackInfo ci) {
        if (storedPos == null || level.isClientSide || storedPos.equals(getBlockPos()) || !ArsNouveauSourceRelayCompat.isMBDSource(level, storedPos)) {
            return;
        }
        ci.cancel();
        if (mbd2$setSendToCompat(storedPos.immutable())) {
            PortUtil.sendMessage(player, Component.translatable("ars_nouveau.connections.send", DominionWand.getPosString(storedPos)));
            ParticleUtil.beam(storedPos, worldPosition, level);
        } else {
            PortUtil.sendMessage(player, Component.translatable("ars_nouveau.connections.fail"));
        }
    }

    /**
     * Connects the selected MBD Source provider as the relay's take-from source.
     *
     * @param storedPos    position selected by the Dominion Wand
     * @param storedEntity selected entity, unused for block source providers
     * @param player       player completing the connection
     * @param ci           callback cancelled when MBD handles the connection attempt
     */
    @Inject(method = "onFinishedConnectionLast", at = @At("HEAD"), cancellable = true)
    private void mbd2$connectMBDSourceAsSource(@Nullable BlockPos storedPos, @Nullable LivingEntity storedEntity, Player player, CallbackInfo ci) {
        if (storedPos == null || storedPos.equals(getBlockPos()) || !ArsNouveauSourceRelayCompat.isMBDSource(level, storedPos)) {
            return;
        }
        ci.cancel();
        if (mbd2$setTakeFromCompat(storedPos.immutable())) {
            PortUtil.sendMessage(player, Component.translatable("ars_nouveau.connections.take", DominionWand.getPosString(storedPos)));
        } else {
            PortUtil.sendMessage(player, Component.translatable("ars_nouveau.connections.fail"));
        }
    }

    /**
     * Runs relay transfers against MBD Source endpoints on the server's once-per-second cadence.
     *
     * @param ci callback cancelled when MBD Source endpoints require custom transfer handling
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mbd2$tickMBDSourceConnections(CallbackInfo ci) {
        if (!mbd2$hasMBDSourceConnection()) {
            return;
        }
        ci.cancel();
        if (level.isClientSide || disabled || level.getGameTime() % 20 != 0) {
            return;
        }
        mbd2$transferFromSource();
        mbd2$transferToSource();
    }

    /**
     * Checks whether either relay endpoint currently points at an MBD Source provider.
     */
    private boolean mbd2$hasMBDSourceConnection() {
        return fromPos != null && ArsNouveauSourceRelayCompat.isMBDSource(level, fromPos) ||
                toPos != null && ArsNouveauSourceRelayCompat.isMBDSource(level, toPos);
    }

    /**
     * Stores an MBD Source provider as the relay's output endpoint.
     *
     * @param pos candidate target position
     * @return {@code true} when the endpoint is in range and exposes Source
     */
    private boolean mbd2$setSendToCompat(BlockPos pos) {
        if (!mbd2$closeEnough(pos) || ArsNouveauSourceRelayCompat.getSource(level, pos) == null) {
            return false;
        }
        toPos = pos;
        updateBlock();
        return true;
    }

    /**
     * Stores an MBD Source provider as the relay's input endpoint.
     *
     * @param pos candidate source position
     * @return {@code true} when the endpoint is in range and exposes Source
     */
    private boolean mbd2$setTakeFromCompat(BlockPos pos) {
        if (!mbd2$closeEnough(pos) || ArsNouveauSourceRelayCompat.getSource(level, pos) == null) {
            return false;
        }
        fromPos = pos;
        updateBlock();
        return true;
    }

    /**
     * Validates the relay distance rule shared with Ars Nouveau's own endpoints.
     *
     * @param pos candidate endpoint
     * @return {@code true} when the position is distinct from this relay and within max distance
     */
    private boolean mbd2$closeEnough(BlockPos pos) {
        return BlockUtil.distanceFrom(pos, worldPosition) <= getMaxDistance() && !pos.equals(getBlockPos());
    }

    /**
     * Pulls Source from an MBD endpoint into this relay.
     *
     * <p>If the remote block no longer exposes Source, the stored endpoint is cleared and the
     * relay block is updated.</p>
     */
    private void mbd2$transferFromSource() {
        if (fromPos == null || !level.isLoaded(fromPos)) {
            return;
        }
        var fromSource = ArsNouveauSourceRelayCompat.getSource(level, fromPos);
        if (fromSource == null) {
            fromPos = null;
            updateBlock();
            return;
        }
        if (transferSource(fromSource, this) > 0) {
            updateBlock();
            ParticleUtil.spawnFollowProjectile(level, fromPos, worldPosition);
        }
    }

    /**
     * Pushes Source from this relay into an MBD endpoint.
     *
     * <p>If the remote block no longer exposes Source, the stored endpoint is cleared and the
     * relay block is updated.</p>
     */
    private void mbd2$transferToSource() {
        if (toPos == null || !level.isLoaded(toPos)) {
            return;
        }
        var toSource = ArsNouveauSourceRelayCompat.getSource(level, toPos);
        if (toSource == null) {
            toPos = null;
            updateBlock();
            return;
        }
        if (transferSource(this, toSource) > 0) {
            ParticleUtil.spawnFollowProjectile(level, worldPosition, toPos);
        }
    }
}
