package com.lowdragmc.mbd2.integration.bloodmagic.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.IUIProviderTrait;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTraitDefinition;
import com.lowdragmc.mbd2.utils.FormattingUtil;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import net.minecraft.network.chat.Component;
import wayoftime.bloodmagic.common.item.BloodMagicItems;

/**
 * Trait definition for Blood Magic Soul Network recipe handling.
 *
 * <p>The generated UI gives the machine a single Blood Orb slot and a live LP readout. The runtime
 * trait uses that orb's binding as the account to drain from or fill during recipes.</p>
 */
@LDLRegister(name = "bloodmagic_soul_network", group = "trait", modID = "bloodmagic")
public class BloodMagicSoulNetworkTraitDefinition extends RecipeCapabilityTraitDefinition implements IUIProviderTrait {

    /**
     * Creates the runtime Soul Network trait for a machine.
     */
    @Override
    public ITrait createTrait(MBDMachine machine) {
        return new BloodMagicSoulNetworkTrait(machine, this);
    }

    /**
     * Returns the weak Blood Orb icon used in trait lists.
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(BloodMagicItems.WEAK_BLOOD_ORB.get());
    }

    /**
     * Builds the orb slot and LP text widgets for machine UI templates.
     */
    @Override
    public void createTraitUITemplate(WidgetGroup ui) {
        var prefix = uiPrefixName();
        var orbSlot = new SlotWidget();
        orbSlot.setSelfPosition(new Position(0, 0));
        orbSlot.initTemplate();
        orbSlot.setId(prefix + "_orb");
        orbSlot.setIngredientIO(IngredientIO.RENDER_ONLY);
        ui.addWidget(orbSlot);

        var text = new TextTextureWidget(22, 4, 100, 10)
                .setText("0/0 LP")
                .textureStyle(t -> t.setType(TextTexture.TextType.LEFT));
        text.setId(prefix + "_text");
        ui.addWidget(text);
    }

    /**
     * Binds the orb slot and live Soul Network text to a runtime trait instance.
     */
    @Override
    public void initTraitUI(ITrait trait, WidgetGroup group) {
        if (trait instanceof BloodMagicSoulNetworkTrait soulNetworkTrait) {
            var prefix = uiPrefixName();
            WidgetUtils.widgetByIdForEach(group, "^%s_orb$".formatted(prefix), SlotWidget.class, slot -> {
                slot.setHandlerSlot(soulNetworkTrait.orbSlot, 0);
                slot.setCanPutItems(true);
                slot.setCanTakeItems(true);
            });
            WidgetUtils.widgetByIdForEach(group, "^%s_text$".formatted(prefix), TextTextureWidget.class, text -> {
                text.setText(() -> {
                    if (!soulNetworkTrait.hasBoundOrb()) {
                        return Component.translatable("config.definition.trait.bloodmagic_soul_network.no_bound_orb");
                    }
                    return Component.translatable("config.definition.trait.bloodmagic_soul_network.ui_container",
                            FormattingUtil.formatNumbers(soulNetworkTrait.getCurrentEssence()),
                            FormattingUtil.formatNumbers(soulNetworkTrait.getOrbCapacity()));
                });
            });
        }
    }
}
