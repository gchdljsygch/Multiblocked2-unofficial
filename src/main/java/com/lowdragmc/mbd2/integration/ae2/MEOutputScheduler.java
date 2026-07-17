package com.lowdragmc.mbd2.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.util.ConfigInventory;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.core.mixins.ae2.InterfaceLogicAccessor;
import com.lowdragmc.mbd2.integration.ae2.trait.SerializableInterfaceLogic;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-side scheduler for long-count ME interface output.
 *
 * <p>AE2 normally processes each interface slot independently from its grid
 * tick callback. MBD interfaces can have many slots and can receive several
 * output writes during one server tick, so this scheduler collects the
 * negative interface plans, groups them by grid, and batches compatible writes
 * before touching the network. The scheduler never performs work on a
 * background thread.</p>
 *
 * <p>Requests are grouped by {@link IGrid}, then by AE key and action-source
 * identity. Keeping the action source in the grouping key preserves AE2's
 * security, priority, and statistics semantics while still coalescing multiple
 * slots belonging to the same interface.</p>
 */
@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MEOutputScheduler {
    private static final Map<MinecraftServer, ServerQueue> QUEUES = new WeakHashMap<>();

    private MEOutputScheduler() {
    }

    /**
     * Queues one negative AE2 interface plan for the current server tick.
     *
     * <p>Preconditions: this method is called from AE2's server-side grid tick,
     * {@code amount} is negative, and all supplied objects belong to the same
     * live interface. Side effects: marks the owning grid dirty and stores or
     * replaces the pending request for the target storage slot.</p>
     *
     * @return {@code true} when the request was accepted or was already queued;
     *         {@code false} when no server or grid is available
     */
    public static boolean enqueueInterfaceOutput(IGrid grid,
                                                  ConfigInventory storage,
                                                  SerializableInterfaceLogic logic,
                                                  InterfaceLogicAccessor planAccessor,
                                                  int slot,
                                                  AEKey key,
                                                  long amount,
                                                  IActionSource actionSource) {
        if (grid == null || storage == null || logic == null || planAccessor == null || key == null || actionSource == null || amount >= 0) {
            return false;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }

        return queueFor(server).enqueue(grid, storage, logic, planAccessor, slot, key, amount, actionSource);
    }

    /**
     * Removes all pending requests owned by one interface.
     *
     * <p>Side effects: removes the interface from every grid queue and clears
     * empty dirty-grid markers. This is used when an MBD machine unloads or its
     * AE2 grid changes.</p>
     *
     * @param logic interface logic being unloaded or detached
     */
    public static void removeInterface(SerializableInterfaceLogic logic) {
        if (logic == null) {
            return;
        }

        synchronized (QUEUES) {
            for (var queue : QUEUES.values()) {
                queue.removeInterface(logic);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerQueue queue;
        synchronized (QUEUES) {
            queue = QUEUES.get(server);
        }
        if (queue != null) {
            queue.flush();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (QUEUES) {
            QUEUES.remove(event.getServer());
        }
    }

    private static ServerQueue queueFor(MinecraftServer server) {
        synchronized (QUEUES) {
            return QUEUES.computeIfAbsent(server, ignored -> new ServerQueue());
        }
    }

    private static final class ServerQueue {
        private final IdentityHashMap<IGrid, GridQueue> grids = new IdentityHashMap<>();
        private final Set<GridQueue> dirtyGrids = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean flushing;

        private boolean enqueue(IGrid grid,
                                ConfigInventory storage,
                                SerializableInterfaceLogic logic,
                                InterfaceLogicAccessor planAccessor,
                                int slot,
                                AEKey key,
                                long amount,
                                IActionSource actionSource) {
            var gridQueue = grids.computeIfAbsent(grid, GridQueue::new);
            var slotKey = new SlotKey(storage, slot);
            var request = gridQueue.pending.get(slotKey);
            if (request == null) {
                request = new PendingRequest(storage, logic, planAccessor, slot, key, amount, actionSource);
                gridQueue.pending.put(slotKey, request);
            } else {
                request.update(logic, planAccessor, slot, key, amount, actionSource);
            }

            if (!gridQueue.dirty) {
                gridQueue.dirty = true;
                dirtyGrids.add(gridQueue);
            }
            return true;
        }

        private void flush() {
            if (flushing) {
                return;
            }

            flushing = true;
            try {
                while (!dirtyGrids.isEmpty()) {
                    var current = dirtyGrids.toArray(GridQueue[]::new);
                    for (var gridQueue : current) {
                        if (!dirtyGrids.remove(gridQueue)) {
                            continue;
                        }
                        gridQueue.dirty = false;
                        var requests = gridQueue.takePending();
                        if (!requests.isEmpty()) {
                            gridQueue.flush(requests);
                        }
                        if (gridQueue.pending.isEmpty()) {
                            grids.remove(gridQueue.grid);
                        }
                    }
                }
            } finally {
                flushing = false;
            }
        }

        private void removeInterface(SerializableInterfaceLogic logic) {
            for (var iterator = grids.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                var gridQueue = entry.getValue();
                gridQueue.pending.values().removeIf(request -> request.logic == logic);
                if (gridQueue.pending.isEmpty()) {
                    dirtyGrids.remove(gridQueue);
                    iterator.remove();
                }
            }
        }
    }

    private static final class GridQueue {
        private final IGrid grid;
        private final Map<SlotKey, PendingRequest> pending = new LinkedHashMap<>();
        private boolean dirty;

        private GridQueue(IGrid grid) {
            this.grid = grid;
        }

        private List<PendingRequest> takePending() {
            if (pending.isEmpty()) {
                return List.of();
            }
            var requests = new ArrayList<>(pending.values());
            pending.clear();
            return requests;
        }

        private void flush(List<PendingRequest> requests) {
            var batchedLogics = Collections.newSetFromMap(new IdentityHashMap<SerializableInterfaceLogic, Boolean>());
            for (var request : requests) {
                if (batchedLogics.add(request.logic)) {
                    request.logic.beginStorageBatch();
                }
            }

            try {
                var batches = new LinkedHashMap<BatchKey, List<PendingRequest>>();
                for (var request : requests) {
                    batches.computeIfAbsent(new BatchKey(request.key, request.actionSource), ignored -> new ArrayList<>()).add(request);
                }

                for (var batchEntry : batches.entrySet()) {
                    flushBatch(batchEntry.getKey(), batchEntry.getValue());
                }
            } finally {
                for (var logic : batchedLogics) {
                    logic.endStorageBatch();
                }
            }
        }

        private void flushBatch(BatchKey batchKey, List<PendingRequest> requests) {
            var validRequests = new ArrayList<PendingRequest>(requests.size());
            long total = 0;
            for (var request : requests) {
                if (!request.isStillValid()) {
                    request.replan();
                    continue;
                }
                validRequests.add(request);
                total = addSaturated(total, request.amount);
            }

            if (total <= 0 || validRequests.isEmpty()) {
                return;
            }

            long inserted;
            try {
                var networkStorage = grid.getStorageService().getInventory();
                var energyService = grid.getEnergyService();
                inserted = StorageHelper.poweredInsert(energyService, networkStorage, batchKey.key, total, batchKey.actionSource);
            } catch (RuntimeException exception) {
                MBD2.LOGGER.debug("ME output batch failed for grid {}", grid, exception);
                return;
            }

            if (inserted <= 0) {
                return;
            }

            long remaining = inserted;
            for (var request : validRequests) {
                if (remaining <= 0) {
                    break;
                }

                var current = request.storage.getStack(request.slot);
                if (!request.key.matches(current)) {
                    request.replan();
                    continue;
                }

                var amount = Math.min(remaining, Math.min(request.amount, current.amount()));
                if (amount <= 0) {
                    request.replan();
                    continue;
                }

                var extracted = request.storage.extract(request.slot, request.key, amount, Actionable.MODULATE);
                remaining -= extracted;
                if (extracted < amount) {
                    request.replan();
                    MBD2.LOGGER.warn("ME output batch extracted {} of {} from interface slot {}", extracted, amount, request.slot);
                }
            }
        }

        private static long addSaturated(long left, long right) {
            if (Long.MAX_VALUE - left < right) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }
    }

    private static final class PendingRequest {
        private final ConfigInventory storage;
        private SerializableInterfaceLogic logic;
        private InterfaceLogicAccessor planAccessor;
        private int slot;
        private AEKey key;
        private long amount;
        private IActionSource actionSource;

        private PendingRequest(ConfigInventory storage,
                               SerializableInterfaceLogic logic,
                               InterfaceLogicAccessor planAccessor,
                               int slot,
                               AEKey key,
                               long amount,
                               IActionSource actionSource) {
            this.storage = storage;
            this.update(logic, planAccessor, slot, key, amount, actionSource);
        }

        private void update(SerializableInterfaceLogic logic,
                            InterfaceLogicAccessor planAccessor,
                            int slot,
                            AEKey key,
                            long amount,
                            IActionSource actionSource) {
            this.logic = logic;
            this.planAccessor = planAccessor;
            this.slot = slot;
            this.key = key;
            this.amount = amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount;
            this.actionSource = actionSource;
        }

        private boolean isStillValid() {
            var current = storage.getStack(slot);
            return key.matches(current) && current.amount() >= amount;
        }

        private void replan() {
            planAccessor.mbd2$updatePlan(slot);
        }
    }

    private static final class SlotKey {
        private final ConfigInventory storage;
        private final int slot;

        private SlotKey(ConfigInventory storage, int slot) {
            this.storage = storage;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SlotKey slotKey)) {
                return false;
            }
            return storage == slotKey.storage && slot == slotKey.slot;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(storage) + slot;
        }
    }

    private static final class BatchKey {
        private final AEKey key;
        private final IActionSource actionSource;

        private BatchKey(AEKey key, IActionSource actionSource) {
            this.key = key;
            this.actionSource = actionSource;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BatchKey batchKey)) {
                return false;
            }
            return key.equals(batchKey.key) && actionSource == batchKey.actionSource;
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + System.identityHashCode(actionSource);
        }
    }
}
