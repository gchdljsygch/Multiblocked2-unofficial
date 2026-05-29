package com.lowdragmc.mbd2.integration.arsnouveau.trait;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.*;
import com.lowdragmc.mbd2.integration.arsnouveau.ArsNouveauSourceRecipeCapability;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ArsNouveauSourceCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ArsNouveauSourceCapabilityTrait.class);
    @Override
    public ManagedFieldHolder getFieldHolder() { return MANAGED_FIELD_HOLDER; }

    @Persisted
    @DescSynced
    public final CopiableSourceStorage storage;
    private final SourceRecipeHandler recipeHandler = new SourceRecipeHandler();
    private final MBDSourceProvider sourceProvider = new MBDSourceProvider();
    private boolean sourceProviderValid;

    public ArsNouveauSourceCapabilityTrait(MBDMachine machine, ArsNouveauSourceCapabilityTraitDefinition definition) {
        super(machine, definition);
        storage = createStorage();
        storage.setOnContentsChanged(this::notifyListeners);
    }

    @Override
    public ArsNouveauSourceCapabilityTraitDefinition getDefinition() {
        return (ArsNouveauSourceCapabilityTraitDefinition) super.getDefinition();
    }

    @Override
    public void onLoadingTraitInPreview() {
        storage.addSource(getDefinition().getCapacity() / 2);
    }

    protected CopiableSourceStorage createStorage() {
        return new CopiableSourceStorage(getDefinition().getCapacity(), getDefinition().getTransferRate());
    }

    public ISourceTile getRelaySource() {
        return new SourceStorageWrapper(storage, getCapabilityIO(null));
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

    @Override
    public @Nullable AutoIO getAutoIO() {
        return getDefinition().getAutoIO().isEnable() ? getDefinition().getAutoIO() : null;
    }

    @Override
    public void onMachineLoad() {
        sourceProviderValid = true;
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, () -> SourceManager.INSTANCE.addInterface(serverLevel, sourceProvider)));
        }
    }

    @Override
    public void onChunkUnloaded() {
        sourceProviderValid = false;
    }

    @Override
    public void onMachineUnLoad() {
        sourceProviderValid = false;
    }

    @Override
    public void onMachineRemoved() {
        sourceProviderValid = false;
    }

    @Override
    public void handleAutoIO(BlockPos port, Direction side, IO io) {
        Optional.ofNullable(getMachine().getLevel().getBlockEntity(port.relative(side)))
                .map(this::asSourceTile)
                .ifPresent(sourceTile -> {
                    if (io.support(IO.IN)) {
                        moveSource(sourceTile, storage, getDefinition().getTransferRate());
                    }
                    if (io.support(IO.OUT)) {
                        moveSource(storage, sourceTile, getDefinition().getTransferRate());
                    }
                });
    }

    private @Nullable ISourceTile asSourceTile(BlockEntity blockEntity) {
        return blockEntity instanceof ISourceTile sourceTile ? sourceTile : null;
    }

    private void moveSource(ISourceTile source, ISourceTile target, int maxAmount) {
        if (maxAmount <= 0 || !target.canAcceptSource()) return;
        int amount = Math.min(maxAmount, source.getSource());
        amount = Math.min(amount, target.getMaxSource() - target.getSource());
        if (amount <= 0) return;
        source.removeSource(amount);
        target.addSource(amount);
    }

    public class SourceRecipeHandler extends RecipeHandlerTrait<Integer> {
        protected SourceRecipeHandler() {
            super(ArsNouveauSourceCapabilityTrait.this, ArsNouveauSourceRecipeCapability.CAP);
        }

        @Override
        public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            int required = left.stream().reduce(0, Integer::sum);
            if (required <= 0) return null;

            var capability = simulate ? storage.copy() : storage;
            if (io == IO.IN) {
                var consumed = Math.min(required, capability.getSource());
                capability.removeSource(consumed);
                if (!simulate && consumed > 0) {
                    RecipeConsumptionTracker.record(ArsNouveauSourceRecipeCapability.CAP, consumed, slotName);
                }
                required -= consumed;
            } else if (io == IO.OUT) {
                var received = Math.min(required, capability.getMaxSource() - capability.getSource());
                capability.addSource(received);
                required -= received;
            }
            return required > 0 ? List.of(required) : null;
        }
    }

    public class MBDSourceProvider implements ISpecialSourceProvider {
        @Override
        public ISourceTile getSource() {
            return new SourceStorageWrapper(storage, getCapabilityIO(null));
        }

        @Override
        public boolean isValid() {
            return sourceProviderValid && !getMachine().isInValid() && getMachine().getLevel() instanceof ServerLevel;
        }

        @Override
        public BlockPos getCurrentPos() {
            return getMachine().getPos();
        }
    }
}
