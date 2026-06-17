package com.lowdragmc.mbd2.common.trait.redstone;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignal;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignalRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.MBDPartMachine;
import com.lowdragmc.mbd2.common.trait.RecipeHandlerTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Recipe capability trait that converts recipes to and from redstone signal state.
 *
 * <p>Input recipes read the strongest configured input side on the controller and proxied multiblock parts. Output
 * recipes emit a temporary signal on every configured output side. Runtime state is persisted/synced so UI widgets
 * and block updates can reflect remaining pulse duration after reload.</p>
 *
 * <p>All methods are intended for the logical server thread except simple read accessors used by synchronized UI
 * state.</p>
 */
public class RedstoneSignalCapabilityTrait extends SimpleCapabilityTrait {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RedstoneSignalCapabilityTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Persisted
    @DescSynced
    private final byte[] emittedSignal = new byte[6];
    @Persisted
    @DescSynced
    private final int[] remainingTicks = new int[6];
    @DescSynced
    private int inputSignal;
    private final RedstoneSignalRecipeHandler recipeHandler = new RedstoneSignalRecipeHandler();

    /**
     * Creates the runtime redstone signal trait for one machine.
     *
     * @param machine    owning machine
     * @param definition definition that supplies IO side configuration
     */
    public RedstoneSignalCapabilityTrait(MBDMachine machine, RedstoneSignalCapabilityTraitDefinition definition) {
        super(machine, definition);
    }

    /**
     * Returns the concrete definition for side configuration and UI integration.
     *
     * @return this trait's {@link RedstoneSignalCapabilityTraitDefinition}
     */
    @Override
    public RedstoneSignalCapabilityTraitDefinition getDefinition() {
        return (RedstoneSignalCapabilityTraitDefinition) super.getDefinition();
    }

    /**
     * Exposes the single recipe handler that checks input ranges and emits output pulses.
     *
     * @return immutable singleton list owned by this trait
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

    /**
     * Updates redstone input and decrements active output pulse timers.
     *
     * <p>When a timer reaches zero, the corresponding controller output is cleared if it still carries this trait's
     * emitted signal. Any changed input or output state notifies listeners, marks the machine changed, and refreshes
     * proxied part signals.</p>
     */
    @Override
    public void serverTick() {
        updateInputSignal();
        boolean timerChanged = false;
        for (Direction side : Direction.values()) {
            int index = side.ordinal();
            if (remainingTicks[index] > 0) {
                remainingTicks[index]--;
                timerChanged = true;
                if (remainingTicks[index] == 0) {
                    clearSignal(side);
                }
            }
        }
        if (timerChanged) {
            onChanged();
        }
    }

    /**
     * Clears every side when the owning machine is removed.
     *
     * <p>This prevents stale redstone power from lingering in the machine block or proxied multiblock parts.</p>
     */
    @Override
    public void onMachineRemoved() {
        for (Direction side : Direction.values()) {
            clearSignal(side);
        }
    }

    /**
     * Re-reads input power after any neighboring block update.
     *
     * @param block    block type that caused the neighbor notification
     * @param fromPos  source position of the update
     * @param isMoving whether the neighbor change is part of block movement
     */
    @Override
    public void onNeighborChanged(Block block, net.minecraft.core.BlockPos fromPos, boolean isMoving) {
        updateInputSignal();
    }

    /**
     * Reports whether a side should connect to vanilla redstone.
     *
     * @param side controller side to test
     * @return {@code true} when that side is configured for input or output
     */
    public boolean canConnectRedstone(Direction side) {
        return getCapabilityIO(side) != IO.NONE;
    }

    /**
     * Returns the strongest currently observed input signal.
     *
     * @return redstone power in the vanilla range {@code 0..15}
     */
    public int getInputSignal() {
        return inputSignal;
    }

    private void updateInputSignal() {
        int signal = readInputSignal();
        if (signal != inputSignal) {
            inputSignal = signal;
            signalChanged();
        }
    }

    private int readInputSignal() {
        Level level = getMachine().getLevel();
        if (level == null) return 0;
        int signal = 0;
        var pos = getMachine().getPos();
        for (Direction side : Direction.values()) {
            if (getCapabilityIO(side).support(IO.IN)) {
                signal = Math.max(signal, level.getSignal(pos.relative(side), side));
            }
        }
        if (getMachine() instanceof MBDMultiblockMachine multiblockMachine && multiblockMachine.isFormed()) {
            for (IMultiPart part : multiblockMachine.getParts()) {
                if (part instanceof MBDPartMachine partMachine && partMachine.isProxyingControllerRedstone(this)) {
                    var partPos = partMachine.getPos();
                    for (Direction side : Direction.values()) {
                        if (partMachine.getControllerRedstoneProxyIO(this, side).support(IO.IN)) {
                            signal = Math.max(signal, level.getSignal(partPos.relative(side), side));
                        }
                    }
                }
            }
        }
        return signal;
    }

    /**
     * Returns the strongest active output signal across all sides.
     *
     * @return redstone power in the vanilla range {@code 0..15}, or {@code 0} when no pulse is active
     */
    public int getStrongestOutputSignal() {
        int signal = 0;
        for (byte value : emittedSignal) {
            signal = Math.max(signal, Byte.toUnsignedInt(value));
        }
        return signal;
    }

    /**
     * Returns the active output signal for one side.
     *
     * @param side controller side to inspect
     * @return redstone power in the vanilla range {@code 0..15}
     */
    public int getOutputSignal(Direction side) {
        return Byte.toUnsignedInt(emittedSignal[side.ordinal()]);
    }

    /**
     * Returns the longest remaining output pulse duration.
     *
     * @return remaining ticks, or {@code 0} if no pulse is active
     */
    public int getMaxRemainingTicks() {
        int ticks = 0;
        for (int value : remainingTicks) {
            ticks = Math.max(ticks, value);
        }
        return ticks;
    }

    /**
     * Applies an output pulse to all controller and proxied output sides.
     *
     * <p>Signals with non-positive strength or duration are ignored. A stronger or expired side is overwritten, and
     * the side timer is extended to the requested duration when longer than the current timer.</p>
     *
     * @param signal output signal; strength is expected to be in {@code 1..15}, duration in ticks
     */
    private void emitSignal(RedstoneSignal signal) {
        if (signal.strength() <= 0 || signal.duration() <= 0) return;
        boolean changed = false;
        for (Direction side : Direction.values()) {
            var outputOnController = getCapabilityIO(side).support(IO.OUT);
            if (outputOnController || isProxiedOutputSide(side)) {
                changed |= emitSignalToSide(signal, side, outputOnController);
            }
        }
        if (changed) {
            signalChanged();
        }
    }

    private boolean isProxiedOutputSide(Direction side) {
        if (getMachine() instanceof MBDMultiblockMachine multiblockMachine && multiblockMachine.isFormed()) {
            for (IMultiPart part : multiblockMachine.getParts()) {
                if (part instanceof MBDPartMachine partMachine &&
                        partMachine.getControllerRedstoneProxyIO(this, side).support(IO.OUT)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean emitSignalToSide(RedstoneSignal signal, Direction side, boolean outputOnController) {
        boolean changed = false;
        int index = side.ordinal();
        int current = Byte.toUnsignedInt(emittedSignal[index]);
        if (signal.strength() >= current || remainingTicks[index] <= 0) {
            emittedSignal[index] = (byte) signal.strength();
            if (outputOnController) {
                getMachine().setOutputSignal(signal.strength(), side);
            }
            changed = true;
        }
        if (remainingTicks[index] < signal.duration()) {
            remainingTicks[index] = signal.duration();
            changed = true;
        }
        return changed;
    }

    /**
     * Clears one side's emitted signal and timer.
     *
     * <p>The controller block output is reset only if it still equals the signal written by this trait, avoiding an
     * accidental clear of a stronger output written elsewhere.</p>
     *
     * @param side controller side to clear
     */
    private void clearSignal(Direction side) {
        int index = side.ordinal();
        int signal = Byte.toUnsignedInt(emittedSignal[index]);
        if (signal <= 0) return;
        if (getMachine().getOutputSignal(side) == signal) {
            getMachine().setOutputSignal(0, side);
        }
        emittedSignal[index] = 0;
        remainingTicks[index] = 0;
        signalChanged();
    }

    private void signalChanged() {
        notifyListeners();
        onChanged();
        updateProxiedPartsSignal();
    }

    private void updateProxiedPartsSignal() {
        if (getMachine() instanceof MBDMultiblockMachine multiblockMachine && multiblockMachine.isFormed()) {
            for (IMultiPart part : multiblockMachine.getParts()) {
                if (part instanceof MBDPartMachine partMachine && partMachine.isProxyingControllerRedstone(this)) {
                    partMachine.updateSignal();
                }
            }
        }
    }

    /**
     * Recipe handler for redstone signal input/output contents.
     */
    public class RedstoneSignalRecipeHandler extends RecipeHandlerTrait<RedstoneSignal> {
        protected RedstoneSignalRecipeHandler() {
            super(RedstoneSignalCapabilityTrait.this, RedstoneSignalRecipeCapability.CAP);
        }

        /**
         * Handles a redstone recipe requirement or output.
         *
         * <p>Input matching compares every requested signal predicate against the current strongest input signal and
         * returns any unmatched predicates. Output matching collapses all requested outputs to the maximum strength
         * and duration, then emits that pulse only on committed execution. Simulation never mutates block power,
         * timers, or synced state.</p>
         *
         * @param io       requested recipe direction
         * @param recipe   recipe currently being checked or executed
         * @param left     requested redstone signal contents
         * @param slotName unused logical slot name
         * @param simulate {@code true} to test without emitting output
         * @return {@code null} when handled, or the unmatched list when input predicates fail or IO is incompatible
         */
        @Override
        public List<RedstoneSignal> handleRecipeInner(IO io, MBDRecipe recipe, List<RedstoneSignal> left, @Nullable String slotName, boolean simulate) {
            if (!compatibleWith(io)) return left;
            if (io == IO.IN) {
                int current = getInputSignal();
                List<RedstoneSignal> unmatched = left.stream().filter(signal -> !signal.matchesInput(current)).toList();
                return unmatched.isEmpty() ? null : unmatched;
            }
            int strength = left.stream().mapToInt(RedstoneSignal::strength).max().orElse(0);
            int duration = left.stream().mapToInt(RedstoneSignal::duration).max().orElse(0);
            if (!simulate) {
                emitSignal(RedstoneSignal.output(strength, duration));
            }
            return null;
        }
    }
}
