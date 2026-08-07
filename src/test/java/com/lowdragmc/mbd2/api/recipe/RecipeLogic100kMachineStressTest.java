package com.lowdragmc.mbd2.api.recipe;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerInteger;
import com.lowdragmc.mbd2.performance.StressTestSupport;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises actual recipe matching and committed IO with lightweight,
 * integer-backed item, fluid, and energy stores.
 */
@Tag("performance")
class RecipeLogic100kMachineStressTest {

    private static final int SERVER_TICK_COUNT = 5;
    private static final int DIRECT_TICK_IO_COUNT = 1;
    private static final int TOTAL_TICK_IO_COUNT = SERVER_TICK_COUNT + DIRECT_TICK_IO_COUNT;

    private static final SyntheticCapability ITEM_CAPABILITY = new SyntheticCapability("stress_item");
    private static final SyntheticCapability FLUID_CAPABILITY = new SyntheticCapability("stress_fluid");
    private static final SyntheticCapability ENERGY_CAPABILITY = new SyntheticCapability("stress_energy");

    private static final ResourceAmounts ITEM = new ResourceAmounts(7, 3, 2, 1);
    private static final ResourceAmounts FLUID = new ResourceAmounts(11, 5, 3, 2);
    private static final ResourceAmounts ENERGY = new ResourceAmounts(13, 7, 5, 4);

    private static final MBDRecipe STRESS_RECIPE = new MBDRecipe(
            null,
            ResourceLocation.fromNamespaceAndPath("mbd2", "test/recipe_logic_100k_stress"),
            Map.of(
                    ITEM_CAPABILITY, List.of(normalContent(ITEM.input()), tickContent(ITEM.tickInput())),
                    FLUID_CAPABILITY, List.of(normalContent(FLUID.input()), tickContent(FLUID.tickInput())),
                    ENERGY_CAPABILITY, List.of(normalContent(ENERGY.input()), tickContent(ENERGY.tickInput()))),
            Map.of(
                    ITEM_CAPABILITY, List.of(normalContent(ITEM.output()), tickContent(ITEM.tickOutput())),
                    FLUID_CAPABILITY, List.of(normalContent(FLUID.output()), tickContent(FLUID.tickOutput())),
                    ENERGY_CAPABILITY, List.of(normalContent(ENERGY.output()), tickContent(ENERGY.tickOutput()))),
            List.of(),
            new CompoundTag(),
            SERVER_TICK_COUNT + 1,
            false,
            false,
            0);

    @Test
    void exercisesOneHundredThousandMachinesThroughMatchingIoAndTicks() {
        StressTestSupport.requireStressScale();
        warmUpRecipePath();
        var machines = createMachines();

        StressTestSupport.measure("recipe-start-match", 1, machines.length, () -> {
            for (StressMachine machine : machines) {
                requireSuccess(STRESS_RECIPE.matchRecipe(machine), "normal recipe match");
            }
        });

        StressTestSupport.measure("recipe-tick-match", 1, machines.length, () -> {
            for (StressMachine machine : machines) {
                requireSuccess(STRESS_RECIPE.matchTickRecipe(machine), "tick recipe match");
            }
        });

        StressTestSupport.measure("recipe-start-io", 2, (long) machines.length * 2, () -> {
            for (StressMachine machine : machines) {
                requireHandled(STRESS_RECIPE.handleRecipeIO(IO.IN, machine), "normal input IO");
                requireHandled(STRESS_RECIPE.handleRecipeIO(IO.OUT, machine), "normal output IO");
            }
        });

        StressTestSupport.measure("recipe-tick-io", 2, (long) machines.length * 2, () -> {
            for (StressMachine machine : machines) {
                requireHandled(STRESS_RECIPE.handleTickRecipeIO(IO.IN, machine), "tick input IO");
                requireHandled(STRESS_RECIPE.handleTickRecipeIO(IO.OUT, machine), "tick output IO");
            }
        });

        for (StressMachine machine : machines) {
            machine.activate(STRESS_RECIPE, SERVER_TICK_COUNT + 1);
        }
        StressTestSupport.measure("recipe-logic-server-tick", SERVER_TICK_COUNT,
                (long) machines.length * SERVER_TICK_COUNT, () -> {
                    for (int tick = 0; tick < SERVER_TICK_COUNT; tick++) {
                        for (StressMachine machine : machines) {
                            machine.getRecipeLogic().serverTick();
                        }
                    }
                });

        for (StressMachine machine : machines) {
            assertEquals(SERVER_TICK_COUNT, machine.getRecipeLogic().getProgress(),
                    "every active recipe must advance once per server tick");
            assertEquals(RecipeLogic.Status.WORKING, machine.getRecipeLogic().getStatus(),
                    "the fixture must retain enough resources to stay active");
            machine.assertResourceTotals();
        }
    }

    private static Content normalContent(int amount) {
        return new Content(amount, false, 1, 0);
    }

    private static Content tickContent(int amount) {
        return new Content(amount, true, 1, 0);
    }

    private static StressMachine[] createMachines() {
        var machines = new StressMachine[StressTestSupport.MACHINE_COUNT];
        for (int index = 0; index < machines.length; index++) {
            machines[index] = new StressMachine(index, TOTAL_TICK_IO_COUNT);
        }
        return machines;
    }

    private static void warmUpRecipePath() {
        var machine = new StressMachine(-1, 4_096);
        requireSuccess(STRESS_RECIPE.matchRecipe(machine), "warmup normal recipe match");
        requireSuccess(STRESS_RECIPE.matchTickRecipe(machine), "warmup tick recipe match");
        requireHandled(STRESS_RECIPE.handleRecipeIO(IO.IN, machine), "warmup normal input IO");
        requireHandled(STRESS_RECIPE.handleRecipeIO(IO.OUT, machine), "warmup normal output IO");
        machine.activate(STRESS_RECIPE, 4_097);
        for (int tick = 0; tick < 4_096; tick++) {
            machine.getRecipeLogic().serverTick();
        }
    }

    private static void requireSuccess(MBDRecipe.ActionResult result, String operation) {
        if (!result.isSuccess()) {
            throw new AssertionError(operation + " unexpectedly failed");
        }
    }

    private static void requireHandled(boolean handled, String operation) {
        if (!handled) {
            throw new AssertionError(operation + " unexpectedly failed");
        }
    }

    private record ResourceAmounts(int input, int output, int tickInput, int tickOutput) {

        int initialInput(int tickIoCount) {
            return input + tickInput * tickIoCount;
        }

        int outputCapacity(int tickIoCount) {
            return output + tickOutput * tickIoCount;
        }
    }

    private static final class StressMachine implements IMachine {

        private final long offset;
        private final ResourceStore items;
        private final ResourceStore fluids;
        private final ResourceStore energy;
        private final Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> recipeCapabilities;
        private final RecipeLogic recipeLogic = new RecipeLogic(this);

        private StressMachine(long offset, int tickIoCount) {
            this.offset = offset;
            this.items = new ResourceStore(ITEM.initialInput(tickIoCount), ITEM.outputCapacity(tickIoCount));
            this.fluids = new ResourceStore(FLUID.initialInput(tickIoCount), FLUID.outputCapacity(tickIoCount));
            this.energy = new ResourceStore(ENERGY.initialInput(tickIoCount), ENERGY.outputCapacity(tickIoCount));
            this.recipeCapabilities = ImmutableTable.<IO, RecipeCapability<?>, List<IRecipeHandler<?>>>builder()
                    .put(IO.BOTH, ITEM_CAPABILITY, List.<IRecipeHandler<?>>of(new ResourceHandler(ITEM_CAPABILITY, items)))
                    .put(IO.BOTH, FLUID_CAPABILITY, List.<IRecipeHandler<?>>of(new ResourceHandler(FLUID_CAPABILITY, fluids)))
                    .put(IO.BOTH, ENERGY_CAPABILITY, List.<IRecipeHandler<?>>of(new ResourceHandler(ENERGY_CAPABILITY, energy)))
                    .build();
        }

        private void activate(MBDRecipe recipe, int duration) {
            recipeLogic.setLastRecipe(recipe);
            recipeLogic.setDuration(duration);
            recipeLogic.setStatus(RecipeLogic.Status.WORKING);
        }

        private void assertResourceTotals() {
            assertStore(items, ITEM);
            assertStore(fluids, FLUID);
            assertStore(energy, ENERGY);
        }

        private static void assertStore(ResourceStore store, ResourceAmounts amounts) {
            assertEquals(0, store.input, "all configured input must be consumed exactly once per IO pass");
            assertEquals(amounts.output() + amounts.tickOutput() * TOTAL_TICK_IO_COUNT, store.output,
                    "all configured output must be produced exactly once per IO pass");
        }

        @Override
        public BlockEntity getHolder() {
            return null;
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
        public List<MBDRecipeType> getRecipeTypes() {
            return List.of();
        }

        @Override
        public RecipeLogic getRecipeLogic() {
            return recipeLogic;
        }

        @Override
        public Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> getRecipeCapabilitiesProxy() {
            return recipeCapabilities;
        }
    }

    private static final class ResourceStore {
        private int input;
        private int output;
        private final int outputCapacity;

        private ResourceStore(int input, int outputCapacity) {
            this.input = input;
            this.outputCapacity = outputCapacity;
        }
    }

    private static final class ResourceHandler implements IRecipeHandler<Integer> {

        private final SyntheticCapability capability;
        private final ResourceStore store;

        private ResourceHandler(SyntheticCapability capability, ResourceStore store) {
            this.capability = capability;
            this.store = store;
        }

        @Override
        public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName,
                                                boolean simulate) {
            int amount = 0;
            for (int content : left) {
                amount += content;
            }
            if (io == IO.IN) {
                if (store.input < amount) {
                    return left;
                }
                if (!simulate) {
                    store.input -= amount;
                }
                return null;
            }
            if (io == IO.OUT) {
                if (store.outputCapacity - store.output < amount) {
                    return left;
                }
                if (!simulate) {
                    store.output += amount;
                }
                return null;
            }
            return left;
        }

        @Override
        public RecipeCapability<Integer> getRecipeCapability() {
            return capability;
        }
    }

    private static final class SyntheticCapability extends RecipeCapability<Integer> {

        private SyntheticCapability(String name) {
            super(name, SerializerInteger.INSTANCE);
        }

        @Override
        public Integer createDefaultContent() {
            return 0;
        }

        @Override
        public Widget createPreviewWidget(Integer content) {
            return null;
        }

        @Override
        public Widget createXEITemplate() {
            return null;
        }

        @Override
        public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        }

        @Override
        public void createContentConfigurator(ConfiguratorGroup father, Supplier<Integer> supplier, Consumer<Integer> onUpdate) {
        }

        @Override
        public Component getLeftErrorInfo(List<Integer> left) {
            return Component.empty();
        }
    }
}
