package com.lowdragmc.mbd2.api.pattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AisleRepeatMatcherTest {

    @Test
    void rejectsMatchWhenAnEarlierAisleDoesNotReachItsMinimum() {
        var transaction = new SymbolTransaction(
                Map.of(-4, 'A', -3, 'B', -2, 'B', -1, 'B', 0, 'C'),
                List.of(Set.of('A'), Set.of('B'), Set.of('C')));

        int[] result = AisleRepeatMatcher.findMatch(
                new int[][]{{2, 4}, {1, 3}, {1, 1}}, 2, transaction);

        assertNull(result);
        assertEquals(List.of(), transaction.acceptedOffsets);
    }

    @Test
    void backtracksWhenARepeatableAisleCanAlsoMatchTheController() {
        var transaction = new SymbolTransaction(
                Map.of(-2, 'A', -1, 'R', 0, 'C'),
                List.of(Set.of('A'), Set.of('R', 'C'), Set.of('C')));

        int[] result = AisleRepeatMatcher.findMatch(
                new int[][]{{1, 1}, {1, 2}, {1, 1}}, 2, transaction);

        assertArrayEquals(new int[]{1, 1, 1}, result);
        assertEquals(List.of(-2, -1, 0), transaction.acceptedOffsets);
    }

    @Test
    void failedCandidatesDoNotLeakTransactionalState() {
        var transaction = new SymbolTransaction(
                Map.of(-2, 'A', -1, 'R', 0, 'C'),
                List.of(Set.of('A'), Set.of('R', 'C'), Set.of('C')));

        int[] result = AisleRepeatMatcher.findMatch(
                new int[][]{{1, 1}, {1, 2}, {1, 1}}, 2, transaction);

        assertArrayEquals(new int[]{1, 1, 1}, result);
        assertEquals(3, transaction.mutationCount);
        assertEquals(List.of(-2, -1, 0), transaction.acceptedOffsets);
    }

    @Test
    void resolvesMultipleRepeatableAislesTogether() {
        var transaction = new SymbolTransaction(
                Map.of(-4, 'A', -3, 'A', -2, 'B', -1, 'B', 0, 'C'),
                List.of(Set.of('A'), Set.of('B'), Set.of('C')));

        int[] result = AisleRepeatMatcher.findMatch(
                new int[][]{{1, 2}, {1, 2}, {1, 1}}, 2, transaction);

        assertArrayEquals(new int[]{2, 2, 1}, result);
        assertEquals(List.of(-4, -3, -2, -1, 0), transaction.acceptedOffsets);
    }

    @Test
    void agreesWithExhaustiveEnumerationAcrossRandomSmallPatterns() {
        Random random = new Random(0x4d424432L);
        for (int iteration = 0; iteration < 500; iteration++) {
            int aisleCount = 1 + random.nextInt(4);
            int controllerAisle = random.nextInt(aisleCount);
            int[][] ranges = new int[aisleCount][2];
            List<Set<Character>> acceptedSymbols = new ArrayList<>();
            for (int aisle = 0; aisle < aisleCount; aisle++) {
                int min = 1 + random.nextInt(2);
                int max = min + random.nextInt(3 - min + 1);
                ranges[aisle] = aisle == controllerAisle ? new int[]{1, 1} : new int[]{min, max};
                Set<Character> accepted = new HashSet<>();
                for (char symbol = 'A'; symbol <= 'C'; symbol++) {
                    if (random.nextBoolean()) {
                        accepted.add(symbol);
                    }
                }
                if (accepted.isEmpty()) {
                    accepted.add((char) ('A' + random.nextInt(3)));
                }
                acceptedSymbols.add(accepted);
            }

            Map<Integer, Character> world = new HashMap<>();
            for (int offset = -12; offset <= 12; offset++) {
                world.put(offset, (char) ('A' + random.nextInt(3)));
            }

            int[] result = AisleRepeatMatcher.findMatch(
                    ranges, controllerAisle, new SymbolTransaction(world, acceptedSymbols));
            boolean expected = hasExhaustiveMatch(world, acceptedSymbols, ranges, controllerAisle,
                    new int[aisleCount], 0);
            assertEquals(expected, result != null, "iteration " + iteration);
            if (result != null) {
                assertNotNull(result);
                assertTrue(isValidMatch(world, acceptedSymbols, result, controllerAisle),
                        "invalid assignment at iteration " + iteration);
            }
        }
    }

    private static boolean hasExhaustiveMatch(Map<Integer, Character> world,
                                              List<Set<Character>> acceptedSymbols,
                                              int[][] ranges,
                                              int controllerAisle,
                                              int[] repetitions,
                                              int aisle) {
        if (aisle == ranges.length) {
            return isValidMatch(world, acceptedSymbols, repetitions, controllerAisle);
        }
        for (int repeat = ranges[aisle][0]; repeat <= ranges[aisle][1]; repeat++) {
            repetitions[aisle] = repeat;
            if (hasExhaustiveMatch(world, acceptedSymbols, ranges, controllerAisle, repetitions, aisle + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidMatch(Map<Integer, Character> world,
                                        List<Set<Character>> acceptedSymbols,
                                        int[] repetitions,
                                        int controllerAisle) {
        int offset = 0;
        for (int aisle = 0; aisle < controllerAisle; aisle++) {
            offset -= repetitions[aisle];
        }
        for (int aisle = 0; aisle < repetitions.length; aisle++) {
            for (int repeat = 0; repeat < repetitions[aisle]; repeat++, offset++) {
                Character symbol = world.get(offset);
                if (symbol == null || !acceptedSymbols.get(aisle).contains(symbol)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class SymbolTransaction implements AisleRepeatMatcher.Transaction<Snapshot> {
        private final Map<Integer, Character> world;
        private final List<Set<Character>> acceptedSymbols;
        private final List<Integer> acceptedOffsets = new ArrayList<>();
        private int mutationCount;

        private SymbolTransaction(Map<Integer, Character> world, List<Set<Character>> acceptedSymbols) {
            this.world = world;
            this.acceptedSymbols = acceptedSymbols;
        }

        @Override
        public Snapshot snapshot() {
            return new Snapshot(acceptedOffsets.size(), mutationCount);
        }

        @Override
        public void restore(Snapshot snapshot) {
            acceptedOffsets.subList(snapshot.acceptedSize(), acceptedOffsets.size()).clear();
            mutationCount = snapshot.mutationCount();
        }

        @Override
        public boolean matchSlice(int aisleIndex, int aisleOffset) {
            mutationCount++;
            Character symbol = world.get(aisleOffset);
            if (symbol == null || !acceptedSymbols.get(aisleIndex).contains(symbol)) {
                return false;
            }
            acceptedOffsets.add(aisleOffset);
            return true;
        }

        @Override
        public boolean validateComplete() {
            return true;
        }
    }

    private record Snapshot(int acceptedSize, int mutationCount) {
    }
}
