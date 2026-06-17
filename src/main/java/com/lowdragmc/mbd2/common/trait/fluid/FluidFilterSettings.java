package com.lowdragmc.mbd2.common.trait.fluid;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.DefaultValue;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.utils.TagUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Toggleable fluid predicate used by fluid-tank traits.
 *
 * <p>When disabled, every fluid stack is accepted. When enabled, configured fluid stacks and fluid tags are matched
 * in order and the first match returns the whitelist mode value; if no entry matches, the inverse of whitelist mode
 * is returned. Matching can include NBT through {@link FluidStack#isFluidEqual(FluidStack)} or compare only the fluid
 * type.</p>
 */
public class FluidFilterSettings implements IToggleConfigurable, Predicate<FluidStack> {
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
    @Configurable(name = "config.definition.trait.filter.fluids")
    private List<FluidStack> filterFluids = new ArrayList<>();
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.filter.fluid_tags", forceUpdate = false)
    @DefaultValue(stringValue = "forge:gaseous")
    private List<ResourceLocation> filterTags = new ArrayList<>();

    /**
     * Tests whether a fluid stack passes the configured filter.
     *
     * @param fluidStack stack to test; expected to be non-null
     * @return {@code true} when the stack is allowed by the current filter mode
     */
    @Override
    public boolean test(FluidStack fluidStack) {
        if (!enable) {
            return true;
        }
        for (var filterFluids : filterFluids) {
            if (matchNBT) {
                if (filterFluids.isFluidEqual(fluidStack)) {
                    return isWhitelist;
                }
            } else if (filterFluids.getFluid() == fluidStack.getFluid()) {
                return isWhitelist;
            }
        }
        for (var filterTag : filterTags) {
            if (fluidStack.getFluid().is(TagUtil.optionalTag(ForgeRegistries.FLUIDS.getRegistryKey(), filterTag))) {
                return isWhitelist;
            }
        }
        return !isWhitelist;
    }
}
