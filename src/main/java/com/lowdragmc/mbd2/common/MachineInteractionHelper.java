package com.lowdragmc.mbd2.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Shared policy for item interactions that should bypass MBD's default machine UI opening.
 *
 * <p>The business goal is to allow compatibility tools to interact with machine blocks or entities even when those
 * machines normally open a UI on right-click. The helper is stateless and safe to call from either logical side; it
 * only reads the supplied stack's item registry id.</p>
 */
public final class MachineInteractionHelper {
    private static final ResourceLocation ARS_NOUVEAU_DOMINION_WAND = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "dominion_wand");

    /**
     * Utility class; not instantiable.
     */
    private MachineInteractionHelper() {
    }

    /**
     * Checks whether a held stack should receive normal item interaction instead of opening an MBD machine UI.
     *
     * @param stack held item stack; empty stacks are accepted
     * @return {@code true} for known compatibility tools, currently Ars Nouveau's Dominion Wand
     */
    public static boolean shouldBypassMachineUI(ItemStack stack) {
        return ARS_NOUVEAU_DOMINION_WAND.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
}
