package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.ATESRRendererProvider;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * @author KilaBash
 * @date 2022/11/3
 * @implNote TCRendererProvider
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MBDBESRenderer extends ATESRRendererProvider<BlockEntity> {
    private static MBDBESRenderer INSTANCE;

    private MBDBESRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Returns the singleton block-entity special renderer provider, creating it during renderer registration if needed.
     * <p>
     * This class has no per-context mutable state, so one provider instance is reused for every MBD machine block entity
     * renderer request on the client render thread.
     *
     * @param context renderer registration context supplied by Minecraft
     * @return singleton renderer provider
     */
    public static MBDBESRenderer getOrCreate(BlockEntityRendererProvider.Context context) {
        if (INSTANCE == null) {
            INSTANCE = new MBDBESRenderer(context);
        }
        return INSTANCE;
    }

    /**
     * Returns the existing renderer provider without creating one.
     *
     * @return singleton renderer provider, or {@code null} before renderer registration initializes it
     */
    @Nullable
    public static MBDBESRenderer getInstance() {
        return INSTANCE;
    }
}
