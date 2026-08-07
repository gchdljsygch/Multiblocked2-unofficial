package com.lowdragmc.mbd2.common.block;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.blockentity.MachineBlockEntity;
import com.lowdragmc.mbd2.common.blockentity.MachineBlockEntityTicker;
import com.lowdragmc.mbd2.performance.StressTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stress tests the actual MBD block-entity ticker bridge and machine lifecycle.
 */
@Tag("performance")
class MachineRuntime100kStressTest {

    @Test
    void ticksAndCyclesOneHundredThousandMachineBlockEntities() {
        StressTestSupport.requireStressScale();
        var holders = createHolders();
        warmUpTickBridge(holders);

        StressTestSupport.measure("block-entity-ticker-dispatch", 1, holders.length, () -> {
            for (MachineBlockEntity holder : holders) {
                MachineBlockEntityTicker.tick(false, holder);
            }
        });

        StressTestSupport.measure("block-entity-client-ticker-dispatch", 1, holders.length, () -> {
            for (MachineBlockEntity holder : holders) {
                MachineBlockEntityTicker.tick(true, holder);
            }
        });

        long serverTicks = 0;
        long clientTicks = 0;
        for (MachineBlockEntity holder : holders) {
            serverTicks += machine(holder).serverTicks;
            clientTicks += machine(holder).clientTicks;
        }
        assertEquals(holders.length, serverTicks, "the ticker bridge must dispatch exactly one server tick per holder");
        assertEquals(holders.length, clientTicks, "the ticker bridge must dispatch exactly one client tick per holder");

        StressTestSupport.measure("block-entity-chunk-load", 1, holders.length, () -> {
            for (MachineBlockEntity holder : holders) {
                holder.clearRemoved();
            }
        });
        StressTestSupport.measure("block-entity-chunk-unload", 1, holders.length, () -> {
            for (MachineBlockEntity holder : holders) {
                holder.onChunkUnloaded();
            }
        });

        long loads = 0;
        long unloads = 0;
        for (MachineBlockEntity holder : holders) {
            var machine = machine(holder);
            loads += machine.loads;
            unloads += machine.chunkUnloads;
        }
        assertEquals(holders.length, loads, "clearRemoved must forward every load callback to its machine");
        assertEquals(holders.length, unloads, "chunk unloading must forward every unload callback to its machine");
    }

    @Test
    void dispatchesOneHundredThousandForgeModEvents() {
        StressTestSupport.requireStressScale();
        var received = new AtomicInteger();
        Consumer<StressModEvent> listener = event -> received.incrementAndGet();
        MinecraftForge.EVENT_BUS.start();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, StressModEvent.class, listener);
        try {
            for (int index = 0; index < 4_096; index++) {
                MinecraftForge.EVENT_BUS.post(new StressModEvent());
            }
            received.set(0);

            StressTestSupport.measure("forge-mod-event-dispatch", 1, StressTestSupport.MACHINE_COUNT, () -> {
                for (int index = 0; index < StressTestSupport.MACHINE_COUNT; index++) {
                    MinecraftForge.EVENT_BUS.post(new StressModEvent());
                }
            });
            assertEquals(StressTestSupport.MACHINE_COUNT, received.get(),
                    "the registered Forge listener must observe every mod event");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    private static MachineBlockEntity[] createHolders() {
        var holders = new MachineBlockEntity[StressTestSupport.MACHINE_COUNT];
        for (int index = 0; index < holders.length; index++) {
            holders[index] = createHolder(index);
        }
        return holders;
    }

    private static MachineBlockEntity createHolder(int index) {
        return new MachineBlockEntity(null, new BlockPos(index & 0x3ff, 64, index >>> 10), null,
                holder -> new TickProbeMachine(holder, index));
    }

    private static TickProbeMachine machine(MachineBlockEntity holder) {
        return (TickProbeMachine) holder.getMetaMachine();
    }

    private static void warmUpTickBridge(MachineBlockEntity[] holders) {
        int warmupCount = Math.min(4_096, holders.length);
        for (int index = 0; index < warmupCount; index++) {
            MachineBlockEntityTicker.tick(false, holders[index]);
            MachineBlockEntityTicker.tick(true, holders[index]);
        }
        for (MachineBlockEntity holder : holders) {
            machine(holder).serverTicks = 0;
            machine(holder).clientTicks = 0;
        }
    }

    private static final class TickProbeMachine implements IMachine {

        private final IMachineBlockEntity holder;
        private final long offset;
        private final RecipeLogic recipeLogic = new RecipeLogic(this);
        private int serverTicks;
        private int clientTicks;
        private int loads;
        private int chunkUnloads;

        private TickProbeMachine(IMachineBlockEntity holder, long offset) {
            this.holder = holder;
            this.offset = offset;
        }

        @Override
        public BlockEntity getHolder() {
            return holder.self();
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Optional<Direction> getFrontFacing() {
            return Optional.empty();
        }

        @Override
        public boolean isFacingValid(Direction facing) {
            return false;
        }

        @Override
        public void setFrontFacing(Direction facing) {
        }

        @Override
        public MBDRecipeType getRecipeType() {
            return null;
        }

        @Override
        public RecipeLogic getRecipeLogic() {
            return recipeLogic;
        }

        @Override
        public Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> getRecipeCapabilitiesProxy() {
            return ImmutableTable.of();
        }

        @Override
        public void serverTick() {
            serverTicks++;
        }

        @Override
        public void clientTick() {
            clientTicks++;
        }

        @Override
        public void onLoad() {
            loads++;
        }

        @Override
        public void onChunkUnloaded() {
            chunkUnloads++;
            IMachine.super.onChunkUnloaded();
        }
    }

    public static final class StressModEvent extends Event {

        public StressModEvent() {
        }
    }

}
