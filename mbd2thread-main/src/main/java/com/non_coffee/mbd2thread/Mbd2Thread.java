package com.non_coffee.mbd2thread;

import com.lowdragmc.mbd2.common.event.MBDRegistryEvent;
import com.non_coffee.mbd2thread.config.Mbd2ThreadConfig;
import com.non_coffee.mbd2thread.energy.fe.api.ILongFeEnergyContainer;
import com.non_coffee.mbd2thread.energy.fe.recipe.LongFeRecipeCapability;
import com.non_coffee.mbd2thread.energy.fe.trait.LongFeEnergyCapabilityTraitDefinition;
import com.non_coffee.mbd2thread.network.Mbd2ThreadNetwork;
import com.non_coffee.mbd2thread.trait.RecipeThreadTraitDefinition;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Mbd2Thread.MOD_ID)
public final class Mbd2Thread
{
    public static final String MOD_ID = "mbd2thread";
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public Mbd2Thread(FMLJavaModLoadingContext context)
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Mbd2ThreadConfig.SPEC);
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::registerTraits);
        modEventBus.addListener(this::registerRecipeCapabilities);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Mbd2Thread common setup");
        event.enqueueWork(Mbd2ThreadNetwork::init);
    }

    private void registerTraits(MBDRegistryEvent.TraitType event) {
        LOGGER.info("Registering Mbd2Thread traits");
        event.register(RecipeThreadTraitDefinition.class);
        event.register(LongFeEnergyCapabilityTraitDefinition.class);
    }

    private void registerRecipeCapabilities(MBDRegistryEvent.RecipeCapability event) {
        event.register("mbd2thread_long_fe", LongFeRecipeCapability.CAP);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ILongFeEnergyContainer.class);
    }
}
