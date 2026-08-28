package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCapabilityTraitDefinitionTest {

    @Test
    void definitionKeepsRecipeGroupAsRuntimeDefault() throws NoSuchFieldException {
        Field recipeGroup = RecipeCapabilityTraitDefinition.class.getDeclaredField("recipeGroup");
        Configurable configurable = recipeGroup.getAnnotation(Configurable.class);
        assertNotNull(configurable);
        assertTrue(!configurable.forceUpdate());

        var definition = new TestDefinition();
        definition.setRecipeGroup("ABCD");
        assertEquals("ABCD", definition.getRecipeGroup());
    }

    @Test
    void recipeGroupIsRuntimeInstanceState() throws NoSuchFieldException {
        Field traitField = RecipeCapabilityTrait.class.getDeclaredField("recipeGroup");
        assertNotNull(traitField.getAnnotation(com.lowdragmc.lowdraglib.syncdata.annotation.Persisted.class));
        assertNotNull(traitField.getAnnotation(com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced.class));
    }

    @Test
    void runtimeTraitCopiesDefinitionDefault() {
        var definition = new TestDefinition();
        definition.setRecipeGroup("ABCD");

        var trait = new TestTrait(null, definition);
        definition.setRecipeGroup("C3D4");

        assertEquals("ABCD", trait.getRecipeGroup());
    }

    @LDLRegister(name = "test_recipe_capability", group = "test")
    private static final class TestDefinition extends RecipeCapabilityTraitDefinition {
        @Override
        public ITrait createTrait(MBDMachine machine) {
            return new TestTrait(machine, this);
        }

        @Override
        public IGuiTexture getIcon() {
            return IGuiTexture.EMPTY;
        }
    }

    private static final class TestTrait extends RecipeCapabilityTrait {
        private TestTrait(MBDMachine machine, TestDefinition definition) {
            super(machine, definition);
        }
    }
}
