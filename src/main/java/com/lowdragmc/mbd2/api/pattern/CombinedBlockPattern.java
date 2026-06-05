package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public class CombinedBlockPattern extends BlockPattern {
    private static final RelativeDirection[] DEFAULT_STRUCTURE_DIR = new RelativeDirection[]{
            RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT
    };

    private final BlockPattern[] patterns;

    public CombinedBlockPattern(BlockPattern... patterns) {
        super(new TraceabilityPredicate[0][0][0], DEFAULT_STRUCTURE_DIR, new int[0][2], new int[5]);
        this.patterns = Arrays.stream(patterns)
                .filter(Objects::nonNull)
                .toArray(BlockPattern[]::new);
    }

    public BlockPattern[] getPatterns() {
        return patterns.clone();
    }

    @Override
    public int getEstimatedBlockCount() {
        return Arrays.stream(patterns)
                .mapToInt(BlockPattern::getEstimatedBlockCount)
                .max()
                .orElse(0);
    }

    @Override
    public boolean checkPatternAtWithoutController(MultiblockState worldState, Direction facing) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAtWithoutController(worldState, facing));
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, savePredicate));
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction facing, boolean savePredicate) {
        return checkPatterns(worldState, pattern -> pattern.checkPatternAt(worldState, centerPos, facing, savePredicate));
    }

    @Override
    public void autoBuild(Player player, MultiblockState worldState) {
        autoBuild(player, worldState, -1);
    }

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

    @Override
    public com.lowdragmc.lowdraglib.utils.BlockInfo[][][] getPreview(int[] repetition) {
        return patterns.length == 0 ? new com.lowdragmc.lowdraglib.utils.BlockInfo[0][0][0] : patterns[0].getPreview(repetition);
    }

    private boolean checkPatterns(MultiblockState worldState, Predicate<BlockPattern> checker) {
        int matchedIndex = findPatternIndex(worldState.getMatchedPattern());
        if (matchedIndex >= 0 && checker.test(patterns[matchedIndex])) {
            worldState.setMatchedPattern(patterns[matchedIndex], matchedIndex);
            return true;
        }
        for (int i = 0; i < patterns.length; i++) {
            if (i == matchedIndex) continue;
            BlockPattern pattern = patterns[i];
            if (checker.test(pattern)) {
                worldState.setMatchedPattern(pattern, i);
                return true;
            }
        }
        worldState.setMatchedPattern(null);
        return false;
    }

    private int findPatternIndex(BlockPattern pattern) {
        if (pattern == null) return -1;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] == pattern) return i;
        }
        return -1;
    }

    private int normalizeSelectedPatternIndex(int selectedPatternIndex) {
        if (selectedPatternIndex < 0 || patterns.length == 0) return -1;
        return Math.min(selectedPatternIndex, patterns.length - 1);
    }
}
