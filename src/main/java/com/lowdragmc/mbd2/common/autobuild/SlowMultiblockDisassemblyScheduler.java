package com.lowdragmc.mbd2.common.autobuild;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.item.MultiblockDisassemblyToolItem;
import com.lowdragmc.mbd2.config.ConfigHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side queue that spreads multiblock disassembly across multiple ticks.
 */
@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlowMultiblockDisassemblyScheduler {
    private static final ConcurrentHashMap<UUID, Task> TASKS = new ConcurrentHashMap<>();

    private SlowMultiblockDisassemblyScheduler() {
    }

    public static void replace(ServerPlayer player,
                               ResourceKey<Level> dimension,
                               Iterable<MultiblockDisassemblyToolItem.DisassemblyEntry> entries,
                               @Nullable IItemHandler boundItemHandler,
                               @Nullable IFluidHandler boundFluidHandler) {
        ArrayDeque<MultiblockDisassemblyToolItem.DisassemblyEntry> queue = new ArrayDeque<>();
        for (MultiblockDisassemblyToolItem.DisassemblyEntry entry : entries) {
            if (entry != null) {
                queue.add(entry);
            }
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
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Task task = entry.getValue();
            if (player == null || player.level().dimension() != task.dimension) {
                iter.remove();
                continue;
            }
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                iter.remove();
                continue;
            }

            int removed = 0;
            while (removed < blocksPerTick && !task.queue.isEmpty()) {
                MultiblockDisassemblyToolItem.DisassemblyEntry disassemblyEntry = task.queue.pollFirst();
                if (disassemblyEntry == null) continue;
                MultiblockDisassemblyToolItem.executeDisassemblyEntry(player, serverLevel, disassemblyEntry,
                        task.boundItemHandler, task.boundFluidHandler);
                removed++;
            }
            if (task.queue.isEmpty()) {
                iter.remove();
            }
        }
    }

    private static final class Task {
        private final ResourceKey<Level> dimension;
        private final ArrayDeque<MultiblockDisassemblyToolItem.DisassemblyEntry> queue;
        private final IItemHandler boundItemHandler;
        private final IFluidHandler boundFluidHandler;

        private Task(ResourceKey<Level> dimension,
                     ArrayDeque<MultiblockDisassemblyToolItem.DisassemblyEntry> queue,
                     @Nullable IItemHandler boundItemHandler,
                     @Nullable IFluidHandler boundFluidHandler) {
            this.dimension = dimension;
            this.queue = queue;
            this.boundItemHandler = boundItemHandler;
            this.boundFluidHandler = boundFluidHandler;
        }
    }
}
