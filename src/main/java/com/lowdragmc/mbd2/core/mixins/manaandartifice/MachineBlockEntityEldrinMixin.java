package com.lowdragmc.mbd2.core.mixins.manaandartifice;

import com.lowdragmc.mbd2.integration.manaandartifice.ManaAndArtificeEldrinCompat;
import com.lowdragmc.mbd2.integration.manaandartifice.trait.ManaAndArtificeEldrinCapabilityTrait;
import com.mna.api.affinity.Affinity;
import com.mna.api.blocks.PlayerOwnershipRecord;
import com.mna.api.blocks.tile.IEldrinCapacitorTile;
import com.mna.api.faction.IFaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.UUID;

/**
 * Exposes MBD machine block entities as Mana and Artifice Eldrin capacitors.
 *
 * <p>The implementation is a thin adapter over {@link ManaAndArtificeEldrinCapabilityTrait}.
 * Machines without that trait return empty, zero, or non-sharing defaults so Mana and Artifice can
 * safely query every machine block entity through {@link IEldrinCapacitorTile}.</p>
 */
@Mixin(value = com.lowdragmc.mbd2.common.blockentity.MachineBlockEntity.class, remap = false)
public abstract class MachineBlockEntityEldrinMixin implements IEldrinCapacitorTile {
    /**
     * Looks up the Eldrin trait attached to this machine block entity.
     *
     * @return trait instance, or {@code null} when this machine does not expose Eldrin power
     */
    private ManaAndArtificeEldrinCapabilityTrait mbd2$eldrin() {
        return ManaAndArtificeEldrinCompat.getTrait((BlockEntity) (Object) this);
    }

    @Override
    public List<Affinity> getAffinities() {
        var trait = mbd2$eldrin();
        return trait == null ? List.of() : trait.getAffinities();
    }

    @Override
    public float getChargeRate() {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.getChargeRate();
    }

    @Override
    public float getRateLimit() {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.getRateLimit();
    }

    @Override
    public float getCapacity(Affinity affinity) {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.getCapacity(affinity);
    }

    @Override
    public float getCharge(Affinity affinity) {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.getCharge(affinity);
    }

    @Override
    public void setCharge(Affinity affinity, float amount) {
        var trait = mbd2$eldrin();
        if (trait != null) {
            trait.setCharge(affinity, amount);
        }
    }

    @Override
    public float charge(Affinity affinity, float amount) {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.charge(affinity, amount);
    }

    @Override
    public float consume(Affinity affinity, float amount) {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.consume(affinity, amount);
    }

    @Override
    public boolean isPublic() {
        var trait = mbd2$eldrin();
        return trait != null && trait.isPublic();
    }

    @Override
    public boolean shareWithTeam() {
        var trait = mbd2$eldrin();
        return trait != null && trait.shareWithTeam();
    }

    @Override
    public boolean shareWithFaction() {
        var trait = mbd2$eldrin();
        return trait != null && trait.shareWithFaction();
    }

    @Override
    public void setPublic(boolean isPublic) {
        var trait = mbd2$eldrin();
        if (trait != null) {
            trait.setPublic(isPublic);
        }
    }

    @Override
    public void setTeamShare(boolean shareWithTeam) {
        var trait = mbd2$eldrin();
        if (trait != null) {
            trait.setTeamShare(shareWithTeam);
        }
    }

    @Override
    public void setFactionShare(boolean shareWithFaction) {
        var trait = mbd2$eldrin();
        if (trait != null) {
            trait.setFactionShare(shareWithFaction);
        }
    }

    @Override
    public UUID getPlacedBy() {
        var trait = mbd2$eldrin();
        return trait == null ? null : trait.getPlacedBy();
    }

    @Override
    public String getPlacedByPlayerName() {
        var trait = mbd2$eldrin();
        return trait == null ? "" : trait.getPlacedByPlayerName();
    }

    @Override
    public String getPlacedByTeam() {
        var trait = mbd2$eldrin();
        return trait == null ? null : trait.getPlacedByTeam();
    }

    @Override
    public IFaction getPlacedByFaction() {
        var trait = mbd2$eldrin();
        return trait == null ? null : trait.getPlacedByFaction();
    }

    @Override
    public void setPlacedBy(Player player) {
        var trait = mbd2$eldrin();
        if (trait != null) {
            trait.setPlacedBy(player);
        }
    }

    @Override
    public float supply(PlayerOwnershipRecord player, net.minecraft.world.phys.Vec3 dest, Affinity affinity, float amount, boolean testOnly) {
        var trait = mbd2$eldrin();
        return trait == null ? 0 : trait.supply(player, dest, affinity, amount, testOnly);
    }
}
