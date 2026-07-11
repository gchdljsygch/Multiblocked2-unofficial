package com.lowdragmc.mbd2.common.data;

import com.lowdragmc.mbd2.api.pattern.TemplateMultiblockXEIData;
import com.lowdragmc.mbd2.common.network.MBD2Network;
import com.lowdragmc.mbd2.common.network.packets.STemplateMultiblockXEIDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the active server data-pack snapshot and synchronizes it to clients.
 */
public final class TemplateMultiblockXEIDataManager {
    public static final TemplateMultiblockXEIDataLoader RELOAD_LISTENER =
            new TemplateMultiblockXEIDataLoader(TemplateMultiblockXEIDataManager::replaceEntries);

    private static volatile Map<ResourceLocation, TemplateMultiblockXEIData> entries = Map.of();

    private TemplateMultiblockXEIDataManager() {
    }

    /**
     * @return the active data-pack entries
     */
    public static List<TemplateMultiblockXEIData> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * Sends the current snapshot to one player, normally immediately after
     * login.
     *
     * @param player receiving server player
     */
    public static void syncToPlayer(ServerPlayer player) {
        if (player != null) {
            MBD2Network.NETWORK.sendToPlayer(new STemplateMultiblockXEIDataPacket(entries()), player);
        }
    }

    /**
     * Sends the current snapshot to every connected player.
     */
    public static void syncToAll() {
        MBD2Network.NETWORK.sendToAll(new STemplateMultiblockXEIDataPacket(entries()));
    }

    /**
     * Clears server state during shutdown.
     */
    public static void clear() {
        entries = Map.of();
    }

    private static void replaceEntries(Map<ResourceLocation, TemplateMultiblockXEIData> loadedEntries) {
        Map<ResourceLocation, TemplateMultiblockXEIData> snapshot = new LinkedHashMap<>();
        if (loadedEntries != null) {
            loadedEntries.forEach((id, data) -> {
                if (id != null && data != null) {
                    snapshot.put(id, data);
                }
            });
        }
        entries = Collections.unmodifiableMap(snapshot);

        // Resource reload application may run off the server thread. Queue the
        // packet send so the network channel observes the normal server state.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(TemplateMultiblockXEIDataManager::syncToAll);
        }
    }
}
