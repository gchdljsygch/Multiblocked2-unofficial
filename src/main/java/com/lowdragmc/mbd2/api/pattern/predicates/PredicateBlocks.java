package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Predicate that accepts blocks by block type, ignoring individual block-state
 * properties.
 *
 * <p>The business goal is to provide a compact editor/configuration predicate
 * for structures where any state of the same block should count as valid.
 * Instances are mutable through the editor; rebuilding the predicate refreshes
 * preview and auto-build candidates.</p>
 */
@LDLRegister(name = "blocks", group = "predicate")
@NoArgsConstructor
public class PredicateBlocks extends SimplePredicate {

    @Configurable(name = "config.predicate.blocks", tips = "config.predicate.blocks.tooltip", collapse = false)
    protected Block[] blocks = new Block[]{Blocks.STONE};

    /**
     * Creates a block-type predicate.
     *
     * @param blocks accepted block types; null entries are discarded during
     *               rebuild
     */
    public PredicateBlocks(Block... blocks) {
        this.blocks = blocks;
        buildPredicate();
    }

    /**
     * Updates the accepted block types from the editor.
     *
     * <p>Side effects: rebuilds the runtime predicate and preview candidates.</p>
     *
     * @param blocks accepted block types
     */
    @ConfigSetter(field = "blocks")
    public void setBlocks(Block[] blocks) {
        this.blocks = blocks;
        buildPredicate();
    }

    /**
     * Rebuilds the matcher and block candidates.
     *
     * <p>Side effects: removes null block entries, replaces an empty list with a
     * barrier fallback so the predicate remains visible, and updates inherited
     * preview state.</p>
     *
     * @return this predicate for chaining
     */
    @Override
    public SimplePredicate buildPredicate() {
        blocks = Arrays.stream(blocks).filter(Objects::nonNull).toArray(Block[]::new);
        if (blocks.length == 0) blocks = new Block[]{Blocks.BARRIER};
        predicate = state -> ArrayUtils.contains(blocks, state.getBlockState().getBlock());
        candidates = Suppliers.memoize(() -> Arrays.stream(blocks).map(BlockInfo::fromBlock).toArray(BlockInfo[]::new));
        return super.buildPredicate();
    }

}
