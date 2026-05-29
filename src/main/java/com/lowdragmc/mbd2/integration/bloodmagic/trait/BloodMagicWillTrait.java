package com.lowdragmc.mbd2.integration.bloodmagic.trait;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTrait;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWill;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWillRecipeCapability;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.demonaura.WorldDemonWillHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class BloodMagicWillTrait extends RecipeCapabilityTrait implements IRecipeHandlerTrait<BloodMagicWill> {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(BloodMagicWillTrait.class);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public BloodMagicWillTrait(MBDMachine machine, BloodMagicWillTraitDefinition definition) {
        super(machine, definition);
    }

    @Override
    public BloodMagicWillTraitDefinition getDefinition() {
        return (BloodMagicWillTraitDefinition) super.getDefinition();
    }

    @Override
    public List<BloodMagicWill> handleRecipeInner(IO io, MBDRecipe recipe, List<BloodMagicWill> left, @Nullable String slotName, boolean simulate) {
        if (!compatibleWith(io)) return left;
        var world = getMachine().getLevel();
        var pos = getMachine().getPos();
        var definition = getDefinition();
        var leftByType = new LinkedHashMap<EnumDemonWillType, BloodMagicWill>();
        var remainingLeft = new ArrayList<BloodMagicWill>();
        for (var will : left) {
            if (!definition.acceptsType(will.type())) {
                remainingLeft.add(will);
                continue;
            }
            var current = leftByType.get(will.type());
            leftByType.put(will.type(), current == null ? will :
                    new BloodMagicWill(will.type(), current.amount() + will.amount(), Math.max(current.maxOutput(), will.maxOutput())));
        }
        if (leftByType.isEmpty()) {
            return left;
        }

        var samplePositions = definition.getChunkSamplePositions(pos);
        var changed = false;
        if (io == IO.IN) {
            var inputBudget = definition.getMaxInput() > 0 ? definition.getMaxInput() : Double.MAX_VALUE;
            for (var will : leftByType.values()) {
                var remaining = will.amount();
                for (var samplePos : samplePositions) {
                    if (remaining <= 0 || inputBudget <= 0) break;
                    if (!world.hasChunkAt(samplePos)) continue;
                    var requested = Math.min(remaining, inputBudget);
                    var drained = WorldDemonWillHandler.drainWill(world, samplePos, will.type(), requested, !simulate);
                    if (drained > 0) {
                        changed = true;
                        if (!simulate) {
                            RecipeConsumptionTracker.record(BloodMagicWillRecipeCapability.CAP,
                                    new BloodMagicWill(will.type(), drained, 0), slotName);
                        }
                        remaining -= drained;
                        inputBudget -= drained;
                    }
                }
                if (remaining > 0) {
                    remainingLeft.add(new BloodMagicWill(will.type(), remaining, will.maxOutput()));
                }
            }
        } else if (io == IO.OUT) {
            for (var will : leftByType.values()) {
                var remaining = will.amount();
                var maxOutput = resolveMaxOutput(will);
                for (var samplePos : samplePositions) {
                    if (remaining <= 0) break;
                    if (!world.hasChunkAt(samplePos)) continue;
                    var filled = WorldDemonWillHandler.fillWillToMaximum(world, samplePos, will.type(), remaining, maxOutput, !simulate);
                    if (filled > 0) {
                        changed = true;
                        remaining -= filled;
                    }
                }
                if (remaining > 0) {
                    remainingLeft.add(new BloodMagicWill(will.type(), remaining, will.maxOutput()));
                }
            }
        }
        if (!simulate && changed) {
            notifyListeners();
        }
        return remainingLeft.isEmpty() ? null : remainingLeft;
    }

    private double resolveMaxOutput(BloodMagicWill will) {
        var recipeMaxOutput = will.maxOutput();
        var traitMaxOutput = getDefinition().getMaxOutput();
        if (traitMaxOutput <= 0) return recipeMaxOutput;
        if (recipeMaxOutput <= 0) return traitMaxOutput;
        return Math.min(traitMaxOutput, recipeMaxOutput);
    }

    @Override
    public RecipeCapability<BloodMagicWill> getRecipeCapability() {
        return BloodMagicWillRecipeCapability.CAP;
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(this);
    }
}
