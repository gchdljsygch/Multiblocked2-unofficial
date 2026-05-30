package com.lowdragmc.mbd2.common.capability.recipe;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.common.gui.recipe.CornerNumberWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RedstoneSignalRecipeCapability extends RecipeCapability<RedstoneSignal> {
    public static final RedstoneSignalRecipeCapability CAP = new RedstoneSignalRecipeCapability();

    protected RedstoneSignalRecipeCapability() {
        super("redstone_signal", SerializerRedstoneSignal.INSTANCE);
    }

    @Override
    public RedstoneSignal createDefaultContent() {
        return RedstoneSignal.output(15, 20);
    }

    @Override
    public RedstoneSignal createDefaultContent(IO io) {
        return io == IO.IN ? RedstoneSignal.input(15) : RedstoneSignal.output(15, 20);
    }

    @Override
    public Widget createPreviewWidget(RedstoneSignal content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16, new ItemStackTexture(Items.REDSTONE_TORCH)));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content.strength()));
        return previewGroup;
    }

    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 80, 10, "0 RS")
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget text) {
            RedstoneSignal signal = of(content.content);
            if (ingredientIO == IngredientIO.OUTPUT || signal.duration() > 0) {
                text.setText(Component.literal("%d RS / %dt".formatted(signal.strength(), signal.duration())));
            } else {
                text.setText(Component.literal(">=%d RS".formatted(signal.strength())));
            }
        }
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<RedstoneSignal> supplier, Consumer<RedstoneSignal> onUpdate) {
        createContentConfigurator(father, supplier, onUpdate, IO.BOTH);
    }

    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<RedstoneSignal> supplier, Consumer<RedstoneSignal> onUpdate, IO io) {
        father.addConfigurators(new NumberConfigurator("recipe.capability.redstone_signal.strength",
                () -> supplier.get().strength(),
                number -> onUpdate.accept(supplier.get().withStrength(number.intValue())), 1, true).setRange(0, 15));
        if (io.support(IO.OUT)) {
            var duration = new NumberConfigurator("recipe.capability.redstone_signal.duration",
                    () -> supplier.get().duration(),
                    number -> onUpdate.accept(supplier.get().withDuration(number.intValue())), 20, true).setRange(1, Integer.MAX_VALUE);
            duration.setTips("recipe.capability.redstone_signal.duration.tooltip");
            father.addConfigurators(duration);
        }
    }

    @Override
    public Component getLeftErrorInfo(List<RedstoneSignal> left) {
        int missing = left.stream().mapToInt(RedstoneSignal::strength).max().orElse(0);
        return Component.translatable("recipe.capability.redstone_signal.missing", missing);
    }

    @Override
    public double calculateAmount(List<RedstoneSignal> left) {
        return left.stream().mapToInt(RedstoneSignal::strength).max().orElse(0);
    }
}
