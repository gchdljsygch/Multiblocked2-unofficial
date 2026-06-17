package com.lowdragmc.mbd2.common.trait.recipethread;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Additional logical recipe lane owned by {@link RecipeThreadTrait}.
 *
 * <p>Each instance reuses the standard {@link RecipeLogic} state machine but filters recipe candidates through
 * per-thread whitelist/blacklist settings and assignment sets supplied by the owning trait. It is persisted as trait
 * state and is expected to tick on the logical server thread, not on a Java background thread.</p>
 */
public class ThreadedRecipeLogic extends RecipeLogic {

    private final int threadId;

    @Persisted
    private final Set<String> whitelist = new HashSet<>();

    @Persisted
    private final Set<String> blacklist = new HashSet<>();

    private final Set<String> externalRecipeBlocklist = new HashSet<>();
    private final Set<String> externalRecipeAllowlist = new HashSet<>();
    private boolean externalRecipeAllowlistEnabled;

    @Persisted
    private String idleText;

    @Persisted
    private String runningText;

    @Persisted
    private String waitingText;

    /**
     * Creates a logical recipe lane for one machine.
     *
     * @param machine  machine whose recipes will be executed by this lane
     * @param threadId logical lane id; extra lanes start at {@code 1}
     */
    public ThreadedRecipeLogic(IMachine machine, int threadId) {
        super(machine);
        this.threadId = threadId;
    }

    /**
     * Returns this lane's stable logical id.
     *
     * @return lane id, where {@code 1..n} are extra lanes managed by {@link RecipeThreadTrait}
     */
    public int getThreadId() {
        return threadId;
    }

    /**
     * Returns the mutable persisted whitelist backing this lane.
     *
     * <p>Entries are lower-case recipe id strings. Callers that mutate the set should do so from the server thread
     * and understand that it directly affects subsequent recipe searches.</p>
     *
     * @return mutable whitelist set
     */
    public Set<String> getWhitelist() {
        return whitelist;
    }

    /**
     * Returns the mutable persisted blacklist backing this lane.
     *
     * @return mutable set of lower-case recipe id strings excluded from this lane
     */
    public Set<String> getBlacklist() {
        return blacklist;
    }

    /**
     * Replaces the transient blocklist supplied by the coordinator.
     *
     * <p>This is used to prevent two lanes from starting the same recipe when same-recipe execution is disabled.
     * Passing {@code null} clears the blocklist.</p>
     *
     * @param recipeIdsLowercase lower-case recipe ids to reject during search
     */
    public void setExternalRecipeBlocklist(Set<String> recipeIdsLowercase) {
        externalRecipeBlocklist.clear();
        if (recipeIdsLowercase != null) {
            externalRecipeBlocklist.addAll(recipeIdsLowercase);
        }
    }

    /**
     * Restricts this lane to a transient set of assigned recipe ids.
     *
     * <p>An empty or {@code null} set disables the external allowlist entirely. Entries must already be lower-case
     * recipe id strings.</p>
     *
     * @param recipeIdsLowercase recipe ids this lane may search
     */
    public void setExternalRecipeAllowlist(Set<String> recipeIdsLowercase) {
        if (recipeIdsLowercase == null || recipeIdsLowercase.isEmpty()) {
            disableExternalRecipeAllowlist();
            return;
        }
        externalRecipeAllowlistEnabled = true;
        externalRecipeAllowlist.clear();
        externalRecipeAllowlist.addAll(recipeIdsLowercase);
    }

    /**
     * Clears the transient assignment allowlist.
     */
    public void disableExternalRecipeAllowlist() {
        externalRecipeAllowlistEnabled = false;
        externalRecipeAllowlist.clear();
    }

    /**
     * Returns this lane's custom idle text key.
     *
     * @return trimmed localization key or literal text, or {@code null} to use the trait default
     */
    public String getIdleText() {
        return idleText;
    }

    /**
     * Stores custom idle text for this lane.
     *
     * @param idleText localization key or literal text; blank values are normalized to {@code null}
     */
    public void setIdleText(String idleText) {
        this.idleText = normalizeText(idleText);
    }

    /**
     * Returns this lane's custom running text key.
     *
     * @return trimmed localization key or literal text, or {@code null} to use the trait default
     */
    public String getRunningText() {
        return runningText;
    }

    /**
     * Stores custom running text for this lane.
     *
     * @param runningText localization key or literal text; blank values are normalized to {@code null}
     */
    public void setRunningText(String runningText) {
        this.runningText = normalizeText(runningText);
    }

    /**
     * Returns this lane's custom waiting text key.
     *
     * @return trimmed localization key or literal text, or {@code null} to use the trait default
     */
    public String getWaitingText() {
        return waitingText;
    }

    /**
     * Stores custom waiting text for this lane.
     *
     * @param waitingText localization key or literal text; blank values are normalized to {@code null}
     */
    public void setWaitingText(String waitingText) {
        this.waitingText = normalizeText(waitingText);
    }

    /**
     * Adds one recipe id to the persisted whitelist.
     *
     * @param recipeId lower-case recipe id string
     */
    public void addToWhitelist(String recipeId) {
        whitelist.add(recipeId);
    }

    /**
     * Removes one recipe id from the persisted whitelist.
     *
     * @param recipeId lower-case recipe id string
     */
    public void removeFromWhitelist(String recipeId) {
        whitelist.remove(recipeId);
    }

    /**
     * Clears every persisted whitelist entry.
     */
    public void clearWhitelist() {
        whitelist.clear();
    }

    /**
     * Searches recipes and filters candidates for this lane.
     *
     * <p>Filtering order is external allowlist, persisted blacklist, external blocklist, then persisted whitelist.
     * Recipe ids are compared as lower-case strings. The returned list is newly allocated and safe for
     * {@link RecipeLogic} to mutate.</p>
     *
     * @return recipe candidates allowed for this lane
     */
    @Override
    protected List<MBDRecipe> searchRecipe() {
        var recipeType = getMachine().getRecipeType();
        var allRecipes = recipeType.searchRecipe(getRecipeManager(), getMachine());

        List<MBDRecipe> candidates = new ArrayList<>();

        for (MBDRecipe recipe : allRecipes) {
            String recipeId = recipe.getId() == null ? "" : recipe.getId().toString().toLowerCase(Locale.ROOT);
            if (externalRecipeAllowlistEnabled && !externalRecipeAllowlist.contains(recipeId)) {
                continue;
            }
            if (!blacklist.isEmpty() && blacklist.contains(recipeId)) {
                continue;
            }
            if (!externalRecipeBlocklist.isEmpty() && externalRecipeBlocklist.contains(recipeId)) {
                continue;
            }
            if (!whitelist.isEmpty() && !whitelist.contains(recipeId)) {
                continue;
            }
            candidates.add(recipe);
        }

        return candidates;
    }

    /**
     * Marks the cached last recipe as dirty before delegating finish handling.
     */
    @Override
    public void onRecipeFinish() {
        markLastRecipeDirty();
        super.onRecipeFinish();
    }

    /**
     * Chooses the display text for the lane's current state.
     *
     * @param defaultIdle    fallback when idle text is unset
     * @param defaultRunning fallback when running text is unset
     * @param defaultWaiting fallback when waiting text is unset
     * @return lane-specific text if configured, otherwise the relevant fallback
     */
    public String getStatusText(String defaultIdle, String defaultRunning, String defaultWaiting) {
        if (isIdle()) {
            return idleText != null && !idleText.isEmpty() ? idleText : defaultIdle;
        } else if (isWorking()) {
            return runningText != null && !runningText.isEmpty() ? runningText : defaultRunning;
        } else if (isWaiting()) {
            return waitingText != null && !waitingText.isEmpty() ? waitingText : defaultWaiting;
        }
        return defaultIdle;
    }

    private static String normalizeText(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
