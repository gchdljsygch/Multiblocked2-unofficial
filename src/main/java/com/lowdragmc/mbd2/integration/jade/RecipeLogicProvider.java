package com.lowdragmc.mbd2.integration.jade;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineJadeTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.impl.ui.IconElement;
import snownee.jade.impl.ui.ProgressElement;
import snownee.jade.impl.ui.ProgressStyle;
import snownee.jade.overlay.IconUI;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Jade provider that displays machine recipe status, progress, fuel, and custom tooltip lines.
 */
public class RecipeLogicProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        var optionalMachine = IMachine.ofMachine(blockAccessor.getBlockEntity());
        if (optionalMachine.isEmpty()) return;
        var rawMachine = optionalMachine.get();
        if (rawMachine instanceof MBDMachine machine) {
            var event = new MachineJadeTooltipEvent(machine, blockAccessor.getPlayer(), getUid());
            MinecraftForge.EVENT_BUS.post(event.postCustomEvent());
            for (var line : event.getTooltipLines()) {
                addTooltipLine(tooltip, line);
            }
            if (event.isCanceled()) {
                return;
            }
        }
        var data = blockAccessor.getServerData();
        if (data.contains("recipe_logic")) {
            data = data.getCompound("recipe_logic");
            var status = data.getString("status");
            tooltip.add(Component.translatable("recipe_logic.status." + status.toLowerCase()));
            var boxStyle = new BoxStyle();
            boxStyle.borderColor = ColorPattern.GRAY.color;
            boxStyle.borderWidth = 1;
            if (data.contains("duration")) {
                var progress = data.getInt("progress");
                var duration = data.getInt("duration");
                tooltip.add(tooltip.getElementHelper().progress(progress * 1f / duration,
                        Component.literal("%.2fs / %.2fs".formatted(progress / 20f, duration / 20f)).withStyle(ChatFormatting.WHITE),
                        new ProgressStyle().color(ColorPattern.GREEN.color), boxStyle, true));
            }

            if (data.contains("fuel")) {
                var fuel = data.getInt("fuel");
                var maxFuel = data.getInt("maxFuel");
                tooltip.add(new ProgressElement(fuel * 1f / maxFuel,
                        Component.literal("%.2f / %.2f ".formatted(fuel / 20f, maxFuel / 20f)).withStyle(ChatFormatting.WHITE),
                        new ProgressStyle().color(ColorPattern.ORANGE.color), boxStyle, true));
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

    private static void addTooltipLine(ITooltip tooltip, MachineJadeTooltipEvent.TooltipLine line) {
        var elements = new ArrayList<IElement>();
        var helper = tooltip.getElementHelper();
        for (var part : line.getParts()) {
            if (part instanceof MachineJadeTooltipEvent.TextPart textPart) {
                elements.add(helper.text(textPart.component()));
            } else if (part instanceof MachineJadeTooltipEvent.ItemIconPart itemPart) {
                elements.add(itemPart.small() ? helper.smallItem(itemPart.stack()) : helper.item(itemPart.stack()));
            } else if (part instanceof MachineJadeTooltipEvent.JadeIconPart iconPart) {
                parseJadeIcon(iconPart.iconName()).ifPresent(icon -> elements.add(new IconElement(icon)));
            } else if (part instanceof MachineJadeTooltipEvent.SpacerPart spacerPart) {
                elements.add(helper.spacer(spacerPart.width(), spacerPart.height()));
            }
        }
        if (!elements.isEmpty()) {
            tooltip.add(elements);
        }
    }

    private static java.util.Optional<IconUI> parseJadeIcon(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(IconUI.valueOf(iconName.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
