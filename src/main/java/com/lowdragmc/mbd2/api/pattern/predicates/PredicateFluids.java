package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import lombok.NoArgsConstructor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Predicate that accepts positions containing one of several fluid types.
 *
 * <p>The business goal is to let multiblock definitions require water, lava, or
 * modded fluids while exposing bucket candidates for previews and auto-build.
 * Instances are mutable through the editor and rebuild their matcher after
 * configuration changes.</p>
 */
@LDLRegister(name = "fluids", group = "predicate")
@NoArgsConstructor
public class PredicateFluids extends SimplePredicate {

    @Configurable(name = "config.predicate.fluids", tips = "config.predicate.fluids.tooltip", collapse = false)
    protected Fluid[] fluids = new Fluid[]{Fluids.WATER};

    /**
     * Creates a fluid predicate.
     *
     * @param fluids accepted fluid types; null entries are discarded during
     *               rebuild
     */
    public PredicateFluids(Fluid... fluids) {
        this.fluids = fluids;
        buildPredicate();
    }

    /**
     * Updates the accepted fluids from the editor.
     *
     * <p>Side effects: rebuilds the runtime predicate and bucket candidates.</p>
     *
     * @param fluids accepted fluid types
     */
    @ConfigSetter(field = "fluids")
    public void setFluids(Fluid[] fluids) {
        this.fluids = fluids;
        buildPredicate();
    }

    /**
     * Rebuilds the matcher and fluid candidates.
     *
     * <p>Side effects: removes null fluid entries, replaces an empty list with a
     * water fallback, and updates inherited preview state.</p>
     *
     * @return this predicate for chaining
     */
    @Override
    public SimplePredicate buildPredicate() {
        fluids = Arrays.stream(fluids).filter(Objects::nonNull).toArray(Fluid[]::new);
        if (fluids.length == 0) fluids = new Fluid[]{Fluids.WATER};
        predicate = state -> ArrayUtils.contains(fluids, state.getRepresentedBlockState().getFluidState().getType());
        candidates = Suppliers.memoize(() -> Arrays.stream(fluids).map(fluid -> new BlockInfo(fluid.defaultFluidState().createLegacyBlock(), false,
                fluid.getBucket().getDefaultInstance(), null)).toArray(BlockInfo[]::new));
        return super.buildPredicate();
    }

}
