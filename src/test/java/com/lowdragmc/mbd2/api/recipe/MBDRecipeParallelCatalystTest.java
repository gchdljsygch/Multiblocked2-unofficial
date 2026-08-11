package com.lowdragmc.mbd2.api.recipe;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandler;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerInteger;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MBDRecipeParallelCatalystTest {

    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath("mbd2", "test/parallel_catalyst");
    private static final IntegerCapability CAPABILITY = new IntegerCapability();

    @Test
    void automaticParallelOnlyScalesCatalystsWhenRequired() {
        var sharedCatalystResult = MBDRecipe.accurateParallel(holderWithCapacity(4), parallelRecipe(false), 4, false);
        assertEquals(3, sharedCatalystResult.getSecond());
        assertEquals(1, sharedCatalystResult.getFirst().getInputContents(CAPABILITY).get(0).getContent());

        var forcedCatalystResult = MBDRecipe.accurateParallel(holderWithCapacity(4), parallelRecipe(true), 4, false);
        assertEquals(2, forcedCatalystResult.getSecond());
        assertEquals(2, forcedCatalystResult.getFirst().getInputContents(CAPABILITY).get(0).getContent());
    }

    @Test
    void builderAndRecipeCopiesKeepTheCatalystParallelPolicy() {
        var built = MBDRecipeBuilder.ofRaw()
                .isForceParallelCatalyst(true)
                .buildRawRecipe();
        assertTrue(built.isForceParallelCatalyst);
        assertTrue(built.copy().isForceParallelCatalyst);
        assertTrue(built.deepCopied(RECIPE_ID).isForceParallelCatalyst);

        var copiedBuilderRecipe = MBDRecipeBuilder.ofRaw()
                .isForceParallelCatalyst(true)
                .copy(RECIPE_ID)
                .buildRawRecipe();
        assertTrue(copiedBuilderRecipe.isForceParallelCatalyst);

        assertTrue(metadataRecipe(true).toBuilder().buildRawRecipe().isForceParallelCatalyst);

        var legacyRecipe = new MBDRecipe(null, RECIPE_ID, Map.of(), Map.of(), List.of(), new CompoundTag(), 100, false, false, 0);
        assertFalse(legacyRecipe.isForceParallelCatalyst);
    }

    @Test
    void serializersWriteThePolicyAndKeepMissingDataFalseByDefault() {
        var serializer = MBDRecipeSerializer.SERIALIZER;
        var forcedRecipe = metadataRecipe(true);
        var defaultRecipe = metadataRecipe(false);

        var forcedJson = serializer.toJson(forcedRecipe);
        assertTrue(forcedJson.get("isForceParallelCatalyst").getAsBoolean());
        assertFalse(serializer.toJson(defaultRecipe).has("isForceParallelCatalyst"));

        CompoundTag forcedNbt = serializer.toNBT(forcedRecipe);
        assertTrue(forcedNbt.getBoolean("isForceParallelCatalyst"));
        CompoundTag legacyNbt = serializer.toNBT(defaultRecipe);
        legacyNbt.remove("isForceParallelCatalyst");
        assertFalse(legacyNbt.getBoolean("isForceParallelCatalyst"));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, forcedRecipe);
        assertTrue(readForceParallelCatalyst(buffer));

        FriendlyByteBuf defaultBuffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(defaultBuffer, defaultRecipe);
        assertFalse(readForceParallelCatalyst(defaultBuffer));
    }

    private static MBDRecipe parallelRecipe(boolean isForceParallelCatalyst) {
        return new MBDRecipe(
                null,
                RECIPE_ID,
                Map.of(CAPABILITY, List.of(
                        new Content(1, false, 0, 0),
                        new Content(1, false, 1, 0))),
                Map.of(),
                List.of(),
                new CompoundTag(),
                100,
                false,
                false,
                0,
                null,
                isForceParallelCatalyst);
    }

    private static MBDRecipe metadataRecipe(boolean isForceParallelCatalyst) {
        return new MBDRecipe(
                new MBDRecipeType(ResourceLocation.fromNamespaceAndPath("mbd2", "test_parallel_catalyst")),
                RECIPE_ID,
                Map.of(),
                Map.of(),
                List.of(),
                new CompoundTag(),
                100,
                false,
                false,
                0,
                null,
                isForceParallelCatalyst);
    }

    private static IRecipeCapabilityHolder holderWithCapacity(int capacity) {
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> handlers = HashBasedTable.create();
        handlers.put(IO.IN, CAPABILITY, List.of(new IntegerCapacityHandler(capacity)));
        return () -> handlers;
    }

    private static boolean readForceParallelCatalyst(FriendlyByteBuf buffer) {
        buffer.readUtf();
        buffer.readVarInt();
        buffer.readVarInt();
        buffer.readVarInt();
        buffer.readVarInt();
        buffer.readNbt();
        buffer.readBoolean();
        buffer.readBoolean();
        buffer.readVarInt();
        buffer.readUtf();
        return buffer.readBoolean();
    }

    private record IntegerCapacityHandler(int capacity) implements IRecipeHandler<Integer> {
        @Override
        @Nullable
        public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName, boolean simulate) {
            return left.stream().mapToInt(Integer::intValue).sum() <= capacity ? null : left;
        }

        @Override
        public RecipeCapability<Integer> getRecipeCapability() {
            return CAPABILITY;
        }
    }

    private static final class IntegerCapability extends RecipeCapability<Integer> {
        private IntegerCapability() {
            super("parallel_catalyst_test", SerializerInteger.INSTANCE);
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
