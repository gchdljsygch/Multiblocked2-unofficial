package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Block pattern that tries multiple compiled patterns as alternatives.
 *
 * <p>The business goal is to let one controller support several valid structure
 * layouts while preserving the {@link BlockPattern} API expected by controllers,
 * preview widgets, and auto-build tools. Matching methods prefer the previously
 * matched pattern first, then try the remaining patterns in array order.</p>
 */
public class CombinedBlockPattern extends BlockPattern {
    private static final RelativeDirection[] DEFAULT_STRUCTURE_DIR = new RelativeDirection[]{
            RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT
    };

    private final BlockPattern[] patterns;

    /**
     * Creates a combined pattern from non-null alternatives.
     *
     * @param patterns candidate patterns; {@code null} entries are ignored
     */
    public CombinedBlockPattern(BlockPattern... patterns) {
        super(new TraceabilityPredicate[0][0][0], DEFAULT_STRUCTURE_DIR, new int[0][2], new int[5]);
        this.patterns = Arrays.stream(patterns)
                .filter(Objects::nonNull)
                .toArray(BlockPattern[]::new);
    }

    /**
     * Returns a defensive copy of the candidate pattern array.
     *
     * @return non-null pattern alternatives
     */
    public BlockPattern[] getPatterns() {
        return patterns.clone();
    }

    /**
     * Estimates the largest candidate pattern size.
     *
     * @return maximum estimated block count across alternatives, or {@code 0}
     * when no patterns exist
     */
    @Override
    public int getEstimatedBlockCount() {
        return Arrays.stream(patterns)
                .mapToInt(BlockPattern::getEstimatedBlockCount)
                .max()
                .orElse(0);
    }

    /**
     * Checks all alternatives at the state's controller position without
     * requiring a controller capability.
     *
     * @param worldState mutable match state
     * @param facing facing to test
     * @return {@code true} when any alternative matches
     */
    @Override
    public boolean checkPatternAtWithoutController(MultiblockState worldState, Direction facing) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAtWithoutController(worldState, facing));
    }

    /**
     * Checks all alternatives around the controller in the world state.
     *
     * @param worldState mutable match state
     * @param savePredicate {@code true} to cache matched predicates
     * @return {@code true} when any alternative matches
     */
    @Override
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, savePredicate));
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate,
                                  BiPredicate<MultiblockState, TraceabilityPredicate> predicateMatcher) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, savePredicate, predicateMatcher));
    }

    /**
     * Checks all alternatives at an explicit center and facing.
     *
     * @param worldState mutable match state
     * @param centerPos controller/anchor position
     * @param facing facing to test
     * @param savePredicate {@code true} to cache matched predicates
     * @return {@code true} when any alternative matches
     */
    @Override
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction facing, boolean savePredicate) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, centerPos, facing, savePredicate));
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction facing, boolean savePredicate,
                                  BiPredicate<MultiblockState, TraceabilityPredicate> predicateMatcher) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, centerPos, facing, savePredicate, predicateMatcher));
    }

    /**
     * Auto-builds the best-known matching pattern.
     *
     * @param player player requesting auto-build
     * @param worldState current match state
     */
    @Override
    public void autoBuild(Player player, MultiblockState worldState) {
        autoBuild(player, worldState, -1);
    }

    /**
     * Auto-builds a selected or inferred alternative pattern.
     *
     * <p>Selection order: explicit valid index, current state's matched pattern,
     * controller state's matched pattern, then index {@code 0}. Side effects:
     * sets the matched pattern on {@code worldState} before delegating to the
     * selected pattern's auto-build logic.</p>
     *
     * @param player player requesting auto-build
     * @param worldState current match state
     * @param selectedPatternIndex requested alternative index; negative means
     * infer from match state
     */
    @Override
    public void autoBuild(Player player, MultiblockState worldState, int selectedPatternIndex) {
        int patternIndex = normalizeSelectedPatternIndex(selectedPatternIndex);
        if (patternIndex < 0) {
            patternIndex = findPatternIndex(worldState.getMatchedPattern());
        }
        if (patternIndex < 0) {
            IMultiController controller = worldState.getController();
            if (controller != null) {
                patternIndex = findPatternIndex(controller.getMultiblockState().getMatchedPattern());
            }
        }
        if (patternIndex < 0 && patterns.length > 0) {
            patternIndex = 0;
        }
        if (patternIndex >= 0) {
            BlockPattern pattern = patterns[patternIndex];
            worldState.setMatchedPattern(pattern, patternIndex);
            pattern.autoBuild(player, worldState);
        }
    }

    /**
     * Returns a preview for the first alternative pattern.
     *
     * @param repetition repetition counts passed to the selected preview
     * @return preview grid, or an empty grid when no alternatives exist
     */
    @Override
    public com.lowdragmc.lowdraglib.utils.BlockInfo[][][] getPreview(int[] repetition) {
        return patterns.length == 0 ? new com.lowdragmc.lowdraglib.utils.BlockInfo[0][0][0] : patterns[0].getPreview(repetition);
    }

    /**
     * Checks candidate patterns and stores the matched pattern/index on success.
     *
     * @param worldState mutable match state
     * @param checker check function bound to the desired matching mode
     * @return {@code true} when a candidate matches
     */
    private boolean checkPatterns(MultiblockState worldState, Predicate<BlockPattern> checker) {
        boolean commitSuccessfulMatches = worldState.shouldCommitSuccessfulMatches();
        worldState.setCommitSuccessfulMatches(false);
        try {
            int matchedIndex = findPatternIndex(worldState.getMatchedPattern());
            if (matchedIndex >= 0 && checker.test(patterns[matchedIndex])) {
                worldState.setMatchedPattern(patterns[matchedIndex], matchedIndex);
                if (commitSuccessfulMatches) {
                    worldState.commitCache();
                }
                return true;
            }
            for (int i = 0; i < patterns.length; i++) {
                if (i == matchedIndex) continue;
                BlockPattern pattern = patterns[i];
                if (checker.test(pattern)) {
                    worldState.setMatchedPattern(pattern, i);
                    if (commitSuccessfulMatches) {
                        worldState.commitCache();
                    }
                    return true;
                }
            }
            worldState.setMatchedPattern(null);
            return false;
        } finally {
            worldState.setCommitSuccessfulMatches(commitSuccessfulMatches);
        }
    }

    /**
     * Finds a pattern by identity.
     *
     * @param pattern pattern reference to locate
     * @return index, or {@code -1} when absent
     */
    private int findPatternIndex(BlockPattern pattern) {
        if (pattern == null) return -1;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] == pattern) return i;
        }
        return -1;
    }

    /**
     * Clamps an explicit pattern index into the available range.
     *
     * @param selectedPatternIndex requested index
     * @return valid index, or {@code -1} when no explicit selection is available
     */
    private int normalizeSelectedPatternIndex(int selectedPatternIndex) {
        if (selectedPatternIndex < 0 || patterns.length == 0) return -1;
        return Math.min(selectedPatternIndex, patterns.length - 1);
    }
}
