package com.lowdragmc.mbd2.utils;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import java.util.Objects;

/**
 * Describes one block or fluid placement selected while auto-building a
 * multiblock pattern.
 *
 * <p>The business goal is to carry the exact material source, expected block
 * state, and target coordinate from pattern analysis to the placement executor.
 * Instances are immutable data carriers and have no side effects after
 * construction. They are safe to share between threads only as immutable
 * snapshots; the contained {@link ItemStack} and {@link BlockInfo} should still
 * be treated according to Minecraft's normal game-thread ownership rules.</p>
 */
public final class PatternAutoBuildPlacement {
    /**
     * World position where the executor should place the block or fluid.
     */
    public final BlockPos pos;

    /**
     * Stack chosen as the placement material. Count is a template count; the
     * executor normally consumes or drains one placement unit from the recorded
     * source.
     */
    public final ItemStack found;

    /**
     * Player inventory slot that originally matched {@link #found}; {@code -1}
     * means the source is not a player inventory slot.
     */
    public final int foundSlot;

    /**
     * Pattern candidate that should be applied after the block/fluid is placed;
     * may be {@code null} when the executor only needs the item form.
     */
    public final BlockInfo expectedInfo;

    /**
     * Rotation between the pattern definition and the controller's current
     * facing. {@link Rotation#NONE} is used when no rotation is required.
     */
    public final Rotation rotation;

    /**
     * Whether {@link #expectedInfo}'s block state should be rotated before it is
     * applied to the world.
     */
    public final boolean rotateExpectedState;

    /**
     * Material source that determines whether the executor consumes inventory,
     * bound item storage, bound fluid storage, or creative-mode material.
     */
    public final Source source;

    /**
     * Optional slot index inside the source handler. Current callers use
     * {@code -1} when no handler-local slot was reserved.
     */
    public final int sourceSlot;

    /**
     * Creates a placement whose expected block state should rotate with the
     * pattern.
     *
     * @param pos          target world position; must be non-null
     * @param found        material stack selected for this placement; must be non-null
     * @param foundSlot    player inventory index in {@code [0, inventory size)} or
     *                     {@code -1} when not sourced from the player inventory
     * @param expectedInfo pattern state to apply after placement; may be
     *                     {@code null}
     * @param rotation     pattern rotation; {@code null} is normalized to
     *                     {@link Rotation#NONE}
     * @param source       material source; must be non-null
     * @param sourceSlot   source-handler slot in {@code [0, handler size)} or
     *                     {@code -1} when not reserved
     */
    public PatternAutoBuildPlacement(BlockPos pos, ItemStack found, int foundSlot, BlockInfo expectedInfo, Rotation rotation, Source source, int sourceSlot) {
        this(pos, found, foundSlot, expectedInfo, rotation, true, source, sourceSlot);
    }

    /**
     * Creates a placement plan entry consumed by auto-build execution.
     *
     * <p>Preconditions: {@code pos}, {@code found}, and {@code source} must be
     * non-null. The constructor performs no inventory or world validation and
     * has no side effects beyond storing references. Slot values are advisory;
     * the executor revalidates player inventory before consuming.</p>
     *
     * @param pos                 target world position; must be non-null
     * @param found               material stack selected for this placement; must be non-null
     * @param foundSlot           player inventory index in {@code [0, inventory size)} or
     *                            {@code -1} when not sourced from the player inventory
     * @param expectedInfo        pattern state to apply after placement; may be
     *                            {@code null}
     * @param rotation            pattern rotation; {@code null} is normalized to
     *                            {@link Rotation#NONE}
     * @param rotateExpectedState whether to rotate {@code expectedInfo} before
     *                            applying it
     * @param source              material source; must be non-null
     * @param sourceSlot          source-handler slot in {@code [0, handler size)} or
     *                            {@code -1} when not reserved
     */
    public PatternAutoBuildPlacement(BlockPos pos, ItemStack found, int foundSlot, BlockInfo expectedInfo, Rotation rotation, boolean rotateExpectedState, Source source, int sourceSlot) {
        this.pos = Objects.requireNonNull(pos);
        this.found = Objects.requireNonNull(found);
        this.foundSlot = foundSlot;
        this.expectedInfo = expectedInfo;
        this.rotation = rotation == null ? Rotation.NONE : rotation;
        this.rotateExpectedState = rotateExpectedState;
        this.source = Objects.requireNonNull(source);
        this.sourceSlot = sourceSlot;
    }

    /**
     * Origin of the material used by a placement plan.
     */
    public enum Source {
        /**
         * Consume one matching item or bucket from the player's inventory.
         */
        PLAYER_INVENTORY,

        /**
         * Extract one matching block item from the builder's bound item handler.
         */
        BOUND_ITEM_HANDLER,

        /**
         * Drain the required fluid from the builder's bound fluid handler.
         */
        BOUND_FLUID_HANDLER,

        /**
         * Use the selected material without consuming player or bound storage.
         */
        CREATIVE
    }
}
