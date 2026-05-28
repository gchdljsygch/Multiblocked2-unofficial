package com.non_coffee.mbd2thread.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Mbd2ThreadConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        SPEC = builder.build();
    }

    private Mbd2ThreadConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.IntValue slowBuildBlocksPerTick;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("multiblock_builder");
            slowBuildBlocksPerTick = builder.defineInRange("slowBuildBlocksPerTick", 5, 5, Integer.MAX_VALUE);
            builder.pop();
        }
    }
}
