package com.lowdragmc.mbd2.common.trait.redstone;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.SimpleCapabilityTraitDefinition;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * Definition for the singleton redstone signal recipe capability trait.
 *
 * <p>The trait intentionally has no block entity renderer and supplies a compact UI row showing current input,
 * strongest output, and remaining pulse time.</p>
 */
@LDLRegister(name = "redstone_signal", group = "trait", priority = -100)
public class RedstoneSignalCapabilityTraitDefinition extends SimpleCapabilityTraitDefinition {

    /**
     * Creates a redstone definition with recipe IO enabled in both directions.
     */
    public RedstoneSignalCapabilityTraitDefinition() {
        setRecipeHandlerIO(IO.BOTH);
    }

    /**
     * Creates the runtime redstone trait.
     *
     * @param machine owning machine
     * @return new redstone capability trait
     */
    @Override
    public SimpleCapabilityTrait createTrait(MBDMachine machine) {
        return new RedstoneSignalCapabilityTrait(machine, this);
    }

    /**
     * Returns the editor icon for this trait type.
     *
     * @return redstone torch texture used in trait lists
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.REDSTONE_TORCH);
    }

    /**
     * Restricts machines to a single redstone signal trait.
     *
     * @return {@code false} because emitted and sampled redstone state is global to the block
     */
    @Override
    public boolean allowMultiple() {
        return false;
    }

    /**
     * Disables block entity rendering for this trait.
     *
     * @param machine machine requesting a renderer
     * @return {@link IRenderer#EMPTY}
     */
    @Override
    public IRenderer getBESRenderer(IMachine machine) {
        return IRenderer.EMPTY;
    }

    /**
     * Creates the static UI template row used to show redstone state.
     *
     * @param ui destination widget group; this method appends one text widget with this trait's UI prefix id
     */
    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var text = new TextTextureWidget(0, 0, 120, 10,
                LocalizationUtils.format("config.definition.trait.redstone_signal.ui", 0, 0, 0))
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(uiPrefixName());
        ui.addWidget(text);
    }

    /**
     * Binds the UI template to the live redstone trait.
     *
     * <p>The text supplier reads synced values from the trait and displays input strength, output strength, and
     * maximum remaining output ticks. Non-redstone traits are ignored.</p>
     *
     * @param trait runtime trait instance
     * @param group instantiated UI widget tree
     */
    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof RedstoneSignalCapabilityTrait redstoneTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s$".formatted(prefix), TextTextureWidget.class, text -> {
                text.setText(() -> Component.translatable("config.definition.trait.redstone_signal.ui",
                        redstoneTrait.getInputSignal(),
                        redstoneTrait.getStrongestOutputSignal(),
                        redstoneTrait.getMaxRemainingTicks()));
            });
        }
    }
}
