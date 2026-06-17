package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for the item stack stored by vanilla
 * {@link Ingredient.ItemValue}.
 *
 * <p>The accessor is used by ingredient copy/normalization code that needs to replace the
 * stack without rebuilding the surrounding {@link Ingredient.Value} object. It bypasses
 * vanilla immutability assumptions and should only be used while constructing recipe data.</p>
 */
@Mixin(Ingredient.ItemValue.class)
public interface ItemValueAccessor {
    /**
     * Returns the item stack represented by the item-value entry.
     */
    @Accessor
    ItemStack getItem();

    /**
     * Replaces the item stack represented by the item-value entry.
     *
     * @param item replacement stack
     */
    @Accessor
    @Mutable
    void setItem(ItemStack item);
}
