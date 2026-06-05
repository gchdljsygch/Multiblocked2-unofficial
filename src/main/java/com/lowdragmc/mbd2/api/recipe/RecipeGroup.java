package com.lowdragmc.mbd2.api.recipe;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class RecipeGroup {
    public static final String DEFAULT = "0000";
    public static final String ANY = "ZZZZ";
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Pattern VALID_GROUP = Pattern.compile("[0-9A-Za-z]{4}");

    private RecipeGroup() {
    }

    public static String normalize(@Nullable String recipeGroup) {
        if (recipeGroup == null || recipeGroup.isEmpty()) {
            throw new IllegalArgumentException("Recipe group must be exactly four ASCII letters or digits: " + recipeGroup);
        }
        if (!VALID_GROUP.matcher(recipeGroup).matches()) {
            throw new IllegalArgumentException("Recipe group must be exactly four ASCII letters or digits: " + recipeGroup);
        }
        return recipeGroup;
    }

    @Nullable
    public static String normalizeOptional(@Nullable String recipeGroup) {
        if (recipeGroup == null || recipeGroup.isEmpty()) {
            return null;
        }
        return normalize(recipeGroup);
    }

    public static String normalizeOrDefault(@Nullable String recipeGroup) {
        try {
            var normalized = normalizeOptional(recipeGroup);
            return normalized == null ? DEFAULT : normalized;
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    public static boolean hasGroup(@Nullable String recipeGroup) {
        return normalizeOptional(recipeGroup) != null;
    }

    public static boolean isDefault(@Nullable String recipeGroup) {
        return DEFAULT.equals(normalizeOptional(recipeGroup));
    }

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
