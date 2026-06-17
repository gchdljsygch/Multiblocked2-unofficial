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

/**
 * Machine trait that stores Ars Nouveau Source and exposes it to recipes, auto IO, and relays.
 *
 * <p>The backing {@link CopiableSourceStorage} is persisted and description-synced. Server load
 * registers a special Source provider with Ars Nouveau's {@link SourceManager}; unload and removal
 * invalidate that provider so relays stop targeting stale machines.</p>
 */
public class ArsNouveauSourceCapabilityTrait extends SimpleCapabilityTrait implements IAutoIOTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ArsNouveauSourceCapabilityTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

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

    /**
     * Creates the mutable Source store for this trait.
     *
     * @return storage bounded by the definition capacity and transfer rate
     */
    protected CopiableSourceStorage createStorage() {
        return new CopiableSourceStorage(getDefinition().getCapacity(), getDefinition().getTransferRate());
    }

    /**
     * Creates an IO-filtered relay endpoint for Ars Nouveau relay integration.
     *
     * @return Source wrapper respecting this trait's side-independent IO setting
     */
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

    /**
     * Moves Source to or from the adjacent Ars Nouveau source tile selected by auto IO.
     *
     * @param port machine port position
     * @param side port side used to find the adjacent block entity
     * @param io configured IO direction for this transfer pass
     */
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

    /**
     * Converts a block entity into a Source tile when Ars Nouveau exposes one directly.
     */
    private @Nullable ISourceTile asSourceTile(BlockEntity blockEntity) {
        return blockEntity instanceof ISourceTile sourceTile ? sourceTile : null;
    }

    /**
     * Transfers Source between two endpoints without exceeding either endpoint's capacity.
     *
     * @param source endpoint to drain
     * @param target endpoint to fill
     * @param maxAmount upper bound for the move
     */
    private void moveSource(ISourceTile source, ISourceTile target, int maxAmount) {
        if (maxAmount <= 0 || !target.canAcceptSource()) return;
        int amount = Math.min(maxAmount, source.getSource());
        amount = Math.min(amount, target.getMaxSource() - target.getSource());
        if (amount <= 0) return;
        source.removeSource(amount);
        target.addSource(amount);
    }

    /**
     * Recipe handler that consumes Source from storage for inputs and fills storage for outputs.
     */
    public class SourceRecipeHandler extends RecipeHandlerTrait<Integer> {
        protected SourceRecipeHandler() {
            super(ArsNouveauSourceCapabilityTrait.this, ArsNouveauSourceRecipeCapability.CAP);
        }

        /**
         * Handles integer Source requirements for one recipe pass.
         *
         * @param io recipe IO role
         * @param recipe recipe being processed
         * @param left remaining Source amounts to satisfy
         * @param slotName optional recipe slot name for consumption tracking
         * @param simulate whether to test without mutating storage
         * @return unsatisfied amounts, or {@code null} when fully handled
         */
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

    /**
     * Ars Nouveau special source provider registered while the machine is loaded on the server.
     */
    public class MBDSourceProvider implements ISpecialSourceProvider {
        /**
         * Provides a current IO-filtered Source endpoint to Ars Nouveau.
         */
        @Override
        public ISourceTile getSource() {
            return new SourceStorageWrapper(storage, getCapabilityIO(null));
        }

        /**
         * Checks whether the provider still points at a loaded, valid server-side machine.
         */
        @Override
        public boolean isValid() {
            return sourceProviderValid && !getMachine().isInValid() && getMachine().getLevel() instanceof ServerLevel;
        }

        /**
         * Reports the machine position used by Ars Nouveau relay/source scanning.
         */
        @Override
        public BlockPos getCurrentPos() {
            return getMachine().getPos();
        }
    }
}
