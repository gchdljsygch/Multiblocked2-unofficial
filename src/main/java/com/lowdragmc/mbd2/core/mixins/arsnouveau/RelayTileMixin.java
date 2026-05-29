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

    private boolean mbd2$hasMBDSourceConnection() {
        return fromPos != null && ArsNouveauSourceRelayCompat.isMBDSource(level, fromPos) ||
                toPos != null && ArsNouveauSourceRelayCompat.isMBDSource(level, toPos);
    }

    private boolean mbd2$setSendToCompat(BlockPos pos) {
        if (!mbd2$closeEnough(pos) || ArsNouveauSourceRelayCompat.getSource(level, pos) == null) {
            return false;
        }
        toPos = pos;
        updateBlock();
        return true;
    }

    private boolean mbd2$setTakeFromCompat(BlockPos pos) {
        if (!mbd2$closeEnough(pos) || ArsNouveauSourceRelayCompat.getSource(level, pos) == null) {
            return false;
        }
        fromPos = pos;
        updateBlock();
        return true;
    }

    private boolean mbd2$closeEnough(BlockPos pos) {
        return BlockUtil.distanceFrom(pos, worldPosition) <= getMaxDistance() && !pos.equals(getBlockPos());
    }

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
