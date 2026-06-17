package com.lowdragmc.mbd2.common.data;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.runtime.AnnotationDetector;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.event.MBDRegistryEvent;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.integration.create.machine.CreateKineticMachineDefinition;
import net.minecraftforge.fml.ModLoader;

import java.util.function.Supplier;

/**
 * Startup registrar for machine definition implementation types.
 * <p>
 * This class populates {@link MBDRegistries#MACHINE_DEFINITION_TYPES} with the
 * built-in machine definition classes and then posts
 * {@link MBDRegistryEvent.MachineDefinitionType} so addons can contribute their
 * own editor/loadable definition types.
 * <p>
 * Thread safety: call {@link #init()} only during mod loading. The backing
 * registry is unfrozen while this method runs and frozen before it returns.
 */
public class MBDMachineDefinitionTypes {

    /**
     * Registers built-in and addon machine definition types.
     * <p>
     * Optional integration types are registered only when their owning mod is
     * loaded. Calling this method after definitions have been loaded can make
     * project deserialization inconsistent.
     */
    public static void init() {
        MBDRegistries.MACHINE_DEFINITION_TYPES.unfreeze();
        register(MBDMachineDefinition.class, MBDMachineDefinition::createDefault);
        register(MultiblockMachineDefinition.class, MultiblockMachineDefinition::createDefault);
        register(EntityMachineDefinition.class, EntityMachineDefinition::createDefault);
        if (MBD2.isCreateLoaded()) {
            register(CreateKineticMachineDefinition.class, CreateKineticMachineDefinition::createDefault);
        }
        ModLoader.get().postEvent(new MBDRegistryEvent.MachineDefinitionType());
        MBDRegistries.MACHINE_DEFINITION_TYPES.freeze();
    }

    /**
     * Registers one machine definition implementation type.
     *
     * @param clazz   definition class annotated with {@link LDLRegister}; the
     *                annotation name becomes the serialized type id
     * @param creator factory that returns a fresh definition instance for
     *                project loading
     * @param <T>     concrete definition type
     */
    public static <T extends MBDMachineDefinition> void register(Class<T> clazz, Supplier<T> creator) {
        if (clazz.isAnnotationPresent(LDLRegister.class)) {
            var annotation = clazz.getAnnotation(LDLRegister.class);
            if (!annotation.modID().isEmpty()) {
                if (!LDLib.isModLoaded(annotation.modID())) {
                    MBD2.LOGGER.info("Skipping registration of machine definition: " + clazz.getName() + " - Mod not loaded: " + annotation.modID());
                    return;
                }
            }
            MBDRegistries.MACHINE_DEFINITION_TYPES.register(clazz.getAnnotation(LDLRegister.class).name(),
                    new AnnotationDetector.Wrapper<>(annotation, clazz, creator));
        } else {
            MBD2.LOGGER.error("Failed to register machine definition: " + clazz.getName() + " - No annotation found");
        }
    }

}
