package com.lowdragmc.mbd2.common.trait.redstone;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignal;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignalRecipeCapability;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.RecipeHandlerTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    public RedstoneSignalCapabilityTrait(MBDMachine machine, RedstoneSignalCapabilityTraitDefinition definition) {
        super(machine, definition);
    }

    @Override
    public RedstoneSignalCapabilityTraitDefinition getDefinition() {
        return (RedstoneSignalCapabilityTraitDefinition) super.getDefinition();
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(recipeHandler);
    }

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

    @Override
    public void onMachineRemoved() {
        for (Direction side : Direction.values()) {
            clearSignal(side);
        }
    }

    @Override
    public void onNeighborChanged(Block block, net.minecraft.core.BlockPos fromPos, boolean isMoving) {
        updateInputSignal();
    }

    public boolean canConnectRedstone(Direction side) {
        return getCapabilityIO(side) != IO.NONE;
    }

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
        return signal;
    }

    public int getStrongestOutputSignal() {
        int signal = 0;
        for (byte value : emittedSignal) {
            signal = Math.max(signal, Byte.toUnsignedInt(value));
        }
        return signal;
    }

    public int getMaxRemainingTicks() {
        int ticks = 0;
        for (int value : remainingTicks) {
            ticks = Math.max(ticks, value);
        }
        return ticks;
    }

    private void emitSignal(RedstoneSignal signal) {
        if (signal.strength() <= 0 || signal.duration() <= 0) return;
        boolean changed = false;
        for (Direction side : Direction.values()) {
            if (!getCapabilityIO(side).support(IO.OUT)) continue;
            int index = side.ordinal();
            int current = Byte.toUnsignedInt(emittedSignal[index]);
            if (signal.strength() >= current || remainingTicks[index] <= 0) {
                emittedSignal[index] = (byte) signal.strength();
                getMachine().setOutputSignal(signal.strength(), side);
                changed = true;
            }
            if (remainingTicks[index] < signal.duration()) {
                remainingTicks[index] = signal.duration();
                changed = true;
            }
        }
        if (changed) {
            signalChanged();
        }
    }

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
    }

    public class RedstoneSignalRecipeHandler extends RecipeHandlerTrait<RedstoneSignal> {
        protected RedstoneSignalRecipeHandler() {
            super(RedstoneSignalCapabilityTrait.this, RedstoneSignalRecipeCapability.CAP);
        }

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
