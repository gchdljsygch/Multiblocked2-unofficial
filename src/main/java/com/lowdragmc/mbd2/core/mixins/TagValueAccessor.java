package com.lowdragmc.mbd2.core.mixins;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the item tag stored by vanilla {@link Ingredient.TagValue}.
 *
 * <p>MBD2 uses this when copying or retargeting tag-based ingredients. The setter bypasses
 * vanilla's normal final-field immutability and should be paired with ingredient cache
 * updates when used on an already-created {@link Ingredient}.</p>
 */
@Mixin(Ingredient.TagValue.class)
public interface TagValueAccessor {
    /**
     * Returns the item tag represented by the tag-value entry.
     */
    @Accessor
    TagKey<Item> getTag();

    /**
     * Replaces the item tag represented by the tag-value entry.
     *
     * @param item replacement item tag
     */
    @Accessor
    @Mutable
    void setTag(TagKey<Item> item);
}
