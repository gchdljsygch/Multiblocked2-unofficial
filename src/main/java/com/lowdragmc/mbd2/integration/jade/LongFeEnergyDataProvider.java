package com.lowdragmc.mbd2.integration.jade;

import com.lowdragmc.mbd2.api.capability.energy.LongFeEnergyCapability;
import com.lowdragmc.mbd2.utils.EnergyFormatUtil;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.Identifiers;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.impl.ui.ProgressElement;
import snownee.jade.impl.ui.ProgressStyle;

import java.util.Objects;

public class LongFeEnergyDataProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    private static final ResourceLocation UID = Objects.requireNonNull(ResourceLocation.tryParse("mbd2:long_fe_energy_hud"));

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        BlockEntity be = blockAccessor.getBlockEntity();
        be.getCapability(LongFeEnergyCapability.CAPABILITY, null).resolve().ifPresent(container -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("stored", container.getEnergyStored());
            tag.putLong("cap", container.getEnergyCapacity());
            tag.putLong("maxR", container.getMaxReceivePerTick());
            tag.putLong("maxE", container.getMaxExtractPerTick());
            data.put("long_fe", tag);
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        CompoundTag data = blockAccessor.getServerData();
        if (!data.contains("long_fe")) return;

        CompoundTag tag = data.getCompound("long_fe");
        long stored = tag.getLong("stored");
        long cap = tag.getLong("cap");
        long maxR = tag.getLong("maxR");
        long maxE = tag.getLong("maxE");

        boolean shift = blockAccessor.getPlayer() != null && blockAccessor.getPlayer().isShiftKeyDown();
        tooltip.remove(Identifiers.UNIVERSAL_ENERGY_STORAGE);

        var boxStyle = new BoxStyle();
        boxStyle.borderColor = ColorPattern.GRAY.color;
        boxStyle.borderWidth = 1;

        float ratio = 0f;
        if (cap > 0) {
            ratio = (float) Math.min(1d, Math.max(0d, stored * 1d / cap));
        }

        String storedText = shift ? Long.toString(stored) : EnergyFormatUtil.formatEnergy(stored);
        String capText = shift ? Long.toString(cap) : EnergyFormatUtil.formatEnergy(cap);
        Component barText = Component.literal(storedText + "FE/" + capText + "FE").withStyle(ChatFormatting.WHITE);

        int baseIndex = Math.min(1, tooltip.size());
        tooltip.add(baseIndex, new ProgressElement(ratio, barText, new ProgressStyle().color(ColorPattern.RED.color), boxStyle, true));

        String maxRText = shift ? Long.toString(maxR) : EnergyFormatUtil.formatEnergy(maxR);
        String maxEText = shift ? Long.toString(maxE) : EnergyFormatUtil.formatEnergy(maxE);
        tooltip.add(baseIndex + 1, Component.translatable(shift ? "mbd2.jade.long_fe_limits.raw" : "mbd2.jade.long_fe_limits", maxRText, maxEText)
                .withStyle(ChatFormatting.GRAY));

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
