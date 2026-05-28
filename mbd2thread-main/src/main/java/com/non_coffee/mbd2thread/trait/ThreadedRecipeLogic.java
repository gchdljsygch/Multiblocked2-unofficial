package com.non_coffee.mbd2thread.trait;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public ThreadedRecipeLogic(IMachine machine, int threadId) {
        super(machine);
        this.threadId = threadId;
    }

    public int getThreadId() {
        return threadId;
    }

    public Set<String> getWhitelist() {
        return whitelist;
    }

    public Set<String> getBlacklist() {
        return blacklist;
    }

    public void setExternalRecipeBlocklist(Set<String> recipeIdsLowercase) {
        externalRecipeBlocklist.clear();
        if (recipeIdsLowercase != null) {
            externalRecipeBlocklist.addAll(recipeIdsLowercase);
        }
    }

    public void setExternalRecipeAllowlist(Set<String> recipeIdsLowercase) {
        if (recipeIdsLowercase == null || recipeIdsLowercase.isEmpty()) {
            disableExternalRecipeAllowlist();
            return;
        }
        externalRecipeAllowlistEnabled = true;
        externalRecipeAllowlist.clear();
        externalRecipeAllowlist.addAll(recipeIdsLowercase);
    }

    public void disableExternalRecipeAllowlist() {
        externalRecipeAllowlistEnabled = false;
        externalRecipeAllowlist.clear();
    }

    public String getIdleText() {
        return idleText;
    }

    public void setIdleText(String idleText) {
        this.idleText = normalizeText(idleText);
    }

    public String getRunningText() {
        return runningText;
    }

    public void setRunningText(String runningText) {
        this.runningText = normalizeText(runningText);
    }

    public String getWaitingText() {
        return waitingText;
    }

    public void setWaitingText(String waitingText) {
        this.waitingText = normalizeText(waitingText);
    }

    public void addToWhitelist(String recipeId) {
        whitelist.add(recipeId);
    }

    public void removeFromWhitelist(String recipeId) {
        whitelist.remove(recipeId);
    }
    
    public void clearWhitelist() {
        whitelist.clear();
    }

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

    @Override
    public void onRecipeFinish() {
        markLastRecipeDirty();
        super.onRecipeFinish();
    }
    
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
