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

/**
 * LowDragLib UI factory for entity-backed MBD machines.
 *
 * <p>The factory serializes the owning entity id so the client can resolve the entity in its local level and read the
 * MBD machine capability before building the UI. The entity must still be tracked by the client when the UI opens;
 * missing client level, entity, or machine capability resolves to {@code null}.</p>
 */
public class EntityMachineUIFactory extends UIFactory<MBDEntityMachine> {
    /**
     * Shared factory instance registered under the {@code mbd2:entity_machine} UI id.
     */
    public static final EntityMachineUIFactory INSTANCE = new EntityMachineUIFactory();

    /**
     * Creates the entity-machine UI factory with its stable network id.
     */
    public EntityMachineUIFactory() {
        super(MBD2.id("entity_machine"));
    }

    /**
     * Delegates UI construction to the entity machine runtime.
     *
     * @param machine      entity-backed machine holder resolved for this UI
     * @param entityPlayer player opening the UI
     * @return modular UI created by the machine, or {@code null} when the machine has no UI
     */
    @Override
    protected ModularUI createUITemplate(MBDEntityMachine machine, Player entityPlayer) {
        return machine.createUI(entityPlayer);
    }

    /**
     * Resolves a client-side entity machine from synchronized entity id data.
     *
     * @param syncData buffer containing the entity id written by {@link #writeHolderToSyncData(FriendlyByteBuf,
     *                 MBDEntityMachine)}
     * @return resolved entity machine, or {@code null} when unavailable
     */
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

    /**
     * Writes the owning entity id needed to resolve the holder on the client.
     *
     * @param syncData destination sync buffer
     * @param holder   entity machine whose owning entity id is serialized
     */
    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, MBDEntityMachine holder) {
        syncData.writeVarInt(holder.getMachineEntity().self().getId());
    }
}
