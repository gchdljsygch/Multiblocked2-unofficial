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

/**
 * Fixes LDLib fluid-stack network deserialization for long fluid amounts.
 *
 * <p>MBD fluid capabilities can store amounts beyond an {@code int}. LDLib writes those amounts
 * as VarLong values, so this overwrite mirrors the writer exactly and avoids truncating recipe or
 * trait fluid counts during GUI synchronization.</p>
 */
@Mixin(FluidStack.class)
public abstract class FluidStackMixin {
    /**
     * Reads a fluid stack from LDLib's network buffer using a long amount field.
     *
     * @param buf buffer positioned at a serialized LDLib fluid stack
     * @return decoded fluid stack, or {@link FluidStack#empty()} when the fluid id is empty
     * @author pingsu
     * @reason Fixes long amount deserialization to match writeToBuf().
     */
    @Overwrite(remap = false)
    public static FluidStack readFromBuf(FriendlyByteBuf buf) {
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(buf.readUtf()));
        long amount = buf.readVarLong();
        CompoundTag tag = buf.readNbt();
        if (fluid == Fluids.EMPTY) {
            return FluidStack.empty();
        }
        return FluidStack.create(fluid, amount, tag);
    }
}
