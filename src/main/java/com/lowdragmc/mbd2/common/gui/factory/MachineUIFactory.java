package com.lowdragmc.mbd2.common.gui.factory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * LowDragLib UI factory for block-backed MBD machines.
 *
 * <p>The factory serializes a machine holder by block position so the client can resolve the same
 * {@link MBDMachine} from its local level before building the UI. The holder must still exist on both sides while the
 * UI opens; missing client level or block entity resolution returns {@code null} and prevents template creation.</p>
 */
public class MachineUIFactory extends UIFactory<MBDMachine> {
    /**
     * Shared factory instance registered under the {@code mbd2:machine} UI id.
     */
    public static final MachineUIFactory INSTANCE = new MachineUIFactory();

    /**
     * Creates the machine UI factory with its stable network id.
     */
    public MachineUIFactory() {
        super(MBD2.id("machine"));
    }

    /**
     * Delegates UI construction to the machine runtime.
     *
     * @param machine      machine holder resolved for this UI
     * @param entityPlayer player opening the UI
     * @return modular UI created by the machine, or {@code null} when the machine has no UI
     */
    @Override
    protected ModularUI createUITemplate(MBDMachine machine, Player entityPlayer) {
        return machine.createUI(entityPlayer);
    }

    /**
     * Resolves a client-side machine holder from synchronized block position data.
     *
     * @param syncData buffer containing the block position written by {@link #writeHolderToSyncData(FriendlyByteBuf,
     *                 MBDMachine)}
     * @return resolved MBD machine, or {@code null} when the client world or holder is unavailable
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    protected MBDMachine readHolderFromSyncData(FriendlyByteBuf syncData) {
        var world = Minecraft.getInstance().level;
        if (world == null) return null;
        return IMachine.ofMachine(world, syncData.readBlockPos()).filter(MBDMachine.class::isInstance).map(MBDMachine.class::cast).orElse(null);
    }

    /**
     * Writes the machine block position needed to resolve the holder on the client.
     *
     * @param syncData destination sync buffer
     * @param holder   machine whose position is serialized
     */
    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, MBDMachine holder) {
        syncData.writeBlockPos(holder.getPos());
    }
}
