package com.lowdragmc.mbd2.common.event;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeSerializer;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.CommonProxy;
import com.lowdragmc.mbd2.common.data.MBDMachineDefinitionTypes;
import com.lowdragmc.mbd2.common.data.MBDTraitDefinitionTypes;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.DataInputStream;
import java.io.File;
import java.util.function.Supplier;

/**
 * Mod-bus registration events for MBD2's local registries.
 * <p>
 * These events are posted during startup so addons can register machine
 * definitions, recipe types, recipe capabilities, recipe conditions, and
 * editor/discovery types without reaching into {@link MBDRegistries} directly.
 * Registration methods mutate global registries and should only be called from
 * the corresponding event handler while the mod bus is performing registration.
 * <p>
 * Thread safety: handlers run on the Forge mod-loading path. The registry data
 * they populate is treated as read-only after startup and is not designed for
 * concurrent runtime mutation.
 */
public class MBDRegistryEvent extends Event implements IModBusEvent {

    /**
     * Registration event for productive machine definitions.
     * <p>
     * Definitions may be provided directly or loaded from serialized project
     * files/resources. Failed loads are logged and skipped so a bad optional
     * definition does not abort the entire registration pass.
     */
    public static class Machine extends MBDRegistryEvent {
        /**
         * Register a machine definition.
         *
         * @param definition complete definition keyed by {@link MBDMachineDefinition#id()}
         */
        public void register(MBDMachineDefinition definition) {
            MBDRegistries.MACHINE_DEFINITIONS.register(definition.id(), definition);
        }

        /**
         * Register a machine definition from a file.
         *
         * @param type definition type id registered in
         *             {@link MBDRegistries#MACHINE_DEFINITION_TYPES}
         * @param file NBT project file to read
         */
        public void registerFromFile(String type, File file) {
            var definitionType = MBDRegistries.MACHINE_DEFINITION_TYPES.get(type);
            if (definitionType == null) {
                LDLib.LOGGER.error("error could not find the definition type {} from file {}", type, file);
                return;
            }
            try {
                var tag = NbtIo.read(file);
                if (tag == null) throw new Exception("tag is null");
                register(definitionType.creator().get().loadProductiveTag(file, tag, CommonProxy.getPostTask()));
            } catch (Exception e) {
                LDLib.LOGGER.error("error could not load the project from file {}", file, e);
            }
        }

        /**
         * Register a machine definition from a resource.
         *
         * @param source      class whose classloader owns the asset
         * @param type        definition type id registered in
         *                    {@link MBDRegistries#MACHINE_DEFINITION_TYPES}
         * @param projectFile path below {@code /assets/}
         */
        public void registerFromResource(Class<?> source, String type, String projectFile) {
            var definitionType = MBDRegistries.MACHINE_DEFINITION_TYPES.get(type);
            if (definitionType == null) {
                LDLib.LOGGER.error("error could not find the definition type {} from resource {}", type, projectFile);
                return;
            }

            var inputstream = source.getResourceAsStream(String.format("/assets/%s", projectFile));
            if (inputstream == null) {
                LDLib.LOGGER.error("error could not find the project from resource {}", projectFile);
                return;
            }
            try {
                var tag = NbtIo.read(new DataInputStream(inputstream));
                if ("mb".equals(type)) {
                    MultiblockMachineProject.expandPatternReferences(source, projectFile, tag);
                }
                register(definitionType.creator().get().loadProductiveTag(null, tag, CommonProxy.getPostTask()));
            } catch (Exception e) {
                LDLib.LOGGER.error("error could not load the project from resource {}", projectFile, e);
            }
        }
    }

    /**
     * Registration event for MBD recipe types.
     * <p>
     * Registering a recipe type also registers the matching Forge recipe type
     * and serializer under the recipe type's registry name.
     */
    public static class MBDRecipeType extends MBDRegistryEvent {
        /**
         * Register a recipe type.
         *
         * @param recipeType recipe type with a non-null registry name
         */
        public void register(com.lowdragmc.mbd2.api.recipe.MBDRecipeType recipeType) {
            ForgeRegistries.RECIPE_TYPES.register(recipeType.getRegistryName(), recipeType);
            ForgeRegistries.RECIPE_SERIALIZERS.register(recipeType.getRegistryName(), new MBDRecipeSerializer());
            MBDRegistries.RECIPE_TYPES.register(recipeType.getRegistryName(), recipeType);
        }

        /**
         * Register a recipe type from a file.
         *
         * @param file NBT recipe-type project file to read
         */
        public void registerFromFile(File file) {
            try {
                var tag = NbtIo.read(file);
                if (tag == null) throw new Exception("tag is null");
                register(com.lowdragmc.mbd2.api.recipe.MBDRecipeType.createDefault().loadProductiveTag(null, tag, CommonProxy.getPostTask()));
            } catch (Exception e) {
                LDLib.LOGGER.error("error could not load the project from file {}", file, e);
            }
        }

        /**
         * Register a recipe type from a resource.
         *
         * @param source      class whose classloader owns the asset
         * @param projectFile path below {@code /assets/}
         */
        public void registerFromResource(Class<?> source, String projectFile) {
            var inputstream = source.getResourceAsStream(String.format("/assets/%s", projectFile));
            if (inputstream == null) {
                LDLib.LOGGER.error("error could not find the project from resource {}", projectFile);
                return;
            }
            try {
                var tag = NbtIo.read(new DataInputStream(inputstream));
                register(com.lowdragmc.mbd2.api.recipe.MBDRecipeType.createDefault().loadProductiveTag(null, tag, CommonProxy.getPostTask()));
            } catch (Exception e) {
                LDLib.LOGGER.error("error could not load the project from resource {}", projectFile, e);
            }
        }
    }

    /**
     * Registration event for recipe condition implementations.
     */
    public static class RecipeCondition extends MBDRegistryEvent {
        /**
         * Register a recipe condition.
         *
         * @param id        stable string id used by serialized recipes
         * @param condition condition implementation class
         */
        public void register(String id, Class<? extends com.lowdragmc.mbd2.api.recipe.RecipeCondition> condition) {
            MBDRegistries.RECIPE_CONDITIONS.register(id, condition);
        }
    }

    /**
     * Registration event for recipe capability channels.
     */
    public static class RecipeCapability extends MBDRegistryEvent {
        /**
         * Register a recipe capability.
         *
         * @param id         stable string id used by recipe content and trait routing
         * @param capability capability implementation
         */
        public void register(String id, com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability<?> capability) {
            MBDRegistries.RECIPE_CAPABILITIES.register(id, capability);
        }
    }


    /**
     * Registration event for machine definition implementation types used by
     * the editor/project loader.
     */
    public static class MachineDefinitionType extends MBDRegistryEvent {
        /**
         * Register a machine definition type.
         *
         * @param clazz   implementation class annotated for LDL discovery
         * @param creator supplier that creates a fresh loadable definition
         * @param <T>     concrete machine definition type
         */
        public <T extends MBDMachineDefinition> void register(Class<T> clazz, Supplier<T> creator) {
            MBDMachineDefinitionTypes.register(clazz, creator);
        }
    }

    /**
     * Registration event for trait definition implementation types used by
     * machine settings and editor discovery.
     */
    public static class TraitType extends MBDRegistryEvent {
        /**
         * Register a trait definition.
         *
         * @param clazz trait definition class annotated for LDL discovery
         */
        public void register(Class<? extends TraitDefinition> clazz) {
            MBDTraitDefinitionTypes.register(clazz);
        }
    }

}
