package com.lowdragmc.mbd2.api.pattern;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server-level runtime data for formed multiblock mappings and async pattern
 * checks.
 *
 * <p>The business goal is to map structure block positions back to their
 * controllers for invalidation, and to run lightweight pattern rechecks for
 * unformed controllers away from the main server tick. Mapping mutations should
 * occur on the server thread. Async checks run on a single daemon thread and
 * must not mutate world state directly.</p>
 */
public class MultiblockWorldSavedData extends SavedData {
    @Getter
    private final ServerLevel serverLevel;

    /**
     * Returns or creates the saved-data instance for a server level.
     *
     * @param serverLevel level whose data storage owns the instance
     * @return level-scoped multiblock runtime data
     */
    public static MultiblockWorldSavedData getOrCreate(ServerLevel serverLevel) {
        return serverLevel.getDataStorage().computeIfAbsent(tag -> new MultiblockWorldSavedData(serverLevel, tag), () -> new MultiblockWorldSavedData(serverLevel), "MBD2_multiblock");
    }

    /**
     * Store all formed multiblocks' structure info
     */
    public final Map<BlockPos, MultiblockState> mapping;
    /**
     * Structure Cache pos mapping.
     */
    public final Long2ObjectOpenHashMap<Set<MultiblockState>> structureCachePosMapping;
    /**
     * Pos Cache of multiblock.
     */
    public final LongOpenHashSet posCache = new LongOpenHashSet();

    /**
     * Creates empty runtime data for a level.
     *
     * @param serverLevel owning server level
     */
    private MultiblockWorldSavedData(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.mapping = new Object2ObjectOpenHashMap<>();
        this.structureCachePosMapping = new Long2ObjectOpenHashMap<>();
    }

    /**
     * Loads runtime data from NBT.
     *
     * <p>The current implementation does not persist active mappings; structures
     * are rediscovered after load.</p>
     *
     * @param serverLevel owning server level
     * @param tag saved data tag
     */
    private MultiblockWorldSavedData(ServerLevel serverLevel, CompoundTag tag) {
        this(serverLevel);
    }

    /**
     * Returns formed controller states that include a block position.
     *
     * @param pos structure block position
     * @return array of states whose position cache contains {@code pos}; empty
     * when none are mapped
     */
    public MultiblockState[] getControllerInPos(BlockPos pos) {
        return structureCachePosMapping.getOrDefault(pos.asLong(), Collections.emptySet()).toArray(MultiblockState[]::new);
    }

    /**
     * Adds a formed multiblock state to controller and position mappings.
     *
     * <p>Side effects: stores the state under its controller position and indexes
     * every cached structure position for block-change invalidation.</p>
     *
     * @param state formed multiblock state with a populated position cache
     */
    public void addMapping(MultiblockState state) {
        this.mapping.put(state.controllerPos, state);
        for (var blockPos : state.getCache()) {
            structureCachePosMapping.computeIfAbsent(blockPos.asLong(), c -> new HashSet<>()).add(state);
        }
    }

    /**
     * Removes a multiblock state from controller and position mappings.
     *
     * <p>Side effects: removes empty position buckets after the state is removed.</p>
     *
     * @param state state to remove
     */
    public void removeMapping(MultiblockState state) {
        this.mapping.remove(state.controllerPos);
        var iterator = structureCachePosMapping.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var stateSet = entry.getValue();
            stateSet.remove(state);
            if (stateSet.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * Saves persistent data.
     *
     * <p>The runtime mapping is intentionally not serialized; controllers rebuild
     * their mappings after world load.</p>
     *
     * @param compound destination tag
     * @return unchanged destination tag
     */
    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag compound) {
        return compound;
    }

    // ********************************* thread for searching ********************************* //
    private final CopyOnWriteArrayList<IMultiController> controllers = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executorService;
    private final static ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
            .setNameFormat("MBD2 Multiblock Async Thread-%d")
            .setDaemon(true)
            .build();
    private static final ThreadLocal<Boolean> IN_SERVICE = ThreadLocal.withInitial(() -> false);
    @Getter
    private long periodID = Long.MIN_VALUE;

    /**
     * Starts the single async pattern-check executor if needed.
     *
     * <p>Side effects: creates a daemon scheduled executor and runs
     * {@link #searchingTask()} every 250 ms, roughly every five server ticks.</p>
     */
    public void createExecutorService() {
        if (executorService != null && !executorService.isShutdown()) return;
        executorService = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
        executorService.scheduleAtFixedRate(this::searchingTask, 0, 250, TimeUnit.MILLISECONDS); // per 5 tick
    }

    /**
     * Registers a controller for async pattern checks.
     *
     * <p>Preconditions: controller async checks must be read-only until they
     * schedule server-thread work. Side effects: starts the executor when this is
     * the first registered controller. Controllers requiring a catalyst are not
     * registered because catalyst state gates formation.</p>
     *
     * @param controller controller to check periodically
     */
    public void addAsyncLogic(IMultiController controller) {
        if (controller instanceof MBDMultiblockMachine machine) {
            // if it requires catalyst, don't add it to async logic.
            if (machine.getDefinition().multiblockSettings().catalyst().isEnable()) return;
        }
        if (controllers.contains(controller)) return;
        controllers.add(controller);
        createExecutorService();
    }

    /**
     * Unregisters a controller from async pattern checks.
     *
     * <p>Side effects: stops the executor when no controllers remain.</p>
     *
     * @param controller controller to remove
     */
    public void removeAsyncLogic(IMultiController controller) {
        if (controllers.contains(controller)) {
            controllers.remove(controller);
            if (controllers.isEmpty()) {
                releaseExecutorService();
            }
        }
    }

    /**
     * Runs one async check pass over registered controllers.
     *
     * <p>Thread safety: executes on the dedicated async executor. It sets a
     * thread-local marker for predicates/logic that need to know they are in the
     * service thread. Side effects: invokes
     * {@link IMultiController#asyncCheckPattern(long)} for each registered
     * controller and increments {@link #periodID}.</p>
     */
    private void searchingTask() {
        try {
            if (Platform.isServerNotSafe()) return;
            IN_SERVICE.set(true);
            for (var controller : controllers) {
                controller.asyncCheckPattern(periodID);
            }
        } catch (Throwable e) {
            MBD2.LOGGER.error("asyncThreadLogic error: {}", e.getMessage());
        } finally {
            IN_SERVICE.set(false);
        }
        periodID++;
    }

    /**
     * Returns whether the current thread is the multiblock async service thread.
     *
     * @return {@code true} only while an async search task is running and the
     * server is safe to query
     */
    public static boolean isThreadService() {
        return IN_SERVICE.get() && !Platform.isServerNotSafe();
    }

    /**
     * Stops the async executor immediately.
     *
     * <p>Side effects: calls {@link ScheduledExecutorService#shutdownNow()} and
     * clears the executor reference. Registered controller list is not cleared.</p>
     */
    public void releaseExecutorService() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
    }

}
