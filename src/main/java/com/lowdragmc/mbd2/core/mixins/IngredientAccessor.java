package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for mutating the cached value arrays inside vanilla
 * {@link Ingredient}.
 *
 * <p>MBD2 uses this when copying or transforming recipe ingredients while preserving
 * vanilla's lazy matching caches. Callers must update values and item stacks consistently;
 * the accessor bypasses vanilla validation.</p>
 */
@Mixin(Ingredient.class)
public interface IngredientAccessor {
    /**
     * Returns the raw ingredient value array used to build matching stacks.
     */
    @Accessor
    Ingredient.Value[] getValues();

    /**
     * Replaces the raw ingredient value array.
     *
     * @param values replacement values; callers are responsible for also refreshing cached
     *               item stacks when needed
     */
    @Accessor
    @Mutable
    void setValues(Ingredient.Value[] values);

    /**
     * Replaces vanilla's cached matching item stacks.
     *
     * @param itemStacks cached stacks matching the current values
     */
    @Accessor
    @Mutable
    void setItemStacks(ItemStack[] itemStacks);
}
