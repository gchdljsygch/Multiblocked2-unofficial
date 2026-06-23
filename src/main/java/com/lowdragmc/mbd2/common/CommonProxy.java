package com.lowdragmc.mbd2.common;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.block.ProxyPartBlock;
import com.lowdragmc.mbd2.api.blockentity.ProxyPartBlockEntity;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeSerializer;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.ingredient.SizedIngredient;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.data.MBDMachineDefinitionTypes;
import com.lowdragmc.mbd2.common.data.MBDRecipeCapabilities;
import com.lowdragmc.mbd2.common.data.MBDRecipeConditions;
import com.lowdragmc.mbd2.common.data.MBDTraitDefinitionTypes;
import com.lowdragmc.mbd2.common.event.MBDRegistryEvent;
import com.lowdragmc.mbd2.common.gui.factory.EntityMachineUIFactory;
import com.lowdragmc.mbd2.common.gui.factory.MachineUIFactory;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.common.network.MBD2Network;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.lowdragmc.mbd2.integration.create.machine.CreateKineticMachineDefinition;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDMachineRegistryEventJS;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDRecipeTypeRegistryEventJS;
import com.lowdragmc.mbd2.integration.kubejs.events.MBDStartupEvents;
import com.lowdragmc.mbd2.test.MBDTest;
import com.lowdragmc.mbd2.utils.FileUtils;
import dev.latvian.mods.kubejs.script.ScriptType;
import lombok.Getter;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Common-side bootstrap for registries, project loading, network setup, and
 * Forge lifecycle hooks.
 *
 * <p>The business goal is to make project-authored machines and recipe types
 * available to both server and client distributions. Methods in this class run
 * during Forge mod loading or registry events; they mutate Forge registries,
 * MBD registries, config/UI factories, and deferred post-load tasks and should
 * remain on the corresponding Forge event thread.</p>
 */
public class CommonProxy {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MBD2.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MBD2.MOD_ID);

    @Getter
    private static final ConcurrentLinkedDeque<Runnable> postTask = new ConcurrentLinkedDeque<>();


    /**
     * Registers common resources and event listeners for the mod instance.
     *
     * <p>Preconditions: called during mod construction before registry events
     * fire. Side effects: creates the workspace directory, registers this proxy
     * on the mod event bus, initializes networking, registers config/UI
     * factories, and queues deferred block/block-entity registrations. In a
     * development environment it also registers the test event listener.</p>
     */
    public CommonProxy() {
        MBD2.getLocation();
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.register(this);
        if (Platform.isDevEnv()) {
            eventBus.register(new MBDTest());
        }
        MBD2Network.init();
        ForgeRegistries.RECIPE_SERIALIZERS.register("mbd_recipe_serializer", MBDRecipeSerializer.SERIALIZER);
        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHolder.SPEC);
        // Register UI Factory
        UIFactory.register(MachineUIFactory.INSTANCE);
        UIFactory.register(EntityMachineUIFactory.INSTANCE);
        // Register blocks
        BLOCKS.register("proxy_part_block", () -> ProxyPartBlock.BLOCK);
        ProxyPartBlockEntity.TYPE = BLOCK_ENTITY_TYPES.register("proxy_part_block", () -> BlockEntityType.Builder.of(ProxyPartBlockEntity::new, ProxyPartBlock.BLOCK).build(null));
        BLOCKS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }

    /**
     * Loads recipe type definitions from the workspace and opens extension
     * points before freezing the registry.
     *
     * <p>Business goal: merge project files, Forge registry events, and optional
     * KubeJS startup scripts into {@link MBDRegistries#RECIPE_TYPES}.
     * Preconditions: recipe condition/capability infrastructure should already
     * be initialized by the construct event. Side effects: unfreezes and freezes
     * the recipe type registry, reads {@code .rt} files, enqueues post-load
     * tasks through project deserialization, posts an MBD registry event, and
     * posts a KubeJS event when KubeJS is loaded.</p>
     */
    public void registerRecipeType() {
        MBDRegistries.RECIPE_TYPES.unfreeze();
        var event = new MBDRegistryEvent.MBDRecipeType();
        MBD2.LOGGER.info("Loading recipe types");
        var path = new File(MBD2.getLocation(), "recipe_type");
        //load recipe type
        FileUtils.loadNBTFiles(path, ".rt", (file, tag) -> event.register(MBDRecipeType.createDefault().loadProductiveTag(file, tag, postTask)));
        ModLoader.get().postEvent(event);
        if (MBD2.isKubeJSLoaded()) {
            KubeJSWrapper.postRecipeTypeEvent();
        }
        MBDRegistries.RECIPE_TYPES.freeze();
    }

    /**
     * Loads machine definitions from the workspace and opens extension points
     * before freezing the machine registry.
     *
     * <p>Business goal: make single-block, multiblock, entity, and optional
     * Create kinetic machines available to registry and factory loading.
     * Preconditions: machine definition types and trait types should already be
     * initialized. Side effects: unfreezes and freezes the machine definition
     * registry, reads project files from the mod workspace, posts an MBD
     * registry event, and posts a KubeJS startup event when available.</p>
     */
    public void registerMachine() {
        MBDRegistries.MACHINE_DEFINITIONS.unfreeze();
        var event = new MBDRegistryEvent.Machine();
        MBD2.LOGGER.info("Loading machines");
        var path = new File(MBD2.getLocation(), "machine");
        // load single machine
        FileUtils.loadNBTFiles(path, ".sm", (file, tag) -> event.register(MBDMachineDefinition.createDefault().loadProductiveTag(file, tag, postTask)));
        // load multiblock machine
        path = new File(MBD2.getLocation(), "multiblock");
        FileUtils.loadNBTFiles(path, ".mb", (file, tag) -> event.register(MultiblockMachineDefinition.createDefault().loadProductiveTag(file, tag, postTask)));
        // load entity machine definitions.
        path = new File(MBD2.getLocation(), "entity_machine");
        FileUtils.loadNBTFiles(path, ".em", (file, tag) -> event.register(EntityMachineDefinition.createDefault().loadProductiveTag(file, tag, postTask)));
        if (MBD2.isCreateLoaded()) {
            // load kinetic machine
            path = new File(MBD2.getLocation(), "kinetic_machine");
            FileUtils.loadNBTFiles(path, ".km", (file, tag) -> event.register(CreateKineticMachineDefinition.createDefault().loadProductiveTag(file, tag, postTask)));
        }
        ModLoader.get().postEvent(event);
        if (MBD2.isKubeJSLoaded()) {
            KubeJSWrapper.postMachineEvent();
        }
        MBDRegistries.MACHINE_DEFINITIONS.freeze();
    }

    /**
     * Isolates KubeJS-only startup event calls so the common proxy can be loaded
     * without resolving KubeJS classes when the dependency is absent.
     */
    public static class KubeJSWrapper {
        /**
         * Posts the machine registry startup event to KubeJS scripts.
         *
         * <p>Preconditions: KubeJS must be loaded. Side effects: registers
         * builder factories exposed to scripts and dispatches the startup event.</p>
         */
        public static void postMachineEvent() {
            MBDMachineRegistryEventJS.BUILDERS.put("single", MBDMachineDefinition::builder);
            MBDMachineRegistryEventJS.BUILDERS.put("multiblock", MultiblockMachineDefinition::builder);
            MBDMachineRegistryEventJS.BUILDERS.put("entity", EntityMachineDefinition::builder);
            if (MBD2.isCreateLoaded()) {
                MBDMachineRegistryEventJS.BUILDERS.put("kinetic", CreateKineticMachineDefinition::builder);
            }
            MBDStartupEvents.MACHINE.post(ScriptType.STARTUP, new MBDMachineRegistryEventJS());
        }

        /**
         * Posts the recipe type registry startup event to KubeJS scripts.
         *
         * <p>Preconditions: KubeJS must be loaded. Side effects: dispatches the
         * startup event for script-authored recipe types.</p>
         */
        public static void postRecipeTypeEvent() {
            MBDStartupEvents.RECIPE_TYPE.post(ScriptType.STARTUP, new MBDRecipeTypeRegistryEventJS());
        }
    }

    /**
     * Initializes MBD registries and loads project-authored definitions during
     * mod construction.
     *
     * <p>Business goal: prepare all recipe conditions, recipe capabilities,
     * machine definition types, and trait definition types before reading
     * workspace projects. Side effects: enqueues work on Forge's mod loading
     * queue and populates the recipe type and machine definition registries.</p>
     *
     * @param e Forge construct mod event
     */
    @SubscribeEvent
    public void constructMod(FMLConstructModEvent e) {
        e.enqueueWork(() -> {
            MBDRecipeConditions.init();
            MBDRecipeCapabilities.init();
            MBDMachineDefinitionTypes.init();
            MBDTraitDefinitionTypes.init();
            registerRecipeType();
            registerMachine();
        });
    }

    /**
     * Performs common setup after construction.
     *
     * <p>Preconditions: called by Forge during common setup. Side effects:
     * registers the custom sized-ingredient serializer with Forge's crafting
     * helper. The current enqueued work block is intentionally empty.</p>
     *
     * @param e Forge common setup event
     */
    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
        });
        CraftingHelper.register(SizedIngredient.TYPE, SizedIngredient.SERIALIZER);
    }

    /**
     * Completes factory loading after all registries and project files are
     * available.
     *
     * <p>Business goal: build runtime factories for fake and project-defined
     * machines after registry data has settled. Side effects: loads factories,
     * runs pending post-load tasks, and clears the shared post-task queue.</p>
     *
     * @param e Forge load complete event
     */
    @SubscribeEvent
    public void loadComplete(FMLLoadCompleteEvent e) {
        e.enqueueWork(() -> {
            MBDRegistries.FAKE_MACHINE().loadFactory();
            MBDRegistries.MACHINE_DEFINITIONS.forEach(MBDMachineDefinition::loadFactory);
            postTask.forEach(Runnable::run);
            postTask.clear();
        });
    }

    /**
     * Registers custom Forge capabilities used by machines and traits.
     *
     * @param event capability registration event supplied by Forge
     */
    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        MBDCapabilities.register(event);
    }

    /**
     * Registers entity attributes for machine definitions backed by entities.
     *
     * <p>Preconditions: machine definitions must already be loaded. Side effects:
     * forwards the Forge event to each entity machine definition.</p>
     *
     * @param event Forge entity attribute creation event
     */
    @SubscribeEvent
    public void registerEntityAttributes(EntityAttributeCreationEvent event) {
        MBDRegistries.MACHINE_DEFINITIONS.forEach(definition -> {
            if (definition instanceof EntityMachineDefinition entityDefinition) {
                entityDefinition.registerEntityAttributes(event);
            }
        });
    }

    /**
     * Lets fake and project-defined machines contribute their blocks, block
     * entities, items, and other registry objects to Forge registries.
     *
     * <p>Side effects: delegates registry population to machine definitions and
     * registers the built-in gadget and selection export tool items.</p>
     *
     * @param event Forge registry event for the currently active registry
     */
    @SubscribeEvent
    public void register(RegisterEvent event) {
        MBDRegistries.FAKE_MACHINE().onRegistry(event);
        MBDRegistries.MACHINE_DEFINITIONS.forEach((definition) -> definition.onRegistry(event));
        // register items
        event.register(ForgeRegistries.ITEMS.getRegistryKey(), helper -> {
            helper.register(MBD2.id("mbd_gadgets"), MBDRegistries.GADGETS_ITEM());
            helper.register(MBD2.id("mbd_selection_export_tool"), MBDRegistries.SELECTION_EXPORT_TOOL_ITEM());
            helper.register(MBD2.id("mbd_disassembly_tool"), MBDRegistries.DISASSEMBLY_TOOL_ITEM());
        });
    }

    /**
     * Adds visible machine and utility items to creative tabs.
     *
     * <p>Preconditions: item definitions and creative tab settings are loaded.
     * Side effects: accepts eligible machine items into their configured tab and
     * adds built-in tools to the redstone blocks tab.</p>
     *
     * @param event creative tab population event
     */
    @SubscribeEvent
    public void buildContents(BuildCreativeModeTabContentsEvent event) {
        var tabLoc = event.getTabKey().location();
        for (var machineDefinition : MBDRegistries.MACHINE_DEFINITIONS) {
            if (machineDefinition.item() != null &&
                    machineDefinition.itemProperties().creativeTab().isEnable() &&
                    tabLoc.equals(machineDefinition.itemProperties().creativeTab().getValue())) {
                event.accept(machineDefinition.item());
            }
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(MBDRegistries.GADGETS_ITEM());
            event.accept(MBDRegistries.SELECTION_EXPORT_TOOL_ITEM());
            event.accept(MBDRegistries.DISASSEMBLY_TOOL_ITEM());
        }
    }
}
