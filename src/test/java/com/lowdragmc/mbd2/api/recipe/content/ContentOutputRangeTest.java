package com.lowdragmc.mbd2.api.recipe.content;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeBuilder;
import com.lowdragmc.mbd2.common.capability.recipe.ForgeEnergyRecipeCapability;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWill;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicWillRecipeCapability;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentOutputRangeTest {

    private static final LongCapability CAPABILITY = new LongCapability();

    @Test
    void jsonAndNbtRoundTripPreservesRange() {
        Content original = new Content(42L, false, 1, 0, 3, 9, "slot", "ui");

        JsonObject json = SerializerLong.INSTANCE.toJsonContent(original).getAsJsonObject();
        Content fromJson = SerializerLong.INSTANCE.fromJsonContent(json);
        assertRange(fromJson, 3, 9);
        assertEquals("slot", fromJson.slotName);
        assertEquals("ui", fromJson.uiName);

        CompoundTag nbt = SerializerLong.INSTANCE.toNBT(original);
        Content fromNbt = SerializerLong.INSTANCE.fromNBT(nbt);
        assertRange(fromNbt, 3, 9);

        FriendlyByteBuf network = new FriendlyByteBuf(Unpooled.buffer());
        SerializerLong.INSTANCE.toNetworkContent(network, original);
        Content fromNetwork = SerializerLong.INSTANCE.fromNetworkContent(network);
        assertRange(fromNetwork, 3, 9);
        assertEquals("slot", fromNetwork.slotName);
        assertEquals("ui", fromNetwork.uiName);
    }

    @Test
    void legacyContentAndInvalidBoundsKeepFixedOutputBehavior() {
        Content legacy = new Content(42L, false, 1, 0);
        assertFalse(legacy.hasOutputRange());

        JsonObject legacyJson = new JsonObject();
        legacyJson.addProperty("content", 42L);
        assertFalse(SerializerLong.INSTANCE.fromJsonContent(legacyJson).hasOutputRange());

        JsonObject underscoredJson = new JsonObject();
        underscoredJson.addProperty("content", 42L);
        underscoredJson.addProperty("min_output", 4L);
        underscoredJson.addProperty("max_output", 8L);
        assertRange(SerializerLong.INSTANCE.fromJsonContent(underscoredJson), 4, 8);

        CompoundTag legacyNbt = new CompoundTag();
        legacyNbt.putLong("content", 42L);
        assertFalse(SerializerLong.INSTANCE.fromNBT(legacyNbt).hasOutputRange());

        Content invalid = new Content(42L, false, 1, 0, -1, 9);
        assertFalse(invalid.hasOutputRange());
        Content copied = invalid.copy(CAPABILITY, ContentModifier.multiplier(2));
        assertEquals(-1, copied.minOutput);
        assertEquals(-1, copied.maxOutput);
    }

    @Test
    void builderOnlyCarriesRangeOnOutputs() {
        MBDRecipeBuilder builder = MBDRecipeBuilder.ofRaw().outputRange(2, 5);
        builder.inputs(CAPABILITY, 1L).outputs(CAPABILITY, 1L);

        Content input = builder.input.get(CAPABILITY).get(0);
        Content output = builder.output.get(CAPABILITY).get(0);
        assertFalse(input.hasOutputRange());
        assertRange(output, 2, 5);

        Content scaled = output.copy(CAPABILITY, ContentModifier.IDENTITY);
        assertRange(scaled, 2, 5);
    }

    @Test
    void exactLongReplacementAndRandomRollStayWithinInclusiveBounds() throws Exception {
        long large = Long.MAX_VALUE - 1;
        assertEquals(large, ContentModifier.value(large).apply(1L).longValue());

        Method roll = MBDRecipe.class.getDeclaredMethod("nextInclusiveLong", long.class, long.class);
        roll.setAccessible(true);
        assertEquals(7L, roll.invoke(null, 7L, 7L));
        for (int i = 0; i < 500; i++) {
            long value = (long) roll.invoke(null, 3L, 5L);
            assertTrue(value >= 3 && value <= 5);
        }
        long wideValue = (long) roll.invoke(null, 0L, Long.MAX_VALUE);
        assertTrue(wideValue >= 0 && wideValue <= Long.MAX_VALUE);

        Method copy = MBDRecipe.class.getDeclaredMethod("copyOutputContent", RecipeCapability.class, Content.class, boolean.class);
        copy.setAccessible(true);
        Content output = new Content(99L, false, 1, 0, 2, 5);
        assertEquals(5L, copy.invoke(null, CAPABILITY, output, true));
        for (int i = 0; i < 500; i++) {
            long amount = (long) copy.invoke(null, CAPABILITY, output, false);
            assertTrue(amount >= 2 && amount <= 5);
        }
        assertEquals(99L, output.getContent());
    }

    @Test
    void outputRangeOnlyReplacesThePrimaryCapabilityAmount() throws Exception {
        BloodMagicWill original = new BloodMagicWill(EnumDemonWillType.DEFAULT, 10, 100);
        Content content = new Content(original, false, 1, 0, 3, 3);

        Method copy = MBDRecipe.class.getDeclaredMethod("copyOutputContent", RecipeCapability.class, Content.class, boolean.class);
        copy.setAccessible(true);
        BloodMagicWill rolled = (BloodMagicWill) copy.invoke(null, BloodMagicWillRecipeCapability.CAP, content, false);

        assertEquals(3, rolled.amount());
        assertEquals(100, rolled.maxOutput());
    }

    @Test
    void narrowPayloadsRejectRangesThatWouldOverflowOrRound() {
        assertTrue(SerializerInteger.INSTANCE.supportsOutputAmount(Integer.MAX_VALUE));
        assertFalse(SerializerInteger.INSTANCE.supportsOutputAmount((long) Integer.MAX_VALUE + 1));
        assertTrue(SerializerFloat.INSTANCE.supportsOutputAmount(1L << 24));
        assertFalse(SerializerFloat.INSTANCE.supportsOutputAmount((1L << 24) + 1));
        assertTrue(SerializerDouble.INSTANCE.supportsOutputAmount(1L << 53));
        assertFalse(SerializerDouble.INSTANCE.supportsOutputAmount((1L << 53) + 1));

        var capability = ForgeEnergyRecipeCapability.CAP;
        assertTrue(capability.supportsOutputRange(0, Integer.MAX_VALUE));
        assertFalse(capability.supportsOutputRange(0, (long) Integer.MAX_VALUE + 1));

        Content invalid = new Content(1, false, 1, 0, 0, (long) Integer.MAX_VALUE + 1);
        Content copied = invalid.copy(capability, null);
        assertEquals(Content.OUTPUT_RANGE_DISABLED, copied.minOutput);
        assertEquals(Content.OUTPUT_RANGE_DISABLED, copied.maxOutput);

        MBDRecipeBuilder builder = MBDRecipeBuilder.ofRaw().outputRange(0, (long) Integer.MAX_VALUE + 1);
        builder.outputs(capability, 1);
        Content built = builder.output.get(capability).get(0);
        assertFalse(built.hasOutputRange());
    }

    @Test
    void narrowNumericCapabilitiesRejectUnrepresentableLongBounds() {
        long floatLimit = 1L << 24;
        long doubleLimit = 1L << 53;

        assertTrue(SerializerInteger.INSTANCE.supportsOutputAmount(Integer.MAX_VALUE));
        assertFalse(SerializerInteger.INSTANCE.supportsOutputAmount((long) Integer.MAX_VALUE + 1));
        assertTrue(SerializerFloat.INSTANCE.supportsOutputAmount(floatLimit));
        assertFalse(SerializerFloat.INSTANCE.supportsOutputAmount(floatLimit + 1));
        assertTrue(SerializerDouble.INSTANCE.supportsOutputAmount(doubleLimit));
        assertFalse(SerializerDouble.INSTANCE.supportsOutputAmount(doubleLimit + 1));

        var integerBuilder = MBDRecipeBuilder.ofRaw().outputRange(0, (long) Integer.MAX_VALUE + 1);
        integerBuilder.outputs(new NumericCapability<>("test_integer_output_range", SerializerInteger.INSTANCE, 0), 1);
        assertFalse(integerBuilder.output.values().stream().flatMap(List::stream).findFirst().orElseThrow().hasOutputRange());

        var floatBuilder = MBDRecipeBuilder.ofRaw().outputRange(0, floatLimit + 1);
        floatBuilder.outputs(new NumericCapability<>("test_float_output_range", SerializerFloat.INSTANCE, 0f), 1f);
        assertFalse(floatBuilder.output.values().stream().flatMap(List::stream).findFirst().orElseThrow().hasOutputRange());

        var doubleBuilder = MBDRecipeBuilder.ofRaw().outputRange(0, doubleLimit + 1);
        doubleBuilder.outputs(new NumericCapability<>("test_double_output_range", SerializerDouble.INSTANCE, 0d), 1d);
        assertFalse(doubleBuilder.output.values().stream().flatMap(List::stream).findFirst().orElseThrow().hasOutputRange());
    }

    private static void assertRange(Content content, long min, long max) {
        assertTrue(content.hasOutputRange());
        assertEquals(min, content.minOutput);
        assertEquals(max, content.maxOutput);
    }

    private static final class LongCapability extends RecipeCapability<Long> {
        private LongCapability() {
            super("test_long_output_range", SerializerLong.INSTANCE);
        }

        @Override
        public Long createDefaultContent() {
            return 0L;
        }

        @Override
        public Widget createPreviewWidget(Long content) {
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
        public void createContentConfigurator(ConfiguratorGroup father, Supplier<Long> supplier, Consumer<Long> onUpdate) {
        }

        @Override
        public Component getLeftErrorInfo(List<Long> left) {
            return Component.empty();
        }
    }

    private static final class NumericCapability<T> extends RecipeCapability<T> {
        private final T defaultContent;

        private NumericCapability(String name, IContentSerializer<T> serializer, T defaultContent) {
            super(name, serializer);
            this.defaultContent = defaultContent;
        }

        @Override
        public T createDefaultContent() {
            return defaultContent;
        }

        @Override
        public Widget createPreviewWidget(T content) {
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
        public void createContentConfigurator(ConfiguratorGroup father, Supplier<T> supplier, Consumer<T> onUpdate) {
        }

        @Override
        public Component getLeftErrorInfo(List<T> left) {
            return Component.empty();
        }
    }

}
