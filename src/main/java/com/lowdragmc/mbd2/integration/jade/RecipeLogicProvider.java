package com.lowdragmc.mbd2.integration.jade;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMachine;
import net.minecraft.nbt.CompoundTag;
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

public class RecipeLogicProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        if (IMachine.ofMachine(blockAccessor.getBlockEntity()).isEmpty()) return;
        var data = blockAccessor.getServerData();
        if (data.contains("recipe_logic")) {
            data = data.getCompound("recipe_logic");
            var status = data.getString("status");
            var progress = data.getInt("progress");
            var duration = data.getInt("duration");
            tooltip.add(Component.translatable("recipe_logic.status." + status.toLowerCase()));
            tooltip.add(new ProgressElement(progress * 1f / duration,
                    Component.literal("%fs / %fs".formatted(progress / 20f, duration / 20f)),
                    new ProgressStyle().color(ColorPattern.T_GREEN.color), new BoxStyle(), true));
            if (data.contains("fuel")) {
                var fuel = data.getInt("fuel");
                var maxFuel = data.getInt("maxFuel");
                tooltip.add(new ProgressElement(fuel * 1f / maxFuel,
                        Component.literal("%d / %d".formatted(fuel, maxFuel)),
                        new ProgressStyle().color(ColorPattern.T_ORANGE.color), new BoxStyle(), true));
            }
            if (data.contains("waitingReason")) {
                var reason = Component.Serializer.fromJson(data.getString("waitingReason"));
                tooltip.add(reason);
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        IMachine.ofMachine(blockAccessor.getBlockEntity()).ifPresent(machine -> {
            var tag = new CompoundTag();
            if (!machine.runRecipeLogic()) return;
            var recipeLogic = machine.getRecipeLogic();
            tag.putString("status", recipeLogic.getStatus().name());
            if (recipeLogic.getDuration() > 0) {
                tag.putInt("progress", recipeLogic.getProgress());
                tag.putInt("duration", recipeLogic.getDuration());
            }
            if (recipeLogic.needFuel()) {
                tag.putInt("fuel", recipeLogic.getFuelTime());
                tag.putInt("maxFuel", recipeLogic.getFuelMaxTime());
            }
            if (recipeLogic.isWaiting() && recipeLogic.getWaitingReason() != null) {
                tag.putString("waitingReason", Component.Serializer.toJson(recipeLogic.getWaitingReason()));
            }
            data.put("recipe_logic", tag);
        });
    }

    @Override
    public ResourceLocation getUid() {
        return MBD2.id("recipe_logic_provider");
    }
}
