package com.lowdragmc.mbd2.api.pattern;

import com.google.common.base.Joiner;
import com.lowdragmc.mbd2.api.pattern.util.RelativeDirection;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Fluent builder for symbolic multiblock patterns.
 *
 * <p>The business goal is to let machine definitions describe a 3D structure as
 * repeated string aisles and character-to-predicate mappings, then compile that
 * description into a {@link BlockPattern}. Builder instances are mutable and not
 * thread-safe; construct them during definition loading or datagen-style setup.</p>
 */
public class FactoryBlockPattern {
    private static final Joiner COMMA_JOIN = Joiner.on(",");
    private final List<String[]> depth;
    private final List<int[]> aisleRepetitions;
    private final Map<Character, TraceabilityPredicate> symbolMap;
    private final RelativeDirection[] structureDir;
    private int aisleHeight;
    private int rowWidth;

    /**
     * Creates a builder with explicit pattern-axis directions.
     *
     * <p>Preconditions: the three relative directions must cover three different
     * axes: vertical, horizontal-left/right, and front/back. Side effects:
     * registers the space character as {@link Predicates#any()}.</p>
     *
     * @param charDir   direction represented by advancing across characters in a
     *                  row
     * @param stringDir direction represented by advancing through rows in an
     *                  aisle
     * @param aisleDir  direction represented by adding aisles
     */
    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        depth = new ArrayList<>();
        aisleRepetitions = new ArrayList<>();
        symbolMap = new HashMap<>();
        structureDir = new RelativeDirection[3];
        structureDir[0] = charDir;
        structureDir[1] = stringDir;
        structureDir[2] = aisleDir;
        int flags = 0;
        for (int i = 0; i < 3; i++) {
            switch (structureDir[i]) {
                case UP, DOWN -> flags |= 0x1;
                case LEFT, RIGHT -> flags |= 0x2;
                case FRONT, BACK -> flags |= 0x4;
            }
        }
        if (flags != 0x7) throw new IllegalArgumentException("Must have 3 different axes!");
        this.symbolMap.put(' ', Predicates.any());
    }

    /**
     * Adds a repeatable aisle to this pattern.
     *
     * <p>Preconditions: aisle rows must be non-empty and every aisle added to the
     * builder must have the same height and row width. Side effects: records new
     * symbols as missing predicates until {@link #where(char, TraceabilityPredicate)}
     * supplies them.</p>
     *
     * @param minRepeat minimum number of times this aisle must appear; must be
     *                  {@code <= maxRepeat}
     * @param maxRepeat maximum number of times this aisle may appear
     * @param aisle     string rows for one z-slice
     * @return this builder for chaining
     */
    public FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        if (!ArrayUtils.isEmpty(aisle) && !StringUtils.isEmpty(aisle[0])) {
            if (this.depth.isEmpty()) {
                this.aisleHeight = aisle.length;
                this.rowWidth = aisle[0].length();
            }

            if (aisle.length != this.aisleHeight) {
                throw new IllegalArgumentException("Expected aisle with height of " + this.aisleHeight + ", but was given one with a height of " + aisle.length + ")");
            } else {
                for (String s : aisle) {
                    if (s.length() != this.rowWidth) {
                        throw new IllegalArgumentException("Not all rows in the given aisle are the correct width (expected " + this.rowWidth + ", found one with " + s.length() + ")");
                    }

                    for (char c0 : s.toCharArray()) {
                        if (!this.symbolMap.containsKey(c0)) {
                            this.symbolMap.put(c0, null);
                        }
                    }
                }

                this.depth.add(aisle);
                if (minRepeat > maxRepeat)
                    throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
                aisleRepetitions.add(new int[]{minRepeat, maxRepeat});
                return this;
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    /**
     * Adds a single aisle to this pattern. (so multiple calls to this will increase the aisleDir by 1)
     *
     * @param aisle string rows for one z-slice
     * @return this builder for chaining
     */
    public FactoryBlockPattern aisle(String... aisle) {
        return aisleRepeatable(1, 1, aisle);
    }

    /**
     * Sets the repeat range for the last added aisle.
     *
     * @param minRepeat minimum repeat count; must be {@code <= maxRepeat}
     * @param maxRepeat maximum repeat count
     * @return this builder for chaining
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[]{minRepeat, maxRepeat});
        return this;
    }

    /**
     * Sets an exact repeat count for the last added aisle.
     *
     * @param repeatCount exact number of repetitions
     * @return this builder for chaining
     */
    public FactoryBlockPattern setRepeatable(int repeatCount) {
        return setRepeatable(repeatCount, repeatCount);
    }

    /**
     * Starts a pattern using the default axes: characters move left, rows move
     * up, and aisles move front.
     *
     * @return new mutable pattern builder
     */
    public static FactoryBlockPattern start() {
        return new FactoryBlockPattern(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT);
    }

    /**
     * Starts a pattern with explicit axis directions.
     *
     * @param charDir   direction represented by advancing across characters in a
     *                  row
     * @param stringDir direction represented by advancing through rows in an
     *                  aisle
     * @param aisleDir  direction represented by adding aisles
     * @return new mutable pattern builder
     */
    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir);
    }

    /**
     * Assigns a predicate to the first character of a string symbol.
     *
     * @param symbol       non-empty symbol string
     * @param blockMatcher predicate used for positions with that symbol
     * @return this builder for chaining
     */
    public FactoryBlockPattern where(String symbol, TraceabilityPredicate blockMatcher) {
        return this.where(symbol.charAt(0), blockMatcher);
    }

    /**
     * Assigns a predicate to a pattern symbol.
     *
     * <p>Side effects: stores {@code any} and {@code air} predicates directly;
     * other predicates are copied and sorted so limited predicates are checked in
     * deterministic order.</p>
     *
     * @param symbol       character used in aisle strings
     * @param blockMatcher predicate for that symbol
     * @return this builder for chaining
     */
    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        if (blockMatcher.isAny() || blockMatcher.isAir()) {
            this.symbolMap.put(symbol, blockMatcher);
        } else {
            this.symbolMap.put(symbol, new TraceabilityPredicate(blockMatcher).sort());
        }
        return this;
    }

    /**
     * Compiles the symbolic pattern into a runtime block pattern.
     *
     * <p>Preconditions: every non-space symbol used by aisles must have a
     * predicate, and one predicate should identify the controller. Side effects:
     * none on the builder beyond reading its current state.</p>
     *
     * @return compiled block pattern
     * @throws IllegalStateException when any symbol is missing a predicate
     */
    public BlockPattern build() {
        this.checkMissingPredicates();
        int[] centerOffset = new int[5];
        int[][] aisleRepetitions = this.aisleRepetitions.toArray(new int[this.aisleRepetitions.size()][]);
        TraceabilityPredicate[][][] predicate = (TraceabilityPredicate[][][]) Array.newInstance(TraceabilityPredicate.class, this.depth.size(), this.aisleHeight, this.rowWidth);

        for (int i = 0, minZ = 0, maxZ = 0; i < this.depth.size(); minZ += aisleRepetitions[i][0], maxZ += aisleRepetitions[i][1], i++) {
            for (int j = 0; j < this.aisleHeight; j++) {
                for (int k = 0; k < this.rowWidth; k++) {
                    predicate[i][j][k] = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                    if (predicate[i][j][k].isController) {
                        centerOffset = new int[]{k, j, i, minZ, maxZ};
                    }
                }
            }
        }

        return new BlockPattern(predicate, structureDir, aisleRepetitions, centerOffset);
    }

    private TraceabilityPredicate[][][] makePredicateArray() {
        this.checkMissingPredicates();
        TraceabilityPredicate[][][] predicate = (TraceabilityPredicate[][][]) Array.newInstance(TraceabilityPredicate.class, this.depth.size(), this.aisleHeight, this.rowWidth);

        for (int i = 0; i < this.depth.size(); ++i) {
            for (int j = 0; j < this.aisleHeight; ++j) {
                for (int k = 0; k < this.rowWidth; ++k) {
                    predicate[i][j][k] = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                }
            }
        }

        return predicate;
    }

    private void checkMissingPredicates() {
        List<Character> list = new ArrayList<>();

        for (Entry<Character, TraceabilityPredicate> entry : this.symbolMap.entrySet()) {
            if (entry.getValue() == null) {
                list.add(entry.getKey());
            }
        }

        if (!list.isEmpty()) {
            throw new IllegalStateException("Predicates for character(s) " + COMMA_JOIN.join(list) + " are missing");
        }
    }
}
