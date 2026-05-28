package com.lowdragmc.mbd2.integration.jade;

import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTrait;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.impl.ui.ProgressElement;
import snownee.jade.impl.ui.ProgressStyle;

import java.util.Locale;
import java.util.Objects;

public class RecipeThreadRecipeLogicDataProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    private static final ResourceLocation UID = Objects.requireNonNull(ResourceLocation.tryParse("mbd2:recipe_thread_hud"));
    private static final ResourceLocation MBD2_RECIPE_LOGIC_UID = Objects.requireNonNull(ResourceLocation.tryParse("mbd2:recipe_logic_provider"));

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        IMachine.ofMachine(blockAccessor.getBlockEntity()).ifPresent(rawMachine -> {
            if (!rawMachine.runRecipeLogic()) return;

            if (!(rawMachine instanceof MBDMachine mbdMachine)) return;
            RecipeThreadTrait trait = RecipeThreadTrait.get(mbdMachine);
            if (trait == null) return;

            CompoundTag traitTag = new CompoundTag();
            int maxThreads = trait.getMaxThreads();
            int runningThreads = trait.getRunningThreadsCount();
            int waitingThreads = trait.getWaitingThreadsCount();
            int idleThreads = Math.max(0, maxThreads - runningThreads - waitingThreads);
            traitTag.putInt("maxThreads", maxThreads);
            traitTag.putInt("runningThreads", runningThreads);
            traitTag.putInt("waitingThreads", waitingThreads);
            traitTag.putInt("idleThreads", idleThreads);

            RecipeLogic summaryLogic = trait.getRecipeLogicForJadeDisplay();
            CompoundTag summaryTag = new CompoundTag();
            String status = summaryLogic.getStatus() == null ? "IDLE" : summaryLogic.getStatus().name();
            summaryTag.putString("status", status);
            if (summaryLogic.getDuration() > 0) {
                summaryTag.putInt("progress", summaryLogic.getProgress());
                summaryTag.putInt("duration", summaryLogic.getDuration());
            }
            traitTag.put("summary", summaryTag);

            ListTag threads = new ListTag();
            int index = 0;
            for (RecipeLogic logic : trait.getThreadLogicsForJadeDetail()) {
                CompoundTag t = new CompoundTag();
                t.putInt("index", index++);
                String s = logic.getStatus() == null ? "IDLE" : logic.getStatus().name();
                t.putString("status", s);
                if (logic.getDuration() > 0) {
                    t.putInt("progress", logic.getProgress());
                    t.putInt("duration", logic.getDuration());
                }
                if (logic.isWaiting()) {
                    var reason = logic.getWaitingReason();
                    if (reason != null) {
                        String json = Component.Serializer.toJson(reason);
                        if (json != null) {
                            t.putString("waitingReason", json);
                        }
                    }
                }
                threads.add(t);
            }
            traitTag.put("threads", threads);

            RecipeLogic fuelSource = null;
            for (RecipeLogic logic : trait.getThreadLogicsForJadeDetail()) {
                if (logic.isWorking() && logic.needFuel() && logic.getFuelMaxTime() > 0) {
                    fuelSource = logic;
                    break;
                }
            }
            if (fuelSource == null) {
                for (RecipeLogic logic : trait.getThreadLogicsForJadeDetail()) {
                    if (logic.needFuel() && logic.getFuelMaxTime() > 0) {
                        fuelSource = logic;
                        break;
                    }
                }
            }

            if (fuelSource != null) {
                CompoundTag fuel = new CompoundTag();
                fuel.putInt("fuel", fuelSource.getFuelTime());
                fuel.putInt("maxFuel", fuelSource.getFuelMaxTime());
                traitTag.put("fuel", fuel);
            }

            data.put("recipe_thread", traitTag);
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        if (IMachine.ofMachine(blockAccessor.getBlockEntity()).isEmpty()) return;

        CompoundTag data = blockAccessor.getServerData();
        if (!data.contains("recipe_thread")) return;

        CompoundTag recipeThread = data.getCompound("recipe_thread");
        if (!recipeThread.contains("summary")) return;
        CompoundTag summary = recipeThread.getCompound("summary");
        String status = Objects.toString(summary.getString("status"), "IDLE");

        int running = recipeThread.getInt("runningThreads");
        int waiting = recipeThread.getInt("waitingThreads");
        int idle = recipeThread.getInt("idleThreads");

        tooltip.remove(MBD2_RECIPE_LOGIC_UID);

        boolean shift = blockAccessor.getPlayer() != null && blockAccessor.getPlayer().isShiftKeyDown();
        if (!shift) {
            Component base = Component.translatable("recipe_logic.status." + status.toLowerCase(Locale.ROOT));

            Component progressText = Component.empty();
            if (summary.contains("duration")) {
                int progress = summary.getInt("progress");
                int duration = summary.getInt("duration");
                if (duration > 0) {
                    int percent = (int) (progress * 100f / duration);
                    progressText = Component.literal(" " + percent + "%");
                }
            }

            tooltip.add(Component.translatable("mbd2.jade.thread_summary", running, waiting, idle, base, progressText)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        var boxStyle = new BoxStyle();
        boxStyle.borderColor = ColorPattern.GRAY.color;
        boxStyle.borderWidth = 1;

        if (recipeThread.contains("threads")) {
            ListTag threads = recipeThread.getList("threads", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < threads.size(); i++) {
                CompoundTag t = threads.getCompound(i);
                int index = t.getInt("index");
                String s = Objects.toString(t.getString("status"), "IDLE");
                Component statusText = Component.translatable("recipe_logic.status." + s.toLowerCase(Locale.ROOT));
                tooltip.add(Component.translatable("mbd2.jade.thread_line", index, statusText).withStyle(ChatFormatting.GRAY));

                if (t.contains("duration")) {
                    int progress = t.getInt("progress");
                    int duration = t.getInt("duration");
                    if (duration > 0) {
                        tooltip.add(tooltip.getElementHelper().progress(progress * 1f / duration,
                                Component.literal(String.format(Locale.ROOT, "%.2fs / %.2fs", progress / 20f, duration / 20f)).withStyle(ChatFormatting.WHITE),
                                new ProgressStyle().color(ColorPattern.GREEN.color), boxStyle, true));
                    }
                }

                if (t.contains("waitingReason")) {
                    Component reason = Component.Serializer.fromJson(t.getString("waitingReason"));
                    if (reason != null) {
                        tooltip.add(reason);
                    }
                }
            }
        }

        if (recipeThread.contains("fuel")) {
            CompoundTag fuelTag = recipeThread.getCompound("fuel");
            int fuel = fuelTag.getInt("fuel");
            int maxFuel = fuelTag.getInt("maxFuel");
            if (maxFuel > 0) {
                tooltip.add(new ProgressElement(fuel * 1f / maxFuel,
                        Component.literal(String.format(Locale.ROOT, "%.2f / %.2f ", fuel / 20f, maxFuel / 20f)).withStyle(ChatFormatting.WHITE),
                        new ProgressStyle().color(ColorPattern.ORANGE.color), boxStyle, true));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 5000;
    }
}
