package com.lowdragmc.mbd2.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;
import com.lowdragmc.mbd2.api.pattern.TemplateMultiblockXEIData;
import com.lowdragmc.mbd2.api.registry.MultiblockXEIRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-to-client snapshot of template multiblock XEI data-pack entries.
 */
public final class STemplateMultiblockXEIDataPacket implements IPacket {
    private List<TemplateMultiblockXEIData> entries = List.of();

    public STemplateMultiblockXEIDataPacket() {
    }

    public STemplateMultiblockXEIDataPacket(Collection<TemplateMultiblockXEIData> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        CompoundTag payload = new CompoundTag();
        ListTag entryList = new ListTag();
        for (TemplateMultiblockXEIData entry : entries) {
            if (entry != null) {
                entryList.add(entry.toNBT());
            }
        }
        payload.put("entries", entryList);
        buf.writeNbt(payload);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        CompoundTag payload = buf.readNbt();
        if (payload == null) {
            entries = List.of();
            return;
        }

        List<TemplateMultiblockXEIData> decoded = new ArrayList<>();
        ListTag entryList = payload.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entryList.size(); i++) {
            decoded.add(TemplateMultiblockXEIData.fromNBT(entryList.getCompound(i)));
        }
        entries = List.copyOf(decoded);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void execute(IHandlerContext handler) {
        MultiblockXEIRegistry.setDataPackEntries(entries);
    }
}
