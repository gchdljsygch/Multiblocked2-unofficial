package com.lowdragmc.mbd2.utils;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Persists and resolves the per-item state used by the multiblock builder.
 *
 * <p>The business goal is to let a builder gadget remember build mode,
 * selected pattern, and external material sources so auto-build can consume
 * blocks or fluids without asking the player to reselect those sources.
 * Mutating methods write directly to the supplied {@link ItemStack}'s NBT;
 * callers should invoke them on the logical game thread that owns the stack and
 * level. The helper itself keeps no shared mutable state.</p>
 */
public final class BuilderMaterialBindings {
    private static final String AE2_ITEM_HANDLER_BRIDGE = "com.lowdragmc.mbd2.integration.ae2.MEStorageItemHandlers";
    private static final String KEY_ITEM_DIM = "mbd2_bind_item_dim";
    private static final String KEY_ITEM_POS = "mbd2_bind_item_pos";
    private static final String KEY_ITEM_SOURCE_TYPE = "mbd2_bind_item_source_type";
    private static final String KEY_FLUID_DIM = "mbd2_bind_fluid_dim";
    private static final String KEY_FLUID_POS = "mbd2_bind_fluid_pos";
    private static final String ITEM_SOURCE_TYPE_ME = "me";
    /**
     * NBT key used to store whether the builder should schedule placements over
     * multiple server ticks instead of placing everything in one pass.
     */
    public static final String KEY_SLOW_BUILD = "mbd2_slow_build";
    private static final String KEY_PATTERN_INDEX = "mbd2_pattern_index";

    private BuilderMaterialBindings() {
    }

    /**
     * Finds the builder gadget currently held by a player.
     *
     * <p>Preconditions: the player must be non-null and its hand stacks must be
     * readable on the owning game thread. Side effects: none.</p>
     *
     * @param player player whose main hand is checked before the off hand
     * @return the first held multiblock builder, or {@link ItemStack#EMPTY} when
     * no held stack is a builder; never {@code null}
     */
    public static ItemStack findBuilderStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isBuilder(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isBuilder(off)) return off;
        return ItemStack.EMPTY;
    }

    /**
     * Tests whether a stack is the multiblock-builder mode of {@link MBDGadgetsItem}.
     *
     * <p>Preconditions: none; {@code null} and empty stacks are accepted. Side
     * effects: none. Thread safety follows the supplied stack's owner.</p>
     *
     * @param stack candidate stack to inspect
     * @return {@code true} only when the stack is a gadget item configured as a
     * multiblock builder
     */
    public static boolean isBuilder(ItemStack stack) {
        if (!isGadget(stack)) return false;
        var gadgets = (MBDGadgetsItem) stack.getItem();
        return gadgets.isMultiblockBuilder(stack);
    }

    /**
     * Reads the builder's slow-build flag used to choose between immediate and
     * scheduled auto-build placement.
     *
     * <p>Invalid or empty stacks are treated as disabled. Side effects: none;
     * the stack tag is read but not created. The saved builder option is readable
     * from every MBD gadget mode so the mode wheel keeps the same state while a
     * debugger mode is selected.</p>
     *
     * @param builder stack expected to be a multiblock builder
     * @return {@code true} when the builder stores the slow-build flag,
     * otherwise {@code false}
     */
    public static boolean isSlowBuild(ItemStack builder) {
        if (!isGadget(builder)) return false;
        var tag = builder.getTag();
        return tag != null && tag.getBoolean(KEY_SLOW_BUILD);
    }

    /**
     * Stores whether a builder should place matched pattern entries gradually.
     *
     * <p>Preconditions: the stack must be a live builder stack owned by the
     * calling game thread. Non-gadget stacks are ignored. Side effects: creates
     * or updates the builder NBT tag.</p>
     *
     * @param builder   builder stack whose mode is updated
     * @param slowBuild {@code true} to use the server tick scheduler,
     *                  {@code false} to place the full plan immediately
     */
    public static void setSlowBuild(ItemStack builder, boolean slowBuild) {
        if (!isGadget(builder)) return;
        builder.getOrCreateTag().putBoolean(KEY_SLOW_BUILD, slowBuild);
    }

    /**
     * Reads the selected multiblock pattern index from a builder stack.
     *
     * <p>Preconditions: none beyond stack ownership. Side effects: none. Stored
     * negative values are normalized because pattern selection is zero-based.</p>
     *
     * @param builder stack expected to carry builder configuration
     * @return selected pattern index in the range {@code [0, Integer.MAX_VALUE]},
     * or {@code 0} when the stack is not an MBD gadget or has no saved index
     */
    public static int getPatternIndex(ItemStack builder) {
        if (!isGadget(builder)) return 0;
        var tag = builder.getTag();
        if (tag == null || !tag.contains(KEY_PATTERN_INDEX)) return 0;
        return Math.max(0, tag.getInt(KEY_PATTERN_INDEX));
    }

    /**
     * Saves the selected multiblock pattern index on a builder stack.
     *
     * <p>Preconditions: the stack must be the MBD gadget being configured.
     * Non-gadget stacks are ignored. Side effects: creates or updates the
     * builder NBT tag on the calling game thread.</p>
     *
     * @param builder      builder stack whose selected pattern is updated
     * @param patternIndex zero-based pattern index; negative values are clamped
     *                     to {@code 0}
     */
    public static void setPatternIndex(ItemStack builder, int patternIndex) {
        if (!isGadget(builder)) return;
        builder.getOrCreateTag().putInt(KEY_PATTERN_INDEX, Math.max(0, patternIndex));
    }

    private static boolean isGadget(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof MBDGadgetsItem;
    }

    /**
     * Records the item-source block position that auto-build should query for
     * item handler capabilities.
     *
     * <p>Business goal: let the builder consume block items from an adjacent
     * inventory selected by shift-right-click. Preconditions: {@code builder},
     * {@code level}, and {@code pos} must be non-null; callers should validate
     * that the stack is a builder and the target exposes item handlers before
     * calling. Side effects: writes dimension id and packed block position to
     * stack NBT. Thread safety follows the stack and level owner.</p>
     *
     * @param builder stack that stores the binding
     * @param level   level containing the selected material source
     * @param pos     block position in that level
     */
    public static void bindItemPos(ItemStack builder, Level level, BlockPos pos) {
        var tag = builder.getOrCreateTag();
        tag.putString(KEY_ITEM_DIM, Objects.requireNonNull(level.dimension().location()).toString());
        tag.putLong(KEY_ITEM_POS, Objects.requireNonNull(pos).asLong());
        tag.remove(KEY_ITEM_SOURCE_TYPE);
    }

    /**
     * Records an AE2 ME network item-source block position.
     *
     * <p>The stored coordinates are still item-source coordinates, but the
     * source type lets UI text identify the target as an ME network instead of
     * a plain inventory.</p>
     *
     * @param builder stack that stores the binding
     * @param level   level containing the selected material source
     * @param pos     block position in that level
     */
    public static void bindMEItemPos(ItemStack builder, Level level, BlockPos pos) {
        bindItemPos(builder, level, pos);
        builder.getOrCreateTag().putString(KEY_ITEM_SOURCE_TYPE, ITEM_SOURCE_TYPE_ME);
    }

    /**
     * Checks whether the saved item binding came from an AE2 ME network.
     *
     * @param builder stack carrying builder NBT
     * @return {@code true} when the item binding was recorded as an ME source
     */
    public static boolean isBoundItemSourceME(ItemStack builder) {
        if (!isGadget(builder)) return false;
        var tag = builder.getTag();
        return tag != null && ITEM_SOURCE_TYPE_ME.equals(tag.getString(KEY_ITEM_SOURCE_TYPE));
    }

    /**
     * Records the fluid-source block position that auto-build should query for
     * fluid handler capabilities.
     *
     * <p>Preconditions and threading match {@link #bindItemPos(ItemStack, Level,
     * BlockPos)}. Side effects: writes dimension id and packed block position to
     * stack NBT.</p>
     *
     * @param builder stack that stores the binding
     * @param level   level containing the selected fluid source
     * @param pos     block position in that level
     */
    public static void bindFluidPos(ItemStack builder, Level level, BlockPos pos) {
        var tag = builder.getOrCreateTag();
        tag.putString(KEY_FLUID_DIM, Objects.requireNonNull(level.dimension().location()).toString());
        tag.putLong(KEY_FLUID_POS, Objects.requireNonNull(pos).asLong());
    }

    /**
     * Resolves all item handlers exposed by the builder's bound block in the
     * player's current dimension.
     *
     * <p>Business goal: provide auto-build with the current external inventory
     * candidates for block placement. Preconditions: the player must be
     * non-null, in a level, and accessed from the game thread. Side effects:
     * resolves Forge capabilities but does not mutate the world or handlers.</p>
     *
     * @param player player holding the configured builder
     * @return a new list of currently resolvable item handlers; empty when no
     * builder is held, the binding is missing, the dimension differs, the block
     * entity is absent, or no item capability is available
     */
    public static List<IItemHandler> getBoundItemHandlers(Player player) {
        ItemStack builder = findBuilderStack(player);
        if (builder.isEmpty()) return Collections.emptyList();
        BoundPos bound = readBoundPos(builder, KEY_ITEM_DIM, KEY_ITEM_POS);
        if (bound == null) return Collections.emptyList();
        if (!isSameDim(player.level(), bound.dimension)) return Collections.emptyList();
        BlockEntity be = player.level().getBlockEntity(bound.pos);
        if (be == null) return Collections.emptyList();
        return collectItemHandlers(be);
    }

    /**
     * Resolves all fluid handlers exposed by the builder's bound block in the
     * player's current dimension.
     *
     * <p>Preconditions, side effects, and thread expectations match
     * {@link #getBoundItemHandlers(Player)}, but the lookup targets fluid
     * capabilities for bucket/fluid placement.</p>
     *
     * @param player player holding the configured builder
     * @return a new list of currently resolvable fluid handlers, or an empty
     * list when the binding cannot be used
     */
    public static List<IFluidHandler> getBoundFluidHandlers(Player player) {
        ItemStack builder = findBuilderStack(player);
        if (builder.isEmpty()) return Collections.emptyList();
        BoundPos bound = readBoundPos(builder, KEY_FLUID_DIM, KEY_FLUID_POS);
        if (bound == null) return Collections.emptyList();
        if (!isSameDim(player.level(), bound.dimension)) return Collections.emptyList();
        BlockEntity be = player.level().getBlockEntity(bound.pos);
        if (be == null) return Collections.emptyList();
        return collectFluidHandlers(be);
    }

    /**
     * Returns the first item handler from the current builder item binding.
     *
     * <p>This is a convenience view for callers that cannot use multiple
     * handlers. Preconditions and side effects match
     * {@link #getBoundItemHandlers(Player)}.</p>
     *
     * @param player player holding the configured builder
     * @return first resolved item handler, or {@code null} when none is
     * available
     */
    public static IItemHandler getBoundItemHandler(Player player) {
        List<IItemHandler> handlers = getBoundItemHandlers(player);
        return handlers.isEmpty() ? null : handlers.get(0);
    }

    /**
     * Returns the first fluid handler from the current builder fluid binding.
     *
     * <p>Preconditions and side effects match
     * {@link #getBoundFluidHandlers(Player)}.</p>
     *
     * @param player player holding the configured builder
     * @return first resolved fluid handler, or {@code null} when none is
     * available
     */
    public static IFluidHandler getBoundFluidHandler(Player player) {
        List<IFluidHandler> handlers = getBoundFluidHandlers(player);
        return handlers.isEmpty() ? null : handlers.get(0);
    }

    /**
     * Reads the saved item-source coordinate from a builder stack.
     *
     * <p>Preconditions: the stack must be non-null. Side effects: none; malformed
     * resource locations or missing tags are treated as absent.</p>
     *
     * @param builder stack carrying builder NBT
     * @return saved dimension and block position, or {@code null} when no valid
     * item binding is present
     */
    public static BoundPos readBoundItemPos(ItemStack builder) {
        return readBoundPos(builder, KEY_ITEM_DIM, KEY_ITEM_POS);
    }

    /**
     * Reads the saved fluid-source coordinate from a builder stack.
     *
     * <p>Preconditions and side effects match {@link #readBoundItemPos(ItemStack)}.</p>
     *
     * @param builder stack carrying builder NBT
     * @return saved dimension and block position, or {@code null} when no valid
     * fluid binding is present
     */
    public static BoundPos readBoundFluidPos(ItemStack builder) {
        return readBoundPos(builder, KEY_FLUID_DIM, KEY_FLUID_POS);
    }

    private static BoundPos readBoundPos(ItemStack stack, String dimKey, String posKey) {
        var tag = stack.getTag();
        if (tag == null) return null;
        if (!tag.contains(dimKey) || !tag.contains(posKey)) return null;
        String dimRaw = tag.getString(dimKey);
        ResourceLocation dim = ResourceLocation.tryParse(dimRaw);
        if (dim == null) return null;
        BlockPos pos = BlockPos.of(tag.getLong(posKey));
        return new BoundPos(dim, pos);
    }

    /**
     * Checks whether a block entity exposes any item handler capability.
     *
     * <p>All six sided capabilities and the null-side capability are queried.
     * Preconditions: none; {@code null} is accepted. Side effects: resolves
     * capability presence only. Thread safety follows Forge capability access
     * rules for the block entity's level thread.</p>
     *
     * @param be block entity to probe
     * @return {@code true} when at least one item handler is present
     */
    public static boolean hasItemHandler(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : Direction.values()) {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).isPresent()) return true;
        }
        if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()) return true;
        return hasMEItemStorage(be);
    }

    /**
     * Checks whether a block entity exposes AE2 ME storage as an item source.
     *
     * @param be block entity to probe
     * @return {@code true} when AE2 is loaded and the target exposes ME storage
     */
    public static boolean hasMEItemStorage(BlockEntity be) {
        if (be == null || !MBD2.isAE2Loaded()) return false;
        try {
            Class<?> bridgeClass = Class.forName(AE2_ITEM_HANDLER_BRIDGE);
            var method = bridgeClass.getMethod("hasItemStorage", BlockEntity.class);
            return Boolean.TRUE.equals(method.invoke(null, be));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                 LinkageError ignored) {
            return false;
        }
    }

    /**
     * Checks whether a block entity exposes any fluid handler capability.
     *
     * <p>Capability sides, side effects, and thread expectations match
     * {@link #hasItemHandler(BlockEntity)}.</p>
     *
     * @param be block entity to probe
     * @return {@code true} when at least one fluid handler is present
     */
    public static boolean hasFluidHandler(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : Direction.values()) {
            if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).isPresent()) return true;
        }
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent();
    }

    public static List<IItemHandler> collectItemHandlers(BlockEntity be) {
        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).resolve().ifPresent(handlers::add);
        }
        be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().ifPresent(handlers::add);
        collectAE2ItemHandlers(be, handlers);
        return handlers;
    }

    public static List<IItemHandler> collectMEItemHandlers(BlockEntity be) {
        List<IItemHandler> handlers = new ArrayList<>();
        collectAE2ItemHandlers(be, handlers);
        return handlers;
    }

    @SuppressWarnings("unchecked")
    private static void collectAE2ItemHandlers(BlockEntity be, List<IItemHandler> handlers) {
        if (!MBD2.isAE2Loaded()) return;
        try {
            Class<?> bridgeClass = Class.forName(AE2_ITEM_HANDLER_BRIDGE);
            var method = bridgeClass.getMethod("collectItemHandlers", BlockEntity.class, List.class);
            method.invoke(null, be, handlers);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                 LinkageError ignored) {
        }
    }

    private static List<IFluidHandler> collectFluidHandlers(BlockEntity be) {
        List<IFluidHandler> handlers = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).resolve().ifPresent(handlers::add);
        }
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().ifPresent(handlers::add);
        return handlers;
    }

    private static boolean isSameDim(Level level, ResourceLocation dim) {
        ResourceLocation current = level.dimension().location();
        return current != null && current.equals(dim);
    }

    /**
     * Immutable coordinate stored by a builder binding.
     *
     * @param dimension registry location of the level dimension; must be non-null
     * @param pos       block position inside that dimension; must be non-null
     */
    public record BoundPos(ResourceLocation dimension, BlockPos pos) {
        public BoundPos {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(pos);
        }
    }
}
