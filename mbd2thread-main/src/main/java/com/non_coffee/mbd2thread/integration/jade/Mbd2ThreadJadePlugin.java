package com.non_coffee.mbd2thread.integration.jade;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class Mbd2ThreadJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new RecipeThreadRecipeLogicDataProvider(), BlockEntity.class);
        registration.registerBlockDataProvider(new LongFeEnergyDataProvider(), BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new RecipeThreadRecipeLogicDataProvider(), Block.class);
        registration.registerBlockComponent(new LongFeEnergyDataProvider(), Block.class);
    }
}
