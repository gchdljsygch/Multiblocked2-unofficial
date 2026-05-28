package com.lowdragmc.mbd2.common.autobuild;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.lowdragmc.mbd2.utils.PatternAutoBuildPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlowAutoBuildScheduler {
    private static final ConcurrentHashMap<UUID, Task> TASKS = new ConcurrentHashMap<>();

    private SlowAutoBuildScheduler() {
    }

    public static void replace(ServerPlayer player,
                               ResourceKey<Level> dimension,
                               Iterable<PatternAutoBuildPlacement> placements,
                               @Nullable IItemHandler boundItemHandler,
                               @Nullable IFluidHandler boundFluidHandler) {
        ArrayDeque<PatternAutoBuildPlacement> queue = new ArrayDeque<>();
        for (PatternAutoBuildPlacement placement : placements) {
            if (placement != null) queue.add(placement);
        }
        TASKS.put(player.getUUID(), new Task(dimension, queue, boundItemHandler, boundFluidHandler));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Thread.currentThread().getThreadGroup() != SidedThreadGroups.SERVER) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int blocksPerTick = ConfigHolder.slowBuildBlocksPerTick;
        if (blocksPerTick <= 0) blocksPerTick = 5;

        for (var iter = TASKS.entrySet().iterator(); iter.hasNext(); ) {
            var entry = iter.next();
            UUID playerId = entry.getKey();
            Task task = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                iter.remove();
                continue;
            }
            if (player.level().dimension() != task.dimension) {
                iter.remove();
                continue;
            }

            int placed = 0;
            while (placed < blocksPerTick && !task.queue.isEmpty()) {
                PatternAutoBuildPlacement placement = task.queue.pollFirst();
                if (placement == null || placement.pos == null) continue;
                AutoBuildPlacementExecutor.executePlacement(player, player.level(), placement, task.blocks, task.boundItemHandler, task.boundFluidHandler);
                placed++;
            }
            if (task.queue.isEmpty()) {
                iter.remove();
            }
        }
    }

    private static final class Task {
        private final ResourceKey<Level> dimension;
        private final ArrayDeque<PatternAutoBuildPlacement> queue;
        private final Map<BlockPos, Object> blocks = new HashMap<>();
        private final IItemHandler boundItemHandler;
        private final IFluidHandler boundFluidHandler;

        private Task(ResourceKey<Level> dimension,
                     ArrayDeque<PatternAutoBuildPlacement> queue,
                     @Nullable IItemHandler boundItemHandler,
                     @Nullable IFluidHandler boundFluidHandler) {
            this.dimension = dimension;
            this.queue = queue;
            this.boundItemHandler = boundItemHandler;
            this.boundFluidHandler = boundFluidHandler;
        }
    }
}
