package com.lowdragmc.mbd2.api.registry;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.runtime.AnnotationDetector;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.common.item.MultiblockSelectionExportToolItem;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigBlockProperties;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigItemProperties;
import com.lowdragmc.mbd2.common.machine.definition.config.MachineState;
import com.lowdragmc.mbd2.common.machine.definition.config.toggle.ToggleCreativeTab;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Central set of MBD2 registries and lazily-created built-in definitions.
 * <p>
 * These registries are lightweight mod-local registries used by machine
 * definitions, recipe types, recipe capabilities, recipe conditions, and
 * editor-detected definition classes before or alongside Forge registries.
 * Register entries during the mod-loading/registration events exposed by
 * {@link com.lowdragmc.mbd2.common.event.MBDRegistryEvent}; mutating them after
 * worlds have loaded can leave machines, recipe logic, and editor data with
 * stale references.
 * <p>
 * Thread safety: registry mutation is expected on the Forge mod bus thread
 * during startup. Runtime lookups are read-only and should be treated as
 * immutable once registration has finished.
 */
public class MBDRegistries {
    @Getter(lazy = true)
    @Accessors(fluent = true)
    private static final MBDGadgetsItem GADGETS_ITEM = createGadgetsItem();
    @Getter(lazy = true)
    @Accessors(fluent = true)
    private static final MultiblockSelectionExportToolItem SELECTION_EXPORT_TOOL_ITEM = createSelectionExportToolItem();
    @Getter(lazy = true)
    @Accessors(fluent = true)
    private static final MBDMachineDefinition FAKE_MACHINE = createFakeMachine();

    private static MBDGadgetsItem createGadgetsItem() {
        return new MBDGadgetsItem();
    }

    private static MultiblockSelectionExportToolItem createSelectionExportToolItem() {
        return new MultiblockSelectionExportToolItem();
    }

    private static MBDMachineDefinition createFakeMachine() {
        return MBDMachineDefinition.builder()
                .id(MBD2.id("fake_machine"))
                .rootState(MachineState.builder()
                        .name("base")
                        .renderer(IRenderer.EMPTY)
                        .shape(Shapes.block())
                        .lightLevel(0)
                        .build())
                .blockProperties(ConfigBlockProperties.builder().rotationState(RotationState.ALL).build())
                .itemProperties(ConfigItemProperties.builder()
                        .creativeTab(new ToggleCreativeTab())
                        .build())
                .build();
    }

    /**
     * Editor/discovery registry for machine definition implementation types.
     * Keys are LDL register names and values are annotation wrappers that can
     * create/load concrete {@link MBDMachineDefinition} instances.
     */
    public static final MBDRegistry.String<AnnotationDetector.Wrapper<LDLRegister, ? extends MBDMachineDefinition>> MACHINE_DEFINITION_TYPES = new MBDRegistry.String<>(MBD2.id("machine_definition_type"));
    /**
     * Editor/discovery registry for trait definition implementation types.
     * Keys are LDL register names and values create concrete trait definition
     * instances used by machine settings.
     */
    public static final MBDRegistry.String<AnnotationDetector.Wrapper<LDLRegister, ? extends TraitDefinition>> TRAIT_DEFINITION_TYPES = new MBDRegistry.String<>(MBD2.id("trait_definition_type"));

    /**
     * Runtime registry of loaded machine definitions keyed by their resource
     * location ids. Machines resolve this registry when block/item definitions,
     * project files, or data packs need to reference a definition by id.
     */
    public static final MBDRegistry.RL<MBDMachineDefinition> MACHINE_DEFINITIONS = new MBDRegistry.RL<>(MBD2.id("machine_definition"));
    /**
     * Runtime registry of MBD recipe types keyed by resource location. The Forge
     * recipe type and serializer registries are updated in parallel by
     * {@link com.lowdragmc.mbd2.common.event.MBDRegistryEvent.MBDRecipeType}.
     */
    public static final MBDRegistry.RL<MBDRecipeType> RECIPE_TYPES = new MBDRegistry.RL<>(MBD2.id("recipe_type"));
    /**
     * Registry of recipe capability channels keyed by string id. Recipe content,
     * traits, and recipe logic use these ids to route typed input/output
     * handlers.
     */
    public static final MBDRegistry.String<RecipeCapability<?>> RECIPE_CAPABILITIES = new MBDRegistry.String<>(MBD2.id("recipe_capability"));
    /**
     * Registry of recipe condition classes keyed by string id. Serialized
     * recipes use these ids to instantiate availability checks.
     */
    public static final MBDRegistry.String<Class<? extends RecipeCondition>> RECIPE_CONDITIONS = new MBDRegistry.String<>(MBD2.id("recipe_condition"));

}
