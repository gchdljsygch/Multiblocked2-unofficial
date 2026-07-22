package com.lowdragmc.mbd2.api.pattern;

/**
 * Backtracking search for aisle repetition counts around a fixed controller aisle.
 *
 * <p>The matcher owns only repetition geometry. Callers provide transactional slice
 * matching so failed candidates can be rolled back without leaking predicate counts,
 * caches, parts, or other match context into the next candidate.</p>
 */
final class AisleRepeatMatcher {

    private AisleRepeatMatcher() {
    }

    interface Transaction<S> {
        S snapshot();

        void restore(S snapshot);

        boolean matchSlice(int aisleIndex, int aisleOffset);

        boolean validateComplete();
    }

    /**
     * Finds a complete repetition assignment, preferring larger structures.
     *
     * @return one repeat count per aisle, or {@code null} when no assignment matches
     */
    static <S> int[] findMatch(int[][] ranges, int controllerAisle, Transaction<S> transaction) {
        if (ranges.length == 0 || controllerAisle < 0 || controllerAisle >= ranges.length) {
            return null;
        }

        int[] minBeforeController = new int[ranges.length + 1];
        int[] maxBeforeController = new int[ranges.length + 1];
        for (int aisle = controllerAisle - 1; aisle >= 0; aisle--) {
            minBeforeController[aisle] = Math.addExact(minBeforeController[aisle + 1], ranges[aisle][0]);
            maxBeforeController[aisle] = Math.addExact(maxBeforeController[aisle + 1], ranges[aisle][1]);
        }

        S initial = transaction.snapshot();
        int[] repetitions = new int[ranges.length];
        for (int startOffset = -maxBeforeController[0]; startOffset <= -minBeforeController[0]; startOffset++) {
            transaction.restore(initial);
            if (matchAisles(ranges, controllerAisle, minBeforeController, maxBeforeController,
                    transaction, repetitions, 0, startOffset)) {
                return repetitions.clone();
            }
        }
        transaction.restore(initial);
        return null;
    }

    private static <S> boolean matchAisles(int[][] ranges,
                                           int controllerAisle,
                                           int[] minBeforeController,
                                           int[] maxBeforeController,
                                           Transaction<S> transaction,
                                           int[] repetitions,
                                           int aisle,
                                           int aisleOffset) {
        if (aisle == ranges.length) {
            return transaction.validateComplete();
        }

        if (aisle <= controllerAisle) {
            int distanceToController = -aisleOffset;
            if (distanceToController < minBeforeController[aisle]
                    || distanceToController > maxBeforeController[aisle]) {
                return false;
            }
            if (aisle == controllerAisle && aisleOffset != 0) {
                return false;
            }
        }

        S beforeAisle = transaction.snapshot();
        int min = ranges[aisle][0];
        int candidate = ranges[aisle][1];
        while (candidate >= min) {
            transaction.restore(beforeAisle);
            int matched = 0;
            while (matched < candidate && transaction.matchSlice(aisle, aisleOffset + matched)) {
                matched++;
            }

            if (matched == candidate) {
                repetitions[aisle] = candidate;
                if (matchAisles(ranges, controllerAisle, minBeforeController, maxBeforeController,
                        transaction, repetitions, aisle + 1, aisleOffset + candidate)) {
                    return true;
                }
                candidate--;
            } else {
                // Every larger count contains the same failed prefix. The matched prefix
                // itself is the next count that can possibly form a valid boundary.
                candidate = matched;
            }
        }

        transaction.restore(beforeAisle);
        return false;
    }
}
