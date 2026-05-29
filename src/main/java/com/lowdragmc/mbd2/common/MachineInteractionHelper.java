package com.lowdragmc.mbd2.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class MachineInteractionHelper {
    private static final ResourceLocation ARS_NOUVEAU_DOMINION_WAND = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "dominion_wand");

    private MachineInteractionHelper() {
    }

    public static boolean shouldBypassMachineUI(ItemStack stack) {
        return ARS_NOUVEAU_DOMINION_WAND.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
}
