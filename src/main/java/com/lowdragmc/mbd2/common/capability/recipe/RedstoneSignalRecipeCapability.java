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
import java.util.stream.Collectors;

/**
 * Recipe capability descriptor for redstone signal input predicates and output pulses.
 *
 * <p>Input recipe contents check the strongest observed signal against a {@link RedstoneSignal} range. Output recipe
 * contents emit temporary redstone pulses. The capability intentionally does not scale for automatic parallelization,
 * because redstone strength is bounded to vanilla {@code 0..15} and pulse duration is an event-like effect.</p>
 */
public class RedstoneSignalRecipeCapability extends RecipeCapability<RedstoneSignal> {
    public static final RedstoneSignalRecipeCapability CAP = new RedstoneSignalRecipeCapability();

    /**
     * Creates the singleton redstone signal capability.
     */
    protected RedstoneSignalRecipeCapability() {
        super("redstone_signal", SerializerRedstoneSignal.INSTANCE);
    }

    /**
     * Returns a representative output pulse for generic editor initialization.
     *
     * @return 15-strength pulse lasting 20 ticks
     */
    @Override
    public RedstoneSignal createDefaultContent() {
        return RedstoneSignal.output(15, 20);
    }

    /**
     * Returns a side-appropriate default content value.
     *
     * @param io recipe side being configured
     * @return exact input strength 15 for input, otherwise a 15-strength 20-tick output pulse
     */
    @Override
    public RedstoneSignal createDefaultContent(IO io) {
        return io == IO.IN ? RedstoneSignal.input(15) : RedstoneSignal.output(15, 20);
    }

    /**
     * Disables automatic parallel scaling for redstone contents.
     *
     * @return {@code false}
     */
    @Override
    public boolean scalesForAutomaticParallel() {
        return false;
    }

    /**
     * Creates an editor preview with a redstone torch icon and strength overlay.
     *
     * @param content signal to preview
     * @return newly created 18x18 preview widget
     */
    @Override
    public Widget createPreviewWidget(RedstoneSignal content) {
        var previewGroup = new WidgetGroup(0, 0, 18, 18);
        previewGroup.addWidget(new ImageWidget(1, 1, 16, 16, new ItemStackTexture(Items.REDSTONE_TORCH)));
        previewGroup.addWidget(new CornerNumberWidget(0, 0, 18, 18).setValue(content.strength()));
        return previewGroup;
    }

    /**
     * Creates the recipe-viewer text template.
     *
     * @return unbound XEI widget template
     */
    @Override
    public Widget createXEITemplate() {
        return new TextTextureWidget(0, 0, 80, 10, "0 RS")
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
    }

    /**
     * Binds input range or output pulse text to a recipe-viewer widget.
     *
     * @param widget       text widget created by {@link #createXEITemplate()}
     * @param content      recipe content wrapper
     * @param ingredientIO viewer role used to distinguish output display
     */
    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TextTextureWidget text) {
            RedstoneSignal signal = of(content.content);
            if (ingredientIO == IngredientIO.OUTPUT || signal.duration() > 0) {
                text.setText(Component.literal("%d RS / %dt".formatted(signal.strength(), signal.duration())));
            } else {
                text.setText(Component.literal("%s RS".formatted(signal.inputDisplay())));
            }
        }
    }

    /**
     * Creates a configurator using both input and output fields by default.
     *
     * @param father   parent configurator group
     * @param supplier current signal supplier
     * @param onUpdate update callback
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<RedstoneSignal> supplier, Consumer<RedstoneSignal> onUpdate) {
        createContentConfigurator(father, supplier, onUpdate, IO.BOTH);
    }

    /**
     * Creates side-aware redstone content configurators.
     *
     * <p>Input configuration exposes minimum and exclusive maximum strength. Output-capable configuration exposes
     * pulse duration in ticks. All values are normalized by {@link RedstoneSignal} when updated.</p>
     *
     * @param father   parent configurator group
     * @param supplier current signal supplier
     * @param onUpdate update callback
     * @param io       recipe side being configured
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<RedstoneSignal> supplier, Consumer<RedstoneSignal> onUpdate, IO io) {
        father.addConfigurators(new NumberConfigurator(io == IO.IN ? "recipe.capability.redstone_signal.min_strength" : "recipe.capability.redstone_signal.strength",
                () -> supplier.get().strength(),
                number -> onUpdate.accept(supplier.get().withStrength(number.intValue())), 1, true).setRange(0, 15));
        if (io == IO.IN) {
            father.addConfigurators(new NumberConfigurator("recipe.capability.redstone_signal.max_strength",
                    () -> supplier.get().maxStrength(),
                    number -> onUpdate.accept(supplier.get().withMaxStrength(number.intValue())), 1, true).setRange(1, 16));
        }
        if (io.support(IO.OUT)) {
            var duration = new NumberConfigurator("recipe.capability.redstone_signal.duration",
                    () -> supplier.get().duration(),
                    number -> onUpdate.accept(supplier.get().withDuration(number.intValue())), 20, true).setRange(1, Integer.MAX_VALUE);
            duration.setTips("recipe.capability.redstone_signal.duration.tooltip");
            father.addConfigurators(duration);
        }
    }

    /**
     * Builds the missing-input error component.
     *
     * @param left unmatched redstone input predicates
     * @return localized error listing missing ranges
     */
    @Override
    public Component getLeftErrorInfo(List<RedstoneSignal> left) {
        String missing = left.stream().map(RedstoneSignal::inputDisplay).collect(Collectors.joining(", "));
        return Component.translatable("recipe.capability.redstone_signal.missing", missing);
    }

    /**
     * Summarizes unmatched redstone requirements by their strongest minimum strength.
     *
     * @param left unmatched redstone contents
     * @return maximum minimum strength, or {@code 0} when empty
     */
    @Override
    public double calculateAmount(List<RedstoneSignal> left) {
        return left.stream().mapToInt(RedstoneSignal::strength).max().orElse(0);
    }
}
