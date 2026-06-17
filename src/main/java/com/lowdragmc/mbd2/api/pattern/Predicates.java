package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.pattern.predicates.PredicateBlocks;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateFluids;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateStates;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Factory methods for common multiblock pattern predicates.
 *
 * <p>The business goal is to keep machine pattern definitions readable while
 * still producing {@link TraceabilityPredicate} instances that carry preview,
 * auto-build, and matching metadata. The returned predicates are mutable
 * builder-style objects; callers usually configure limits, IO, NBT, or
 * tooltips before passing them to {@link FactoryBlockPattern#where(char,
 * TraceabilityPredicate)}. These helpers do not mutate world state.</p>
 */
public class Predicates {

    /**
     * Marks an existing predicate as the controller position.
     *
     * <p>Side effects: mutates the supplied predicate by setting its controller
     * marker. Exactly one controller marker should be present in a normal
     * pattern so the compiled pattern can derive its anchor offset.</p>
     *
     * @param predicate predicate representing the controller block
     * @return the same predicate instance for chaining
     */
    public static TraceabilityPredicate controller(TraceabilityPredicate predicate) {
        return predicate.setController();
    }

    /**
     * Creates a predicate that matches exact block states.
     *
     * <p>Block states are also used as preview and auto-build candidates. When
     * the predicate is not locked to a controller front, {@link PredicateStates}
     * rotates expected horizontal state properties to the current pattern
     * facing during matching.</p>
     *
     * @param allowedStates accepted states; null entries are discarded by the
     *                      concrete predicate
     * @return mutable traceability predicate wrapping the state matcher
     */
    public static TraceabilityPredicate states(BlockState... allowedStates) {
        var candidates = new ArrayList<BlockState>();
        for (BlockState state : allowedStates) {
            candidates.add(state);
            // TODO vaBlocks
//            if (state.getBlock() instanceof ActiveBlock block) {
//                candidates.add(block.changeActive(state, !block.isActive(state)));
//            }
        }
        return new TraceabilityPredicate(new PredicateStates(candidates.toArray(BlockState[]::new)));
    }

    /**
     * Creates a predicate that matches any of the supplied block types.
     *
     * @param blocks accepted blocks; null entries are discarded by the concrete
     *               predicate
     * @return mutable traceability predicate wrapping the block matcher
     */
    public static TraceabilityPredicate blocks(Block... blocks) {
        return new TraceabilityPredicate(new PredicateBlocks(blocks));
    }

    /**
     * Creates a predicate that matches source or flowing states of the supplied
     * fluids.
     *
     * @param fluids accepted fluids; null entries are discarded by the concrete
     *               predicate
     * @return mutable traceability predicate wrapping the fluid matcher
     */
    public static TraceabilityPredicate fluids(Fluid... fluids) {
        return new TraceabilityPredicate(new PredicateFluids(fluids));
    }

    /**
     * Creates a predicate from a caller-supplied state test.
     *
     * <p>Preconditions: the predicate should be safe to run during pattern
     * validation and should not mutate the level. Candidate suppliers may be
     * invoked by previews, tooltips, and auto-build planning.</p>
     *
     * @param predicate  test for the active multiblock state cursor
     * @param candidates optional preview/auto-build candidates
     * @return mutable traceability predicate wrapping the custom matcher
     */
    public static TraceabilityPredicate custom(Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        return new TraceabilityPredicate(predicate, candidates);
    }

    /**
     * Creates a wildcard predicate.
     *
     * <p>Wildcard positions are not added to the formed-structure position
     * cache and therefore do not participate in block-change invalidation.</p>
     *
     * @return predicate that accepts any loaded position
     */
    public static TraceabilityPredicate any() {
        return new TraceabilityPredicate(SimplePredicate.ANY);
    }

    /**
     * Creates a predicate that requires an empty block position.
     *
     * @return predicate that accepts empty world positions
     */
    public static TraceabilityPredicate air() {
        return new TraceabilityPredicate(SimplePredicate.AIR);
    }

}
