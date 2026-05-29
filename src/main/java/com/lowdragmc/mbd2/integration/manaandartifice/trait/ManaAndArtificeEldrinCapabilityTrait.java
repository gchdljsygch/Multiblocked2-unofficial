package com.lowdragmc.mbd2.integration.manaandartifice.trait;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.RecipeHandlerTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.integration.manaandartifice.EldrinPower;
import com.lowdragmc.mbd2.integration.manaandartifice.ManaAndArtificeEldrinRecipeCapability;
import com.mna.api.ManaAndArtificeMod;
import com.mna.api.affinity.Affinity;
import com.mna.api.blocks.PlayerOwnershipRecord;
import com.mna.api.blocks.tile.IEldrinCapacitorTile;
import com.mna.api.events.EldrinPowerTransferredEvent;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Getter
public class ManaAndArtificeEldrinCapabilityTrait extends SimpleCapabilityTrait implements IEldrinCapacitorTile {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ManaAndArtificeEldrinCapabilityTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Persisted
    @DescSynced
    public final CopiableEldrinPowerStorage storage;
    @Persisted
    @DescSynced
    private boolean isPublic;
    @Persisted
    @DescSynced
    private boolean shareWithTeam;
    @Persisted
    @DescSynced
    private boolean shareWithFaction;
    @Persisted
    @DescSynced
    private net.minecraft.nbt.CompoundTag ownerTag;

    private final EldrinRecipeHandler recipeHandler = new EldrinRecipeHandler();
    private PlayerOwnershipRecord placedBy;

    public ManaAndArtificeEldrinCapabilityTrait(MBDMachine machine, ManaAndArtificeEldrinCapabilityTraitDefinition definition) {
        super(machine, definition);
        storage = createStorage();
        storage.setOnContentsChanged(this::notifyListeners);
        isPublic = definition.isPublic();
        shareWithTeam = definition.isShareWithTeam();
        shareWithFaction = definition.isShareWithFaction();
        placedBy = PlayerOwnershipRecord.of(ownerTag);
    }

    @Override
    public ManaAndArtificeEldrinCapabilityTraitDefinition getDefinition() {
        return (ManaAndArtificeEldrinCapabilityTraitDefinition) super.getDefinition();
    }

    @Override
    public void onLoadingTraitInPreview() {
        for (var affinity : getAffinities()) {
            storage.charge(affinity, getDefinition().getCapacity() / 2);
        }
    }

    protected CopiableEldrinPowerStorage createStorage() {
        return new CopiableEldrinPowerStorage(getDefinition().getCapacity(), getDefinition().getAffinities());
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

    @Override
    public void onMachineLoad() {
        refreshOwner();
        pushSupplier();
    }

    @Override
    public void onChunkUnloaded() {
        popSupplier();
    }

    @Override
    public void onMachineUnLoad() {
        popSupplier();
    }

    @Override
    public void onMachineRemoved() {
        popSupplier();
    }

    @Override
    public void onMachineDrop(net.minecraft.world.entity.Entity entity, List<ItemStack> drops) {
        popSupplier();
    }

    public void onMachinePlaced(@Nullable LivingEntity entity) {
        if (entity instanceof Player player) {
            setPlacedBy(player);
        }
        pushSupplier();
    }

    public void pushSupplier() {
        var level = getMachine().getLevel();
        if (level != null && !level.isClientSide) {
            level.getChunkAt(getMachine().getPos()).getCapability(ManaAndArtificeMod.getChunkMagicCapability())
                    .ifPresent(chunkMagic -> chunkMagic.pushKnownEldrinSupplier(getMachine().getPos()));
        }
    }

    public void popSupplier() {
        var level = getMachine().getLevel();
        if (level != null && !level.isClientSide && level.hasChunkAt(getMachine().getPos())) {
            level.getChunkAt(getMachine().getPos()).getCapability(ManaAndArtificeMod.getChunkMagicCapability())
                    .ifPresent(chunkMagic -> chunkMagic.popKnownEldrinSupplier(getMachine().getPos()));
        }
    }

    @Override
    public List<Affinity> getAffinities() {
        return storage.getAffinities();
    }

    @Override
    public float getChargeRate() {
        return getDefinition().getChargeRate();
    }

    @Override
    public float getRateLimit() {
        return getDefinition().getTransferRate();
    }

    @Override
    public float getCapacity(Affinity affinity) {
        return storage.getCapacity(affinity);
    }

    @Override
    public float getCharge(Affinity affinity) {
        return storage.getCharge(affinity);
    }

    @Override
    public void setCharge(Affinity affinity, float amount) {
        storage.setCharge(affinity, amount);
    }

    @Override
    public float charge(Affinity affinity, float amount) {
        return storage.charge(affinity, amount);
    }

    @Override
    public float consume(Affinity affinity, float amount) {
        return storage.consume(affinity, amount);
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

    @Override
    public boolean shareWithTeam() {
        return shareWithTeam;
    }

    @Override
    public boolean shareWithFaction() {
        return shareWithFaction;
    }

    @Override
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        notifyListeners();
    }

    @Override
    public void setTeamShare(boolean shareWithTeam) {
        this.shareWithTeam = shareWithTeam;
        notifyListeners();
    }

    @Override
    public void setFactionShare(boolean shareWithFaction) {
        this.shareWithFaction = shareWithFaction;
        notifyListeners();
    }

    @Override
    public UUID getPlacedBy() {
        refreshOwner();
        return placedBy.getPlayerProfileID();
    }

    @Override
    public String getPlacedByPlayerName() {
        refreshOwner();
        return placedBy.getDisplayName();
    }

    @Override
    public String getPlacedByTeam() {
        refreshOwner();
        return placedBy.getTeam();
    }

    @Override
    public com.mna.api.faction.IFaction getPlacedByFaction() {
        refreshOwner();
        return placedBy.getFaction();
    }

    @Override
    public void setPlacedBy(Player player) {
        placedBy = PlayerOwnershipRecord.of(player);
        ownerTag = placedBy.save(getMachine().getLevel());
        notifyListeners();
    }

    @Override
    public float supply(PlayerOwnershipRecord player, Vec3 dest, Affinity affinity, float amount, boolean testOnly) {
        if (!canSupply(affinity, player)) {
            return 0;
        }
        var rateLimit = getRateLimit();
        if (rateLimit > 0 && amount > rateLimit) {
            amount = rateLimit;
        }
        if (testOnly) {
            return Math.min(amount, getCharge(affinity));
        }
        var supplied = consume(affinity, amount);
        if (supplied > 0) {
            var event = new EldrinPowerTransferredEvent(affinity, supplied, Vec3.atCenterOf(getMachine().getPos()), dest);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                charge(affinity, supplied);
                return 0;
            }
        }
        return supplied;
    }

    private void refreshOwner() {
        if (placedBy == null) {
            placedBy = PlayerOwnershipRecord.of(ownerTag);
        }
        placedBy.refresh(getMachine().getLevel());
        ownerTag = placedBy.save(getMachine().getLevel());
    }

    public class EldrinRecipeHandler extends RecipeHandlerTrait<EldrinPower> {
        protected EldrinRecipeHandler() {
            super(ManaAndArtificeEldrinCapabilityTrait.this, ManaAndArtificeEldrinRecipeCapability.CAP);
        }

        @Override
        public List<EldrinPower> handleRecipeInner(IO io, MBDRecipe recipe, List<EldrinPower> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            var capability = simulate ? storage.copy() : storage;
            var remaining = new java.util.ArrayList<EldrinPower>();
            for (var power : left) {
                var required = power.amount();
                if (io == IO.IN) {
                    var consumed = capability.consume(power.affinity(), required);
                    if (!simulate && consumed > 0) {
                        RecipeConsumptionTracker.record(ManaAndArtificeEldrinRecipeCapability.CAP,
                                new EldrinPower(power.affinity(), consumed), slotName);
                    }
                    required -= consumed;
                } else if (io == IO.OUT) {
                    required -= capability.charge(power.affinity(), required);
                }
                if (required > 0) {
                    remaining.add(new EldrinPower(power.affinity(), required));
                }
            }
            return remaining.isEmpty() ? null : remaining;
        }
    }
}
