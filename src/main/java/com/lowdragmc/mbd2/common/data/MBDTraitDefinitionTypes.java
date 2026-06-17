package com.lowdragmc.mbd2.common.data;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.runtime.AnnotationDetector;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.event.MBDRegistryEvent;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.lowdragmc.mbd2.common.trait.entity.EntityHandlerTraitDefinition;
import com.lowdragmc.mbd2.common.trait.fluid.FluidTankCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.ForgeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.redstone.RedstoneSignalCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.LongFeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.ae2.trait.MEInterfaceTraitDefinition;
import com.lowdragmc.mbd2.integration.ae2.trait.MEPatternInputTraitDefinition;
import com.lowdragmc.mbd2.integration.arsnouveau.trait.ArsNouveauSourceCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.bloodmagic.trait.BloodMagicSoulNetworkTraitDefinition;
import com.lowdragmc.mbd2.integration.bloodmagic.trait.BloodMagicWillTraitDefinition;
import com.lowdragmc.mbd2.integration.botania.trait.BotaniaManaCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.embers.trait.EmbersEmberCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.gtm.trait.GTMEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.manaandartifice.trait.ManaAndArtificeEldrinCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.mekanism.trait.chemical.ChemicalTankCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.mekanism.trait.heat.MekHeatCapabilityTraitDefinition;
import com.lowdragmc.mbd2.integration.naturesaura.trait.AuraHandlerTraitDefinition;
import com.lowdragmc.mbd2.integration.pneumaticcraft.trait.pressure.PNCPressureAirHandlerTraitDefinition;
import com.lowdragmc.mbd2.integration.pneumaticcraft.trait.heat.PNCHeatExchangerTraitDefinition;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTraitDefinition;
import net.minecraftforge.fml.ModLoader;

/**
 * Startup registrar for trait definition implementation types.
 * <p>
 * Trait definition types are the editor/load-time factories used by machine
 * settings to create concrete traits. Built-in capability traits are registered
 * first, optional integration traits are registered when their owning mods are
 * present, and addons can contribute more types through
 * {@link MBDRegistryEvent.TraitType}.
 * <p>
 * Thread safety: call {@link #init()} only during mod loading. The backing
 * registry is intentionally unfrozen only for the duration of initialization.
 */
public class MBDTraitDefinitionTypes {

    /**
     * Registers built-in, optional integration, and addon trait definition
     * types.
     */
    public static void init() {
        MBDRegistries.TRAIT_DEFINITION_TYPES.unfreeze();
        register(ItemSlotCapabilityTraitDefinition.class);
        register(FluidTankCapabilityTraitDefinition.class);
        register(ForgeEnergyCapabilityTraitDefinition.class);
        register(LongFeEnergyCapabilityTraitDefinition.class);
        register(RedstoneSignalCapabilityTraitDefinition.class);
        register(EntityHandlerTraitDefinition.class);
        register(RecipeThreadTraitDefinition.class);
        // Register the mod capabilities
        if (MBD2.isBotaniaLoaded()) {
            register(BotaniaManaCapabilityTraitDefinition.class);
        }
        if (MBD2.isArsNouveauLoaded()) {
            register(ArsNouveauSourceCapabilityTraitDefinition.class);
        }
        if (MBD2.isGTMLoaded()) {
            register(GTMEnergyCapabilityTraitDefinition.class);
        }
        if (MBD2.isMekanismLoaded()) {
            register(ChemicalTankCapabilityTraitDefinition.Gas.class);
            register(ChemicalTankCapabilityTraitDefinition.Infuse.class);
            register(ChemicalTankCapabilityTraitDefinition.Pigment.class);
            register(ChemicalTankCapabilityTraitDefinition.Slurry.class);
            register(MekHeatCapabilityTraitDefinition.class);
        }
        if (MBD2.isNaturesAuraLoaded()) {
            register(AuraHandlerTraitDefinition.class);
        }
        if (MBD2.isPneumaticCraftLoaded()) {
            register(PNCPressureAirHandlerTraitDefinition.class);
            register(PNCHeatExchangerTraitDefinition.class);
        }
        if (MBD2.isEmbersLoaded()) {
            register(EmbersEmberCapabilityTraitDefinition.class);
        }
        if (MBD2.isAE2Loaded()) {
            register(MEInterfaceTraitDefinition.class);
            register(MEPatternInputTraitDefinition.class);
        }
        if (MBD2.isBloodMagicLoaded()) {
            register(BloodMagicWillTraitDefinition.class);
            register(BloodMagicSoulNetworkTraitDefinition.class);
        }
        if (MBD2.isManaAndArtificeLoaded()) {
            register(ManaAndArtificeEldrinCapabilityTraitDefinition.class);
        }
        ModLoader.get().postEvent(new MBDRegistryEvent.TraitType());
        MBDRegistries.TRAIT_DEFINITION_TYPES.freeze();
    }

    /**
     * Registers one trait definition class.
     * <p>
     * The class must be annotated with {@link LDLRegister} and must expose a
     * default constructor. If the annotation declares a required mod id and that
     * mod is missing, registration is skipped.
     *
     * @param clazz trait definition implementation class
     */
    public static void register(Class<? extends TraitDefinition> clazz) {
        if (clazz.isAnnotationPresent(LDLRegister.class)) {
            var annotation = clazz.getAnnotation(LDLRegister.class);
            if (!annotation.modID().isEmpty()) {
                if (!LDLib.isModLoaded(annotation.modID())) {
                    MBD2.LOGGER.info("Skipping registration of trait definition: " + clazz.getName() + " - Mod not loaded: " + annotation.modID());
                    return;
                }
            }
            try {
                var constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                MBDRegistries.TRAIT_DEFINITION_TYPES.register(clazz.getAnnotation(LDLRegister.class).name(),
                        new AnnotationDetector.Wrapper<>(annotation, clazz, () -> {
                            try {
                                return constructor.newInstance();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }));
            } catch (NoSuchMethodException e) {
                MBD2.LOGGER.error("Failed to register trait definition: " + clazz.getName() + " - No default constructor found");
            }
        } else {
            MBD2.LOGGER.error("Failed to register trait definition: " + clazz.getName() + " - No annotation found");
        }
    }


}
