package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the stack stored in Forge's {@link StrictNBTIngredient}.
 *
 * <p>The accessor is used when copying or transforming recipe ingredients that must keep
 * exact NBT matching. It bypasses the ingredient's normal immutability contract and should
 * only be used while constructing recipe data.</p>
 */
@Mixin(StrictNBTIngredient.class)
public interface StrictNBTIngredientAccessor {
    /**
     * Returns the exact stack used by the strict ingredient.
     */
    @Accessor
    ItemStack getStack();

    /**
     * Replaces the exact stack used by the strict ingredient.
     *
     * @param stack replacement stack
     */
    @Accessor
    @Mutable
    void setStack(ItemStack stack);
}
