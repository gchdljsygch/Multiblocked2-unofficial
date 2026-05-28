package com.lowdragmc.mbd2.utils;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BuilderMaterialBindings {
    private static final String KEY_ITEM_DIM = "mbd2_bind_item_dim";
    private static final String KEY_ITEM_POS = "mbd2_bind_item_pos";
    private static final String KEY_FLUID_DIM = "mbd2_bind_fluid_dim";
    private static final String KEY_FLUID_POS = "mbd2_bind_fluid_pos";
    public static final String KEY_SLOW_BUILD = "mbd2_slow_build";

    private BuilderMaterialBindings() {
    }

    public static ItemStack findBuilderStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isBuilder(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isBuilder(off)) return off;
        return ItemStack.EMPTY;
    }

    public static boolean isBuilder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof MBDGadgetsItem gadgets)) return false;
        return gadgets.isMultiblockBuilder(stack);
    }

    public static boolean isSlowBuild(ItemStack builder) {
        if (!isBuilder(builder)) return false;
        var tag = builder.getTag();
        return tag != null && tag.getBoolean(KEY_SLOW_BUILD);
    }

    public static void setSlowBuild(ItemStack builder, boolean slowBuild) {
        if (!isBuilder(builder)) return;
        builder.getOrCreateTag().putBoolean(KEY_SLOW_BUILD, slowBuild);
    }

    public static void bindItemPos(ItemStack builder, Level level, BlockPos pos) {
        var tag = builder.getOrCreateTag();
        tag.putString(KEY_ITEM_DIM, Objects.requireNonNull(level.dimension().location()).toString());
        tag.putLong(KEY_ITEM_POS, Objects.requireNonNull(pos).asLong());
    }

    public static void bindFluidPos(ItemStack builder, Level level, BlockPos pos) {
        var tag = builder.getOrCreateTag();
        tag.putString(KEY_FLUID_DIM, Objects.requireNonNull(level.dimension().location()).toString());
        tag.putLong(KEY_FLUID_POS, Objects.requireNonNull(pos).asLong());
    }

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

    public static IItemHandler getBoundItemHandler(Player player) {
        List<IItemHandler> handlers = getBoundItemHandlers(player);
        return handlers.isEmpty() ? null : handlers.get(0);
    }

    public static IFluidHandler getBoundFluidHandler(Player player) {
        List<IFluidHandler> handlers = getBoundFluidHandlers(player);
        return handlers.isEmpty() ? null : handlers.get(0);
    }

    public static BoundPos readBoundItemPos(ItemStack builder) {
        return readBoundPos(builder, KEY_ITEM_DIM, KEY_ITEM_POS);
    }

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

    public static boolean hasItemHandler(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : Direction.values()) {
            if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).isPresent()) return true;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    public static boolean hasFluidHandler(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : Direction.values()) {
            if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).isPresent()) return true;
        }
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent();
    }

    private static List<IItemHandler> collectItemHandlers(BlockEntity be) {
        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).resolve().ifPresent(handlers::add);
        }
        be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().ifPresent(handlers::add);
        return handlers;
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

    public record BoundPos(ResourceLocation dimension, BlockPos pos) {
        public BoundPos {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(pos);
        }
    }
}
