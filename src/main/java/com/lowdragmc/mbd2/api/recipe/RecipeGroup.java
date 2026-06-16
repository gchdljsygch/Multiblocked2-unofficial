package com.lowdragmc.mbd2.api.recipe;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Validates and compares four-character recipe group identifiers.
 *
 * <p>The business goal is to partition recipes and handlers into compact
 * routing groups while preserving two reserved groups: {@link #DEFAULT} for
 * ungrouped handlers and {@link #ANY} for wildcard matching. Group identifiers
 * are exactly four ASCII base62 characters. Methods are stateless and
 * thread-safe.</p>
 */
public final class RecipeGroup {
    /**
     * Default group used when no valid group is supplied.
     */
    public static final String DEFAULT = "0000";
    /**
     * Wildcard group that matches every normalized group.
     */
    public static final String ANY = "ZZZZ";
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Pattern VALID_GROUP = Pattern.compile("[0-9A-Za-z]{4}");

    private RecipeGroup() {
    }

    /**
     * Validates a required recipe group identifier.
     *
     * <p>Preconditions: the group must be non-null, non-empty, and match
     * {@code [0-9A-Za-z]{4}}. Side effects: none.</p>
     *
     * @param recipeGroup candidate group value
     * @return the unchanged group string when valid
     * @throws IllegalArgumentException when the value is absent or not exactly
     *                                  four ASCII letters or digits
     */
    public static String normalize(@Nullable String recipeGroup) {
        if (recipeGroup == null || recipeGroup.isEmpty()) {
            throw new IllegalArgumentException("Recipe group must be exactly four ASCII letters or digits: " + recipeGroup);
        }
        if (!VALID_GROUP.matcher(recipeGroup).matches()) {
            throw new IllegalArgumentException("Recipe group must be exactly four ASCII letters or digits: " + recipeGroup);
        }
        return recipeGroup;
    }

    /**
     * Validates an optional recipe group identifier.
     *
     * @param recipeGroup candidate group value; {@code null} or empty means no
     *                    explicit group
     * @return a valid four-character group, or {@code null} when absent
     * @throws IllegalArgumentException when a non-empty value is malformed
     */
    @Nullable
    public static String normalizeOptional(@Nullable String recipeGroup) {
        if (recipeGroup == null || recipeGroup.isEmpty()) {
            return null;
        }
        return normalize(recipeGroup);
    }

    /**
     * Normalizes a group or falls back to {@link #DEFAULT}.
     *
     * <p>Unlike {@link #normalizeOptional(String)}, malformed non-empty values do
     * not propagate an exception.</p>
     *
     * @param recipeGroup candidate group value
     * @return valid group value, or {@link #DEFAULT} when absent or invalid
     */
    public static String normalizeOrDefault(@Nullable String recipeGroup) {
        try {
            var normalized = normalizeOptional(recipeGroup);
            return normalized == null ? DEFAULT : normalized;
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    /**
     * Tests whether a value declares a valid explicit group.
     *
     * @param recipeGroup candidate group value
     * @return {@code true} when the value is non-empty and valid
     * @throws IllegalArgumentException when a non-empty value is malformed
     */
    public static boolean hasGroup(@Nullable String recipeGroup) {
        return normalizeOptional(recipeGroup) != null;
    }

    /**
     * Tests whether a value represents the default group.
     *
     * @param recipeGroup candidate group value
     * @return {@code true} only when the normalized optional group equals
     * {@link #DEFAULT}
     * @throws IllegalArgumentException when a non-empty value is malformed
     */
    public static boolean isDefault(@Nullable String recipeGroup) {
        return DEFAULT.equals(normalizeOptional(recipeGroup));
    }

    /**
     * Checks whether a recipe group can run on a handler group.
     *
     * <p>Business rule: absent recipe groups never match; absent or invalid
     * handler groups behave as {@link #DEFAULT}; either side equal to
     * {@link #ANY} matches all valid counterpart groups.</p>
     *
     * @param recipeGroup  recipe's optional group; malformed non-empty values
     *                     throw from normalization
     * @param handlerGroup handler's optional group; absent or malformed values
     *                     are treated as {@link #DEFAULT}
     * @return {@code true} when the recipe may use the handler group
     */
    public static boolean matches(@Nullable String recipeGroup, @Nullable String handlerGroup) {
        var normalizedRecipeGroup = normalizeOptional(recipeGroup);
        if (normalizedRecipeGroup == null) {
            return false;
        }
        var normalizedHandlerGroup = normalizeOrDefault(handlerGroup);
        return ANY.equals(normalizedRecipeGroup) ||
                ANY.equals(normalizedHandlerGroup) ||
                normalizedRecipeGroup.equals(normalizedHandlerGroup);
    }

    /**
     * Derives a deterministic non-reserved group from an integer hash.
     *
     * <p>Preconditions: any signed int is accepted and treated as unsigned for
     * modulo reduction. Side effects: none.</p>
     *
     * @param hash source hash, commonly from a recipe id or handler id
     * @return four-character base62 group in the valid range, never
     * {@link #DEFAULT} or {@link #ANY}
     */
    public static String fromHash(int hash) {
        var value = Integer.toUnsignedLong(hash) % (62L * 62L * 62L * 62L);
        var group = new char[4];
        for (int i = group.length - 1; i >= 0; i--) {
            group[i] = BASE62[(int) (value % BASE62.length)];
            value /= BASE62.length;
        }
        var normalized = new String(group);
        if (DEFAULT.equals(normalized) || ANY.equals(normalized)) {
            return "0001";
        }
        return normalized;
    }
}
