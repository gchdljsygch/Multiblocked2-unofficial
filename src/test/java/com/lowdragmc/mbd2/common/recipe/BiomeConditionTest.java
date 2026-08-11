package com.lowdragmc.mbd2.common.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BiomeConditionTest {

    @Test
    void tooltipKeepsBiomeNameAsTranslatableComponent() {
        var tooltip = new BiomeCondition(ResourceLocation.parse("minecraft:plains")).getTooltips();

        var tooltipTranslation = assertInstanceOf(TranslatableContents.class, tooltip.getContents());
        assertEquals("recipe.condition.biome.tooltip", tooltipTranslation.getKey());
        assertEquals(1, tooltipTranslation.getArgs().length);
        assertBiomeTranslation(tooltipTranslation.getArgs()[0], "biome.minecraft.plains", "minecraft:plains");
    }

    @Test
    void tooltipNormalizesNestedBiomePathAndKeepsRegistryNameFallback() {
        var biome = ResourceLocation.fromNamespaceAndPath("examplemod", "climate/frozen_peaks");
        var tooltip = new BiomeCondition(biome).getTooltips();
        var tooltipTranslation = assertInstanceOf(TranslatableContents.class, tooltip.getContents());

        assertBiomeTranslation(
                tooltipTranslation.getArgs()[0],
                "biome.examplemod.climate.frozen_peaks",
                "examplemod:climate/frozen_peaks"
        );
    }

    private static void assertBiomeTranslation(Object argument, String expectedKey, String expectedFallback) {
        var biomeName = assertInstanceOf(Component.class, argument);
        var biomeTranslation = assertInstanceOf(TranslatableContents.class, biomeName.getContents());
        assertEquals(expectedKey, biomeTranslation.getKey());
        assertEquals(expectedFallback, biomeTranslation.getFallback());
    }
}
