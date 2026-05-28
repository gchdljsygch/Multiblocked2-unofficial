package com.non_coffee.mbd2thread.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.StringConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.runtime.ConfiguratorParser;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.IUIProviderTrait;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import com.non_coffee.mbd2thread.gui.widget.SyncedHoverTooltipWidget;
import com.non_coffee.mbd2thread.gui.widget.ThreadProgressDigitsLabelWidget;
import com.non_coffee.mbd2thread.gui.widget.ThreadStatusLabelWidget;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

@LDLRegister(name = "recipe_thread", group = "capability")
public class RecipeThreadTraitDefinition extends TraitDefinition implements IUIProviderTrait {

    private static final ResourceLocation AIR_ID = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:air"));

    @Configurable(name = "mbd2thread.trait.max_threads", tips = "mbd2thread.trait.max_threads.tips")
    @NumberRange(range = {1, 20})
    public int maxThreads = 20;

    @Configurable(name = "mbd2thread.trait.default_idle_text", tips = "mbd2thread.trait.default_idle_text.tips")
    public String defaultIdleText = "mbd2thread.state.idle";

    @Configurable(name = "mbd2thread.trait.default_running_text", tips = "mbd2thread.trait.default_running_text.tips")
    public String defaultRunningText = "mbd2thread.state.running";

    @Configurable(name = "mbd2thread.trait.default_waiting_text", tips = "mbd2thread.trait.default_waiting_text.tips")
    public String defaultWaitingText = "mbd2thread.state.waiting";

    @Configurable(name = "mbd2thread.trait.allow_same_recipe", tips = "mbd2thread.trait.allow_same_recipe.tips")
    public boolean allowSameRecipe = true;

    @Configurable(name = "mbd2thread.compat.enable_vanilla_fuel_line_a", tips = "mbd2thread.compat.enable_vanilla_fuel_line_a.tips")
    public boolean enableVanillaFuelLineA = false;

    @Configurable(name = "mbd2thread.compat.vanilla_fuel_item_trait_name", tips = "mbd2thread.compat.vanilla_fuel_item_trait_name.tips")
    public String vanillaFuelItemTraitName = "";

    @Persisted
    private final java.util.List<String> threadIdleTexts = new java.util.ArrayList<>();

    @Persisted
    private final java.util.List<String> threadRunningTexts = new java.util.ArrayList<>();

    @Persisted
    private final java.util.List<String> threadWaitingTexts = new java.util.ArrayList<>();

    @Persisted
    private final java.util.List<String> threadWhitelistEntries = new java.util.ArrayList<>();

    @Persisted
    private final java.util.List<String> threadBlacklistEntries = new java.util.ArrayList<>();

    @Override
    public ITrait createTrait(MBDMachine machine) {
        syncThreadConfigCount();
        return new RecipeThreadTrait(machine, this);
    }

    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        String p = uiPrefixName();

        ui.addWidget(new LabelWidget(0, 0, "mbd2thread.gui.title").setId(p + "_title"));

        DraggableScrollableWidgetGroup list = new DraggableScrollableWidgetGroup(0, 14, 176, 144);
        list.setYScrollBarWidth(4);
        list.setYBarStyle(ColorPattern.GRAY.rectTexture(), ColorPattern.WHITE.rectTexture().setRadius(2).transform(-0.5f, 0));
        list.setId(p + "_threadList");
        ui.addWidget(list);

        int total = Math.max(1, maxThreads);
        for (int i = 0; i < total; i++) {
            int rowY = i * 18;
            WidgetGroup row = new WidgetGroup(0, rowY, 172, 18);
            row.setId(p + "_row_" + i);

            row.addWidget(new LabelWidget(0, 5, "#" + i));
            row.addWidget(new ThreadStatusLabelWidget(24, 5, 146, 10).setId(p + "_status_" + i));
            row.addWidget(new ThreadProgressDigitsLabelWidget(24, 5, 10, 10).setId(p + "_progress_" + i));
            row.addWidget(new SyncedHoverTooltipWidget(0, 0, 172, 18).setId(p + "_tooltip_" + i));

            list.addWidget(row);
        }
    }

    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (!(trait instanceof RecipeThreadTrait recipeTrait)) return;
        String p = uiPrefixName();

        WidgetUtils.widgetByIdForEach(group, "^" + java.util.regex.Pattern.quote(p + "_status_") + "\\d+$", ThreadStatusLabelWidget.class, w -> {
            int id = WidgetUtils.widgetIdIndex(w);
            w.setStatusSupplier(() -> recipeTrait.getThreadStatusText(id));
        });

        WidgetUtils.widgetByIdForEach(group, "^" + java.util.regex.Pattern.quote(p + "_progress_") + "\\d+$", ThreadProgressDigitsLabelWidget.class, w -> {
            int id = WidgetUtils.widgetIdIndex(w);
            w.setProgressDigitsSupplier(() -> recipeTrait.getThreadProgressDigits(id));
            w.setAnchorStatusWidgetId(p + "_status_" + id);
        });

        WidgetUtils.widgetByIdForEach(group, "^" + java.util.regex.Pattern.quote(p + "_tooltip_") + "\\d+$", SyncedHoverTooltipWidget.class, w -> {
            int id = WidgetUtils.widgetIdIndex(w);
            w.setTooltipSupplier(() -> recipeTrait.getThreadHoverRecipeIdText(id));
        });
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("mbd2thread:textures/icon.png");
    }

    @ConfigSetter(field = "maxThreads")
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = Math.max(1, Math.min(20, maxThreads));
        syncThreadConfigCount();
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        syncThreadConfigCount();
        ConfiguratorParser.createConfigurators(father, new java.util.HashMap<>(), getClass(), this);

        ConfiguratorGroup threadsGroup = new ConfiguratorGroup("mbd2thread.config.threads", false);
        father.addConfigurators(threadsGroup);

        int total = Math.max(1, maxThreads);
        for (int i = 0; i < total; i++) {
            int threadId = i;
            ConfiguratorGroup threadGroup = new ConfiguratorGroup("Thread " + threadId, true);
            threadsGroup.addConfigurators(threadGroup);

            threadGroup.addConfigurators(new StringConfigurator(
                    "mbd2thread.config.thread.idle_text",
                    () -> getThreadIdleText(threadId),
                    v -> setThreadIdleText(threadId, v),
                    "",
                    false
            ));
            threadGroup.addConfigurators(new StringConfigurator(
                    "mbd2thread.config.thread.running_text",
                    () -> getThreadRunningText(threadId),
                    v -> setThreadRunningText(threadId, v),
                    "",
                    false
            ));
            threadGroup.addConfigurators(new StringConfigurator(
                    "mbd2thread.config.thread.waiting_text",
                    () -> getThreadWaitingText(threadId),
                    v -> setThreadWaitingText(threadId, v),
                    "",
                    false
            ));

            var whitelistGroup = new ArrayConfiguratorGroup<>(
                    "mbd2thread.config.thread.whitelist",
                    true,
                    () -> getThreadWhitelist(threadId),
                    (getter, setter) -> {
                        var c = new StringConfigurator("", () -> {
                            var v = getter.get();
                            return v == null ? "" : v.toString();
                        }, s -> {
                            String raw = s == null ? "" : s;
                            if (raw.isBlank()) {
                                setter.accept(AIR_ID);
                                return;
                            }
                            String trimmed = Objects.requireNonNull(raw.trim());
                            ResourceLocation parsed = ResourceLocation.tryParse(trimmed);
                            setter.accept(parsed == null ? AIR_ID : parsed);
                        }, "minecraft:air", false);
                        c.setResourceLocation(true);
                        return c;
                    },
                    false
            );
            whitelistGroup.setAddDefault(() -> AIR_ID);
            whitelistGroup.setOnUpdate(list -> setThreadWhitelist(threadId, list));
            threadGroup.addConfigurators(whitelistGroup);

            var blacklistGroup = new ArrayConfiguratorGroup<>(
                    "mbd2thread.config.thread.blacklist",
                    true,
                    () -> getThreadBlacklist(threadId),
                    (getter, setter) -> {
                        var c = new StringConfigurator("", () -> {
                            var v = getter.get();
                            return v == null ? "" : v.toString();
                        }, s -> {
                            String raw = s == null ? "" : s;
                            if (raw.isBlank()) {
                                setter.accept(AIR_ID);
                                return;
                            }
                            String trimmed = Objects.requireNonNull(raw.trim());
                            ResourceLocation parsed = ResourceLocation.tryParse(trimmed);
                            setter.accept(parsed == null ? AIR_ID : parsed);
                        }, "minecraft:air", false);
                        c.setResourceLocation(true);
                        return c;
                    },
                    false
            );
            blacklistGroup.setAddDefault(() -> AIR_ID);
            blacklistGroup.setOnUpdate(list -> setThreadBlacklist(threadId, list));
            threadGroup.addConfigurators(blacklistGroup);
        }
    }

    public String getThreadIdleText(int threadId) {
        return threadIdleTexts.get(threadId);
    }

    public void setThreadIdleText(int threadId, String text) {
        threadIdleTexts.set(threadId, normalizeTextOrEmpty(text));
    }

    public String getThreadRunningText(int threadId) {
        return threadRunningTexts.get(threadId);
    }

    public void setThreadRunningText(int threadId, String text) {
        threadRunningTexts.set(threadId, normalizeTextOrEmpty(text));
    }

    public String getThreadWaitingText(int threadId) {
        return threadWaitingTexts.get(threadId);
    }

    public void setThreadWaitingText(int threadId, String text) {
        threadWaitingTexts.set(threadId, normalizeTextOrEmpty(text));
    }

    public java.util.List<String> getThreadWhitelistIdsLowercase(int threadId) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (ResourceLocation id : getThreadWhitelist(threadId)) {
            result.add(id.toString().toLowerCase(java.util.Locale.ROOT));
        }
        return result;
    }

    public java.util.List<String> getThreadBlacklistIdsLowercase(int threadId) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (ResourceLocation id : getThreadBlacklist(threadId)) {
            result.add(id.toString().toLowerCase(java.util.Locale.ROOT));
        }
        return result;
    }

    private java.util.List<ResourceLocation> getThreadWhitelist(int threadId) {
        java.util.List<ResourceLocation> list = new java.util.ArrayList<>();
        String prefix = threadId + "|";
        for (String entry : threadWhitelistEntries) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (!trimmed.startsWith(prefix)) continue;
            String value = trimmed.substring(prefix.length()).trim();
            if (value.isEmpty()) continue;
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null) list.add(parsed);
        }
        return list;
    }

    private void setThreadWhitelist(int threadId, java.util.List<ResourceLocation> list) {
        String prefix = threadId + "|";
        threadWhitelistEntries.removeIf(s -> s != null && s.trim().startsWith(prefix));
        for (ResourceLocation id : list) {
            if (id == null) continue;
            threadWhitelistEntries.add(prefix + id);
        }
    }

    private java.util.List<ResourceLocation> getThreadBlacklist(int threadId) {
        java.util.List<ResourceLocation> list = new java.util.ArrayList<>();
        String prefix = threadId + "|";
        for (String entry : threadBlacklistEntries) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (!trimmed.startsWith(prefix)) continue;
            String value = trimmed.substring(prefix.length()).trim();
            if (value.isEmpty()) continue;
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null) list.add(parsed);
        }
        return list;
    }

    private void setThreadBlacklist(int threadId, java.util.List<ResourceLocation> list) {
        String prefix = threadId + "|";
        threadBlacklistEntries.removeIf(s -> s != null && s.trim().startsWith(prefix));
        for (ResourceLocation id : list) {
            if (id == null) continue;
            threadBlacklistEntries.add(prefix + id);
        }
    }

    private void syncThreadConfigCount() {
        int target = Math.max(1, maxThreads);
        while (threadIdleTexts.size() < target) threadIdleTexts.add("");
        while (threadRunningTexts.size() < target) threadRunningTexts.add("");
        while (threadWaitingTexts.size() < target) threadWaitingTexts.add("");
        while (threadIdleTexts.size() > target) threadIdleTexts.remove(threadIdleTexts.size() - 1);
        while (threadRunningTexts.size() > target) threadRunningTexts.remove(threadRunningTexts.size() - 1);
        while (threadWaitingTexts.size() > target) threadWaitingTexts.remove(threadWaitingTexts.size() - 1);

        java.util.Set<String> allowedPrefixes = new java.util.HashSet<>();
        for (int i = 0; i < target; i++) allowedPrefixes.add(i + "|");
        threadWhitelistEntries.removeIf(s -> {
            if (s == null) return true;
            String trimmed = s.trim();
            for (String p : allowedPrefixes) {
                if (trimmed.startsWith(p)) return false;
            }
            return true;
        });

        threadBlacklistEntries.removeIf(s -> {
            if (s == null) return true;
            String trimmed = s.trim();
            for (String p : allowedPrefixes) {
                if (trimmed.startsWith(p)) return false;
            }
            return true;
        });
    }

    private static String normalizeTextOrEmpty(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
