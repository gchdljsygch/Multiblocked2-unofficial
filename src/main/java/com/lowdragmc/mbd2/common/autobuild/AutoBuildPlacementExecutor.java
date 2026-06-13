package com.lowdragmc.mbd2.common.autobuild;

import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.pattern.util.PatternStateRotation;
import com.lowdragmc.mbd2.utils.PatternAutoBuildPlacement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AutoBuildPlacementExecutor {
    private AutoBuildPlacementExecutor() {
    }

    public static void executePlacement(Player player,
                                        Level world,
                                        PatternAutoBuildPlacement placement,
                                        Map<BlockPos, Object> blocks,
                                        @Nullable IItemHandler boundItemHandler,
                                        @Nullable IFluidHandler boundFluidHandler) {
        BlockPos pos = Objects.requireNonNull(placement.pos);
        if (!world.isEmptyBlock(pos) && hasAnyFluid(world.getBlockState(pos))) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        ItemStack found = placement.found.copy();
        if (found.getItem() instanceof BlockItem itemBlock) {
            ItemStack placedStack = found;
            ItemStack extractedFromBound = ItemStack.EMPTY;
            if (placement.source == PatternAutoBuildPlacement.Source.BOUND_ITEM_HANDLER && boundItemHandler != null) {
                extractedFromBound = tryExtractOneMatchingFromHandler(boundItemHandler, placedStack);
                if (extractedFromBound.isEmpty()) {
                    return;
                }
                placedStack = extractedFromBound.copyWithCount(1);
            }

            BlockHitResult hit = Objects.requireNonNull(BlockHitResult.miss(
                    Objects.requireNonNull(player.getEyePosition(0)),
                    Direction.UP,
                    pos
            ));
            BlockPlaceContext context = new BlockPlaceContext(
                    world,
                    player,
                    InteractionHand.MAIN_HAND,
                    placedStack,
                    hit
            );
            InteractionResult interactionResult = itemBlock.place(context);
            boolean placed = interactionResult != InteractionResult.FAIL;
            if (placed) {
                applyExpectedInfo(world, pos, placement);
                if (placement.foundSlot >= 0 && !player.isCreative() && placement.source == PatternAutoBuildPlacement.Source.PLAYER_INVENTORY) {
                    int slot = normalizeSlot(player, placement.foundSlot, placement.found);
                    if (slot >= 0) {
                        player.getInventory().getItem(slot).shrink(1);
                    }
                }
            }
            if (!placed && placement.source == PatternAutoBuildPlacement.Source.BOUND_ITEM_HANDLER && boundItemHandler != null && !extractedFromBound.isEmpty()) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(boundItemHandler, extractedFromBound, false);
                if (!remainder.isEmpty()) {
                    player.drop(remainder, false);
                }
            }
        } else if (found.getItem() instanceof BucketItem itemBucket) {
            if (placement.source == PatternAutoBuildPlacement.Source.BOUND_FLUID_HANDLER && boundFluidHandler != null) {
                FluidStack request = new FluidStack(itemBucket.getFluid(), 1000);
                FluidStack drained = boundFluidHandler.drain(request, IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() < 1000) {
                    if (!drained.isEmpty()) {
                        boundFluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    }
                } else {
                    FluidState fluidState = itemBucket.getFluid() instanceof FlowingFluid f ? f.getSource(false) : itemBucket.getFluid().defaultFluidState();
                    boolean placed = world.setBlock(pos, fluidState.createLegacyBlock(), 3);
                    if (!placed) {
                        boundFluidHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    } else {
                        applyExpectedInfo(world, pos, placement);
                    }
                }
            } else {
                if (!world.isEmptyBlock(pos) && hasAnyFluid(world.getBlockState(pos))) {
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (itemBucket.emptyContents(player, world, pos, null, null)) {
                    itemBucket.checkExtraContent(player, world, found, pos);
                    applyExpectedInfo(world, pos, placement);
                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, found);
                    }
                    player.awardStat(Objects.requireNonNull(Stats.ITEM_USED.get(itemBucket)));
                    if (placement.foundSlot >= 0 && !player.isCreative() && placement.source == PatternAutoBuildPlacement.Source.PLAYER_INVENTORY) {
                        ItemStack emptyBucket = Objects.requireNonNull(BucketItem.getEmptySuccessItem(found, player));
                        int slot = normalizeSlot(player, placement.foundSlot, placement.found);
                        if (slot >= 0) {
                            player.getInventory().setItem(slot, emptyBucket);
                        }
                    }
                }
            }
        }

        IMachine.ofMachine(world, pos).ifPresentOrElse(
                machine -> blocks.put(pos, machine),
                () -> blocks.put(pos, world.getBlockState(pos))
        );
    }

    public static List<IItemHandler> collectItemHandlers(BlockEntity be) {
        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).resolve().ifPresent(handlers::add);
        }
        be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().ifPresent(handlers::add);
        return handlers;
    }

    public static List<IFluidHandler> collectFluidHandlers(BlockEntity be) {
        List<IFluidHandler> handlers = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).resolve().ifPresent(handlers::add);
        }
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().ifPresent(handlers::add);
        return handlers;
    }

    private static boolean hasAnyFluid(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    private static void applyExpectedInfo(Level world, BlockPos pos, PatternAutoBuildPlacement placement) {
        if (placement.expectedInfo == null) return;
        BlockState expectedState = placement.expectedInfo.getBlockState();
        if (expectedState == null) return;
        Rotation rotation = placement.rotateExpectedState && placement.rotation != null ? placement.rotation : Rotation.NONE;
        BlockState rotatedState = rotation == Rotation.NONE ? expectedState : PatternStateRotation.rotate(expectedState, rotation);
        BlockState currentState = world.getBlockState(pos);
        if (currentState.getBlock() != rotatedState.getBlock()) return;
        try {
            var rotatedInfo = new BlockInfo();
            rotatedInfo.deserializeNBT(placement.expectedInfo.serializeNBT());
            rotatedInfo.setHasBlockEntity(placement.expectedInfo.hasBlockEntity());
            rotatedInfo.setBlockState(rotatedState);
            rotatedInfo.apply(world, pos);
        } catch (Throwable ignored) {
        }
    }

    private static int normalizeSlot(Player player, int slot, ItemStack template) {
        if (slot >= 0 && slot < player.getInventory().items.size()) {
            if (ItemStack.isSameItemSameTags(player.getInventory().getItem(slot), template)) return slot;
        }
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            if (ItemStack.isSameItemSameTags(stack, template)) return i;
        }
        return -1;
    }

    private static ItemStack tryExtractOneMatchingFromHandler(IItemHandler handler, ItemStack template) {
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty()) continue;
            if (inSlot.getItem() instanceof BucketItem) continue;
            if (!ItemStack.isSameItemSameTags(inSlot, template)) continue;
            ItemStack extracted = handler.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                return extracted.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }
}
