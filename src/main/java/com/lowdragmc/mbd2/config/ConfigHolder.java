package com.lowdragmc.mbd2.config;

import com.lowdragmc.mbd2.MBD2;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * @author KilaBash
 * @date 2023/2/14
 * @implNote ConfigHolder
 */
@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigHolder {
    public static ConfigHolder INSTANCE;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ASYNC_RECIPE_SEARCHING = BUILDER
            .comment("Whether search for recipes asynchronously.")
            .define("asyncRecipeSearching", true);

    private static final ForgeConfigSpec.BooleanValue USE_VBO = BUILDER
            .comment("Whether use vbo for preview page rendering.")
            .define("useVBO", true);

    private static final ForgeConfigSpec.IntValue MULTIBLOCK_PREVIEW_DURATION = BUILDER
            .comment("Duration of the multiblock in-world preview (s)")
            .defineInRange("multiblockPreviewDuration", 10, 1, 999);

    private static final ForgeConfigSpec.IntValue MULTIBLOCK_PATTERN_ERROR_DURATION = BUILDER
            .comment("Duration of the multiblock in-world pattern error position (s)")
            .defineInRange("multiblockPatternErrorPosDuration", 10, 1, 999);

    private static final ForgeConfigSpec.IntValue SLOW_BUILD_BLOCKS_PER_TICK = BUILDER
            .comment("Blocks placed per tick when using slow multiblock auto-build.")
            .defineInRange("slowBuildBlocksPerTick", 5, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean asyncRecipeSearching;

    public static boolean useVBO;

    public static int multiblockPreviewDuration;
    public static int multiblockPatternErrorPosDuration;
    public static int slowBuildBlocksPerTick;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        asyncRecipeSearching = ASYNC_RECIPE_SEARCHING.get();
        useVBO = USE_VBO.get();
        multiblockPreviewDuration = MULTIBLOCK_PREVIEW_DURATION.get();
        multiblockPatternErrorPosDuration = MULTIBLOCK_PATTERN_ERROR_DURATION.get();
        slowBuildBlocksPerTick = SLOW_BUILD_BLOCKS_PER_TICK.get();
    }
}
