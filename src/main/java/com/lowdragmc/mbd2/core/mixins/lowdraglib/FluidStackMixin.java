package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FluidStack.class)
public abstract class FluidStackMixin {
    /**
     * @author pingsu
     * @reason Fixes long amount deserialization to match writeToBuf().
     */
    @Overwrite(remap = false)
    public static FluidStack readFromBuf(FriendlyByteBuf buf) {
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(buf.readUtf()));
        long amount = buf.readVarLong();
        CompoundTag tag = buf.readNbt();
        if (fluid == Fluids.EMPTY) {
            return FluidStack.empty();
        }
        return FluidStack.create(fluid, amount, tag);
    }
}
