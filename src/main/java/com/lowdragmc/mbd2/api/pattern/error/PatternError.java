package com.lowdragmc.mbd2.api.pattern.error;

import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Base diagnostic object for a failed multiblock pattern check.
 *
 * <p>The business goal is to retain enough context for UI and tooltip code to
 * explain why a structure position failed. Instances are attached to a
 * {@link MultiblockState} by {@link MultiblockState#setError(PatternError)} and
 * then read later by client/server diagnostics. They are not thread-safe and
 * should be treated as transient state owned by one pattern check.</p>
 */
public class PatternError {

    protected MultiblockState worldState;

    /**
     * Attaches the state that produced this error.
     *
     * <p>Preconditions: callers should set this before invoking any accessor on
     * the error. Side effects: replaces the previous state reference.</p>
     *
     * @param worldState failed pattern state
     */
    public void setWorldState(MultiblockState worldState) {
        this.worldState = worldState;
    }

    /**
     * Returns the level where the failure occurred.
     *
     * @return level from the attached multiblock state
     */
    public Level getWorld() {
        return worldState.getWorld();
    }

    /**
     * Returns the cursor position that failed.
     *
     * @return failed world position
     */
    public BlockPos getPos() {
        return worldState.getPos();
    }

    /**
     * Returns candidate item groups for all simple predicates at the failed
     * position.
     *
     * <p>Side effects: may invoke candidate suppliers on the failed predicate.
     * The outer list separates alternative simple predicates; each inner list
     * contains display stacks for that alternative.</p>
     *
     * @return candidate stacks for diagnostics; groups may be empty
     */
    public List<List<ItemStack>> getCandidates() {
        TraceabilityPredicate predicate = worldState.predicate;
        List<List<ItemStack>> candidates = new ArrayList<>();
        for (SimplePredicate common : predicate.common) {
            candidates.add(common.getCandidates());
        }
        for (SimplePredicate limited : predicate.limited) {
            candidates.add(limited.getCandidates());
        }
        return candidates;
    }

    /**
     * Builds the localized diagnostic message for UI display.
     *
     * @return translatable component describing expected candidates and position
     */
    public Component getErrorInfo() {
        List<List<ItemStack>> candidates = getCandidates();
        var items = Component.empty();
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) {
                items.append(candidate.get(0).getDisplayName());
                items.append(Component.literal(", "));
            }
        }
        return Component.translatable("mbd2.multiblock.pattern.error", items, worldState.getPos());
    }
}
