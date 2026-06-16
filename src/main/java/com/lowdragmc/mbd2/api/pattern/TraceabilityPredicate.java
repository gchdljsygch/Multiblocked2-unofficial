package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.block.ProxyPartBlock;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Composite predicate used by {@link BlockPattern} to match one symbolic
 * position in a multiblock pattern.
 *
 * <p>The business goal is to combine one or more simple block predicates with
 * optional global/layer limits, preview candidates, IO metadata, NBT checks, and
 * controller markers. Instances are mutable builder objects and are not
 * thread-safe while being configured. Once a pattern is built, predicate testing
 * should remain read-only except for updates to the supplied
 * {@link MultiblockState}'s match context and counters.</p>
 */
public class TraceabilityPredicate {

    public List<SimplePredicate> common = new ArrayList<>();
    public List<SimplePredicate> limited = new ArrayList<>();
    public boolean isController;

    /**
     * Creates an empty predicate that matches nothing until simple predicates are
     * added.
     */
    public TraceabilityPredicate() {
    }

    /**
     * Copies the simple predicate lists and controller flag from another
     * composite predicate.
     *
     * @param predicate source predicate
     */
    public TraceabilityPredicate(TraceabilityPredicate predicate) {
        common.addAll(predicate.common);
        limited.addAll(predicate.limited);
        isController = predicate.isController;
    }

    /**
     * Creates a common predicate from a raw state test and preview candidates.
     *
     * @param predicate  world-state predicate; should not mutate world state
     * @param candidates optional preview/auto-build candidates
     */
    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo[]> candidates) {
        common.add(new SimplePredicate(predicate, candidates));
    }

    /**
     * Wraps a simple predicate as either limited or common based on its count
     * constraints.
     *
     * @param simplePredicate simple predicate to include
     */
    public TraceabilityPredicate(SimplePredicate simplePredicate) {
        if (simplePredicate.minCount != -1 || simplePredicate.maxCount != -1) {
            limited.add(simplePredicate);
        } else {
            common.add(simplePredicate);
        }
    }

    /**
     * Marks this pattern position as the multiblock controller.
     *
     * <p>Side effects: sets the controller flag used by
     * {@link FactoryBlockPattern#build()} to compute the center offset. Most
     * callers should use the higher-level predicate factory for controllers.</p>
     *
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setController() {
        isController = true;
        return this;
    }

    /**
     * Sorts limited predicates by minimum global count.
     *
     * @return this predicate for chaining
     */
    public TraceabilityPredicate sort() {
        limited.sort(Comparator.comparingInt(a -> a.minCount));
        return this;
    }

    /**
     * Add tooltips for candidates. They are shown in JEI Pages.
     *
     * <p>Side effects: appends the supplied components to all simple predicates
     * that provide preview candidates.</p>
     *
     * @param tips tooltip lines to show for matching candidates
     * @return this predicate for chaining
     */
    public TraceabilityPredicate addTooltips(Component... tips) {
        if (tips.length > 0) {
            List<Component> tooltips = Arrays.stream(tips).toList();
            common.forEach(predicate -> {
                if (predicate.candidates == null) return;
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                predicate.toolTips.addAll(tooltips);
            });
        }
        return this;
    }

    /**
     * Set the minimum number of candidate blocks.
     *
     * <p>Side effects: moves all common predicates into the limited set and sets
     * their minimum global count.</p>
     *
     * @param min minimum required matches across the whole pattern; {@code -1}
     *            disables the lower bound
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMinGlobalLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minCount = min;
        }
        return this;
    }

    /**
     * Sets the minimum global count and preview display count together.
     *
     * @param min          minimum required matches across the whole pattern
     * @param previewCount number of candidate blocks to show in previews;
     *                     {@code -1} means unlimited/default
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMinGlobalLimited(int min, int previewCount) {
        return this.setMinGlobalLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks.
     *
     * @param max maximum allowed matches across the whole pattern; {@code -1}
     *            disables the upper bound
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMaxGlobalLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxCount = max;
        }
        return this;
    }

    /**
     * Sets the maximum global count and preview display count together.
     *
     * @param max          maximum allowed matches across the whole pattern
     * @param previewCount number of candidate blocks to show in previews;
     *                     {@code -1} means unlimited/default
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMaxGlobalLimited(int max, int previewCount) {
        return this.setMaxGlobalLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Set the minimum number of candidate blocks for each aisle layer.
     *
     * @param min minimum required matches per layer; {@code -1} disables the
     *            lower bound
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMinLayerLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minLayerCount = min;
        }
        return this;
    }

    /**
     * Sets the minimum layer count and preview display count together.
     *
     * @param min          minimum required matches per layer
     * @param previewCount number of candidate blocks to show in previews;
     *                     {@code -1} means unlimited/default
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMinLayerLimited(int min, int previewCount) {
        return this.setMinLayerLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks for each aisle layer.
     *
     * @param max maximum allowed matches per layer; {@code -1} disables the
     *            upper bound
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMaxLayerLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxLayerCount = max;
        }
        return this;
    }

    /**
     * Sets the maximum layer count and preview display count together.
     *
     * @param max          maximum allowed matches per layer
     * @param previewCount number of candidate blocks to show in previews;
     *                     {@code -1} means unlimited/default
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setMaxLayerLimited(int max, int previewCount) {
        return this.setMaxLayerLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Sets the Minimum and Maximum limit to the passed value
     *
     * @param limit exact number of global matches required
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setExactLimit(int limit) {
        return this.setMinGlobalLimited(limit).setMaxGlobalLimited(limit);
    }

    /**
     * Set the number of it appears in JEI pages. It only affects JEI preview. (The specific number)
     *
     * @param count preview candidate count; {@code -1} means default/unlimited
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setPreviewCount(int count) {
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    /**
     * Set renderMask.
     *
     * <p>Side effects: marks matched positions so formed-structure rendering can
     * hide them.</p>
     *
     * @return this predicate for chaining
     */
    public TraceabilityPredicate disableRenderFormed() {
        common.forEach(predicate -> predicate.disableRenderFormed = true);
        limited.forEach(predicate -> predicate.disableRenderFormed = true);
        return this;
    }

    /**
     * Set io.
     *
     * <p>Business goal: label matched positions as input, output, or both for
     * recipe-handler discovery.</p>
     *
     * @param io IO side stored in the match context when this predicate matches
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setIO(IO io) {
        common.forEach(predicate -> predicate.io = io);
        limited.forEach(predicate -> predicate.io = io);
        return this;
    }

    /**
     * Requires matched block entities to contain the supplied NBT.
     *
     * @param nbt partial NBT that must merge without changing the block entity's
     *            full metadata
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setNBT(CompoundTag nbt) {
        common.forEach(predicate -> predicate.nbt = nbt);
        limited.forEach(predicate -> predicate.nbt = nbt);
        return this;
    }

    /**
     * Assigns a recipe slot name to matched positions.
     *
     * @param slotName slot name stored in the match context for recipe capability
     *                 binding
     * @return this predicate for chaining
     */
    public TraceabilityPredicate setSlotName(String slotName) {
        common.forEach(predicate -> predicate.slotName = slotName);
        limited.forEach(predicate -> predicate.slotName = slotName);
        return this;
    }

    /**
     * Tests this composite predicate against the current multiblock state.
     *
     * <p>Side effects: resets {@link MultiblockState#io} to {@link IO#BOTH},
     * updates global/layer counters for limited predicates, may write
     * diagnostics to the state error field, and writes slot/render/open-UI masks
     * into the match context through matching simple predicates.</p>
     *
     * @param blockWorldState current pattern position state
     * @return {@code true} when any common predicate matches or any limited
     * predicate passes its limits and inner conditions
     */
    public boolean test(MultiblockState blockWorldState) {
        blockWorldState.io = IO.BOTH;
        boolean flag = false;
        for (SimplePredicate predicate : limited) {
            if (predicate.testLimited(blockWorldState)) {
                flag = true;
            }
        }
        flag = flag || common.stream().anyMatch(predicate -> predicate.test(blockWorldState));
        if (flag) {
            blockWorldState.setError(null);
        }
        return flag;
    }

    /**
     * Returns a predicate that matches either this predicate or another one.
     *
     * <p>Side effects: none when {@code other} is null; otherwise returns a new
     * composite containing both simple predicate lists.</p>
     *
     * @param other additional predicate to accept
     * @return combined predicate, or this predicate when {@code other} is null
     */
    public TraceabilityPredicate or(TraceabilityPredicate other) {
        if (other != null) {
            TraceabilityPredicate newPredicate = new TraceabilityPredicate(this);
            newPredicate.common.addAll(other.common);
            newPredicate.limited.addAll(other.limited);
            return newPredicate;
        }
        return this;
    }

    /**
     * Returns whether this predicate is the wildcard predicate.
     *
     * @return {@code true} only when this wraps {@link SimplePredicate#ANY}
     */
    public boolean isAny() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.ANY;
    }

    /**
     * Returns whether matched positions should be cached.
     *
     * @return {@code false} for wildcard positions, {@code true} otherwise
     */
    public boolean addCache() {
        return !isAny();
    }

    /**
     * Returns whether this predicate requires air.
     *
     * @return {@code true} only when this wraps {@link SimplePredicate#AIR}
     */
    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.AIR;
    }

    /**
     * Returns whether this predicate contains exactly one non-air, non-any simple
     * predicate.
     *
     * @return {@code true} for one concrete simple predicate
     */
    public boolean isSingle() {
        return !isAny() && !isAir() && this.common.size() + this.limited.size() == 1;
    }

    /**
     * Returns whether air is one accepted option.
     *
     * @return {@code true} when the common predicate list contains
     * {@link SimplePredicate#AIR}
     */
    public boolean hasAir() {
        return this.common.contains(SimplePredicate.AIR);
    }

}
