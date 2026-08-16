package com.lowdragmc.mbd2.common.trait.recipethread;

import java.util.Set;

/**
 * Immutable per-lane recipe filter snapshot used by assignment and search paths.
 */
record RecipeThreadFilter(Set<String> whitelist, Set<String> blacklist) {

    static final RecipeThreadFilter ALLOW_ALL = new RecipeThreadFilter(Set.of(), Set.of());

    RecipeThreadFilter {
        whitelist = whitelist == null || whitelist.isEmpty() ? Set.of() : Set.copyOf(whitelist);
        blacklist = blacklist == null || blacklist.isEmpty() ? Set.of() : Set.copyOf(blacklist);
    }

    boolean allows(String recipeIdLowercase) {
        if (recipeIdLowercase == null || recipeIdLowercase.isEmpty()) return true;
        if (blacklist.contains(recipeIdLowercase)) return false;
        return whitelist.isEmpty() || whitelist.contains(recipeIdLowercase);
    }
}
