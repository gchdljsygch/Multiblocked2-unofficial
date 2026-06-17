package com.lowdragmc.mbd2.common.trait.item;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.DefaultValue;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.utils.TagUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Toggleable item predicate used by item-slot traits.
 *
 * <p>When disabled, every stack is accepted. When enabled, configured item stacks and item tags are matched in order
 * and the first match returns the whitelist mode value; if no entry matches, the inverse of whitelist mode is
 * returned. All calls are read-only with respect to the tested stack.</p>
 */
public class ItemFilterSettings implements IToggleConfigurable, Predicate<ItemStack> {
    @Getter
    @Setter
    @Persisted
    private boolean enable;

    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.filter.whitelist")
    private boolean isWhitelist = true;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.filter.match_nbt")
    private boolean matchNBT = false;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.filter.items")
    private List<ItemStack> filterItems = new ArrayList<>();
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.filter.item_tags", forceUpdate = false)
    @DefaultValue(stringValue = "forge:ingots/iron")
    private List<ResourceLocation> filterTags = new ArrayList<>();

    /**
     * Tests whether an item stack passes the configured filter.
     *
     * @param itemStack stack to test; expected to be non-null
     * @return {@code true} when the stack is allowed by the current filter mode
     */
    @Override
    public boolean test(ItemStack itemStack) {
        if (!enable) {
            return true;
        }
        for (var filterItem : filterItems) {
            if (matchNBT) {
                if (ItemStack.isSameItemSameTags(filterItem, itemStack)) {
                    return isWhitelist;
                }
            } else if (ItemStack.isSameItem(filterItem, itemStack)) {
                return isWhitelist;
            }
        }
        for (var filterTag : filterTags) {
            if (itemStack.is(TagUtil.optionalTag(ForgeRegistries.ITEMS.getRegistryKey(), filterTag))) {
                return isWhitelist;
            }
        }
        return !isWhitelist;
    }
}
