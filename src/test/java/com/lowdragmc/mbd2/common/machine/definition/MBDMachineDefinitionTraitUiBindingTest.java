package com.lowdragmc.mbd2.common.machine.definition;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.IUIProviderTrait;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MBDMachineDefinitionTraitUiBindingTest {

    @Test
    void bindsTheDefinitionHeldByTheLiveTraitAfterDefinitionReload() {
        var definitionHeldByLiveTrait = new RecordingUiTraitDefinition();
        var reloadedDefinition = new RecordingUiTraitDefinition();
        var liveTrait = new TestTrait(definitionHeldByLiveTrait);

        assertNotSame(reloadedDefinition, liveTrait.getDefinition());

        MBDMachineDefinition.bindLoadedTraitUIs(List.of(liveTrait), new WidgetGroup());

        assertSame(liveTrait, definitionHeldByLiveTrait.boundTrait);
        assertNull(reloadedDefinition.boundTrait);
    }

    @LDLRegister(name = "test_ui_trait", group = "test")
    private static final class RecordingUiTraitDefinition extends TraitDefinition implements IUIProviderTrait {
        private ITrait boundTrait;

        @Override
        public ITrait createTrait(MBDMachine machine) {
            return null;
        }

        @Override
        public IGuiTexture getIcon() {
            return IGuiTexture.EMPTY;
        }

        @Override
        public void createTraitUITemplate(WidgetGroup ui) {
        }

        @Override
        public void initTraitUI(ITrait trait, WidgetGroup group) {
            boundTrait = trait;
        }
    }

    private static final class TestTrait implements ITrait {
        private final TraitDefinition definition;

        private TestTrait(TraitDefinition definition) {
            this.definition = definition;
        }

        @Override
        public MBDMachine getMachine() {
            return null;
        }

        @Override
        public TraitDefinition getDefinition() {
            return definition;
        }
    }
}
