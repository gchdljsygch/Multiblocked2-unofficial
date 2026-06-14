package com.lowdragmc.mbd2.common.gui.factory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class EntityMachineUIFactory extends UIFactory<MBDEntityMachine> {
    public static final EntityMachineUIFactory INSTANCE = new EntityMachineUIFactory();

    public EntityMachineUIFactory() {
        super(MBD2.id("entity_machine"));
    }

    @Override
    protected ModularUI createUITemplate(MBDEntityMachine machine, Player entityPlayer) {
        return machine.createUI(entityPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    protected MBDEntityMachine readHolderFromSyncData(FriendlyByteBuf syncData) {
        var world = Minecraft.getInstance().level;
        if (world == null) return null;
        Entity entity = world.getEntity(syncData.readVarInt());
        if (entity == null) return null;
        return entity.getCapability(MBDCapabilities.CAPABILITY_MACHINE)
                .filter(MBDEntityMachine.class::isInstance)
                .map(MBDEntityMachine.class::cast)
                .orElse(null);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, MBDEntityMachine holder) {
        syncData.writeVarInt(holder.getMachineEntity().self().getId());
    }
}
