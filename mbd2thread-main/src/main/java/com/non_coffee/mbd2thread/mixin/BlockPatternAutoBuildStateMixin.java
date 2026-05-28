package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.BlockPattern;
import com.lowdragmc.mbd2.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import com.non_coffee.mbd2thread.duck.BlockPatternBaseFacingAccess;
import com.non_coffee.mbd2thread.autobuild.AutoBuildPlacementExecutor;
import com.non_coffee.mbd2thread.autobuild.SlowAutoBuildScheduler;
import com.non_coffee.mbd2thread.util.BuilderMaterialBindings;
import com.non_coffee.mbd2thread.util.MultiFluidHandler;
import com.non_coffee.mbd2thread.util.MultiItemHandler;
import com.non_coffee.mbd2thread.util.PatternAutoBuildPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(value = BlockPattern.class, remap = false)
public abstract class BlockPatternAutoBuildStateMixin {
    @Shadow @Final public int[][] aisleRepetitions;
    @Shadow @Final protected TraceabilityPredicate[][][] blockMatches;
    @Shadow @Final protected int fingerLength;
    @Shadow @Final protected int thumbLength;
    @Shadow @Final protected int palmLength;
    @Shadow @Final protected int[] centerOffset;

    @Shadow
    protected abstract BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing);

    @Inject(method = "autoBuild", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2thread$autoBuildPlaceExpectedStates(Player player, MultiblockState worldState, CallbackInfo ci) {
        Level world = player.level();
        int minZ = -centerOffset[4];
        ((MultiblockStateAccessors) (Object) worldState).mbd2thread$clean();
        IMultiController controller = worldState.getController();
        if (controller == null) {
            ci.cancel();
            return;
        }
        BlockPos centerPos = controller.getPos();
        Direction facing = controller.getFrontFacing().orElse(Direction.NORTH);
        Rotation rotation = computeRotation((BlockPattern) (Object) this, controller, facing);
        Map<SimplePredicate, Integer> cacheGlobal = worldState.getGlobalCount();
        Map<SimplePredicate, Integer> cacheLayer = worldState.getLayerCount();
        Map<BlockPos, Object> blocks = new HashMap<>();
        blocks.put(centerPos, controller);

        List<PatternAutoBuildPlacement> nonFluidPlacements = new ArrayList<>();
        List<PatternAutoBuildPlacement> fluidPlacements = new ArrayList<>();
        int[] reservedPerSlot = player.isCreative() ? null : new int[player.getInventory().items.size()];
        IItemHandler boundItemHandler = null;
        IFluidHandler boundFluidHandler = null;
        ItemStack builderStack = ItemStack.EMPTY;
        boolean slowBuild = false;
        if (!player.isCreative()) {
            builderStack = BuilderMaterialBindings.findBuilderStack(player);
            if (!builderStack.isEmpty()) {
                slowBuild = BuilderMaterialBindings.isSlowBuild(builderStack);
                var dim = world.dimension().location();

                var itemBound = BuilderMaterialBindings.readBoundItemPos(builderStack);
                if (itemBound != null && dim != null && dim.equals(itemBound.dimension())) {
                    BlockEntity be = world.getBlockEntity(itemBound.pos());
                    if (be != null) {
                        var handlers = collectItemHandlers(be);
                        if (handlers.size() == 1) {
                            boundItemHandler = handlers.get(0);
                        } else if (handlers.size() > 1) {
                            MultiItemHandler multi = new MultiItemHandler(handlers);
                            if (!multi.isEmpty()) {
                                boundItemHandler = multi;
                            }
                        }
                    }
                }

                var fluidBound = BuilderMaterialBindings.readBoundFluidPos(builderStack);
                if (fluidBound != null && dim != null && dim.equals(fluidBound.dimension())) {
                    BlockEntity be = world.getBlockEntity(fluidBound.pos());
                    if (be != null) {
                        var handlers = collectFluidHandlers(be);
                        if (handlers.size() == 1) {
                            boundFluidHandler = handlers.get(0);
                        } else if (handlers.size() > 1) {
                            MultiFluidHandler multi = new MultiFluidHandler(handlers);
                            if (!multi.isEmpty()) {
                                boundFluidHandler = multi;
                            }
                        }
                    }
                }
            }
        }

        for (int c = 0, z = minZ++; c < this.fingerLength; c++) {
            for (int r = 0; r < aisleRepetitions[c][0]; r++) {
                cacheLayer.clear();
                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        BlockPos pos = setActualRelativeOffset(x, y, z, facing).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        ((MultiblockStateAccessors) (Object) worldState).mbd2thread$update(pos, predicate);

                        BlockState existing = world.getBlockState(pos);
                        boolean emptyBlock = world.isEmptyBlock(pos);
                        boolean existingHasFluid = hasAnyFluid(existing);
                        if (!emptyBlock && !existingHasFluid) {
                            blocks.put(pos, existing);
                            for (SimplePredicate limit : predicate.limited) {
                                limit.testLimited(worldState);
                            }
                            continue;
                        }

                        boolean find = false;
                        BlockInfo[] infos = new BlockInfo[0];
                        for (SimplePredicate limit : predicate.limited) {
                            if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != facing) continue;
                            if (limit.minLayerCount > 0) {
                                if (!cacheLayer.containsKey(limit)) {
                                    cacheLayer.put(limit, 1);
                                } else if (cacheLayer.get(limit) < limit.minLayerCount && (limit.maxLayerCount == -1 || cacheLayer.get(limit) < limit.maxLayerCount)) {
                                    cacheLayer.put(limit, cacheLayer.get(limit) + 1);
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                            infos = limit.candidates == null ? null : limit.candidates.get();
                            find = true;
                            break;
                        }
                        if (!find) {
                            for (SimplePredicate limit : predicate.limited) {
                                if (limit.controllerFront.isEnable() && limit.controllerFront.getValue() != facing) continue;
                                if (limit.maxLayerCount != -1 && cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount) continue;
                                if (limit.maxCount != -1 && cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount) continue;

                                cacheLayer.put(limit, cacheLayer.getOrDefault(limit, 0) + 1);
                                cacheGlobal.put(limit, cacheGlobal.getOrDefault(limit, 0) + 1);

                                infos = ArrayUtils.addAll(infos, limit.candidates == null ? null : limit.candidates.get());
                            }
                            for (SimplePredicate common : predicate.common) {
                                if (common.controllerFront.isEnable() && common.controllerFront.getValue() != facing) continue;
                                infos = ArrayUtils.addAll(infos, common.candidates == null ? null : common.candidates.get());
                            }
                        }

                        List<BlockInfo> candidateInfos = new ArrayList<>();
                        List<ItemStack> candidates = new ArrayList<>();
                        if (infos != null) {
                            for (BlockInfo info : infos) {
                                if (info == null) continue;
                                BlockState s = info.getBlockState();
                                if (s == null || s.getBlock() == Blocks.AIR) continue;
                                ItemStack asItem = info.getItemStackForm();
                                if (asItem.isEmpty()) continue;
                                if (!(asItem.getItem() instanceof BlockItem || asItem.getItem() instanceof BucketItem)) continue;
                                candidateInfos.add(info);
                                candidates.add(asItem);
                            }
                        }

                        BlockInfo expectedFluidInfo = pickExpectedFluidInfo(candidateInfos);
                        if (!emptyBlock && existingHasFluid && existing.getBlock() instanceof LiquidBlock && expectedFluidInfo != null) {
                            BlockState expectedFluidState = expectedFluidInfo.getBlockState();
                            if (expectedFluidState != null
                                    && expectedFluidState.getBlock() instanceof LiquidBlock
                                    && existing.getFluidState().isSource()
                                    && existing.getFluidState().getType() == expectedFluidState.getFluidState().getType()) {
                                blocks.put(pos, existing);
                                for (SimplePredicate limit : predicate.limited) {
                                    limit.testLimited(worldState);
                                }
                                continue;
                            }
                        }

                        ItemStack found = null;
                        int foundSlot = -1;
                        PatternAutoBuildPlacement.Source source = PatternAutoBuildPlacement.Source.CREATIVE;
                        int sourceSlot = -1;
                        if (!player.isCreative()) {
                            for (int i = 0; i < player.getInventory().items.size(); i++) {
                                ItemStack itemStack = player.getInventory().items.get(i);
                                if (itemStack.isEmpty()) continue;
                                if (reservedPerSlot != null && reservedPerSlot[i] >= itemStack.getCount()) continue;
                                if (matchesAny(candidates, itemStack)) {
                                    found = itemStack.copy();
                                    foundSlot = i;
                                    source = PatternAutoBuildPlacement.Source.PLAYER_INVENTORY;
                                    if (reservedPerSlot != null) {
                                        reservedPerSlot[i]++;
                                    }
                                    break;
                                }
                            }

                            if (found == null && boundFluidHandler != null) {
                                FluidStack request = toRequiredFluidStack(expectedFluidInfo);
                                if (request != null && boundFluidHandler.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount() >= request.getAmount()) {
                                    ItemStack bucketCandidate = findBucketCandidate(candidates, request.getFluid());
                                    if (bucketCandidate != null) {
                                        found = bucketCandidate.copyWithCount(1);
                                        source = PatternAutoBuildPlacement.Source.BOUND_FLUID_HANDLER;
                                    }
                                }
                            }

                            if (found == null && boundItemHandler != null) {
                                ItemStack match = findMatchingStackInHandler(boundItemHandler, candidates);
                                if (!match.isEmpty()) {
                                    found = match.copyWithCount(1);
                                    source = PatternAutoBuildPlacement.Source.BOUND_ITEM_HANDLER;
                                }
                            }
                        } else {
                            for (ItemStack candidate : candidates) {
                                found = candidate.copy();
                                if (!found.isEmpty() && (found.getItem() instanceof BlockItem || found.getItem() instanceof BucketItem)) {
                                    source = PatternAutoBuildPlacement.Source.CREATIVE;
                                    break;
                                }
                                found = null;
                            }
                        }
                        if (found == null) {
                            continue;
                        }

                        BlockInfo expectedInfo = null;
                        for (BlockInfo info : candidateInfos) {
                            if (ItemStack.isSameItemSameTags(info.getItemStackForm(), found)) {
                                expectedInfo = info;
                                break;
                            }
                        }

                        boolean expectsFluid = (found.getItem() instanceof BucketItem)
                                || (expectedInfo != null && expectedInfo.getBlockState() != null && hasAnyFluid(expectedInfo.getBlockState()));
                        if (expectsFluid) {
                            if (!emptyBlock && !existingHasFluid) {
                                continue;
                            }
                            fluidPlacements.add(new PatternAutoBuildPlacement(pos, found, foundSlot, expectedInfo, rotation, source, sourceSlot));
                        } else {
                            nonFluidPlacements.add(new PatternAutoBuildPlacement(pos, found, foundSlot, expectedInfo, rotation, source, sourceSlot));
                        }
                    }
                }
                z++;
            }
        }

        if (slowBuild && player instanceof ServerPlayer serverPlayer) {
            List<PatternAutoBuildPlacement> all = new ArrayList<>(nonFluidPlacements.size() + fluidPlacements.size());
            all.addAll(nonFluidPlacements);
            all.addAll(fluidPlacements);
            SlowAutoBuildScheduler.replace(serverPlayer, world.dimension(), all, boundItemHandler, boundFluidHandler);
            ci.cancel();
            return;
        }

        for (PatternAutoBuildPlacement p : nonFluidPlacements) {
            AutoBuildPlacementExecutor.executePlacement(player, world, p, blocks, boundItemHandler, boundFluidHandler);
        }
        for (PatternAutoBuildPlacement p : fluidPlacements) {
            AutoBuildPlacementExecutor.executePlacement(player, world, p, blocks, boundItemHandler, boundFluidHandler);
        }

        blocks.forEach((pos, block) -> {
            if (block instanceof IMultiController) return;
            if (block instanceof BlockState state) {
                world.setBlock(pos, state, 3);
            } else if (block instanceof IMachine machine) {
                world.setBlock(pos, machine.getBlockState(), 3);
            }
        });

        ci.cancel();
    }

    private static boolean hasAnyFluid(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    private static boolean matchesAny(List<ItemStack> candidates, ItemStack stack) {
        ItemStack s = Objects.requireNonNull(stack);
        for (ItemStack candidate : candidates) {
            if (ItemStack.isSameItemSameTags(Objects.requireNonNull(candidate), s)) return true;
        }
        return false;
    }


    private static BlockInfo pickExpectedFluidInfo(List<BlockInfo> infos) {
        for (BlockInfo info : infos) {
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null) continue;
            if (state.getBlock() instanceof LiquidBlock) return info;
        }
        return null;
    }

    private static FluidStack toRequiredFluidStack(BlockInfo expected) {
        if (expected == null) return null;
        BlockState state = expected.getBlockState();
        if (state == null) return null;
        if (!hasAnyFluid(state)) return null;
        var fluid = state.getFluidState().getType();
        if (fluid == null) return null;
        return new FluidStack(fluid, 1000);
    }

    private static ItemStack findBucketCandidate(List<ItemStack> candidates, net.minecraft.world.level.material.Fluid fluid) {
        for (ItemStack c : candidates) {
            if (c == null || c.isEmpty()) continue;
            if (c.getItem() instanceof BucketItem b && b.getFluid() == fluid) {
                return c;
            }
        }
        return null;
    }

    private static ItemStack findMatchingStackInHandler(IItemHandler handler, List<ItemStack> candidates) {
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty()) continue;
            if (inSlot.getItem() instanceof BucketItem) continue;
            if (matchesAny(candidates, inSlot)) {
                return inSlot.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<IItemHandler> collectItemHandlers(BlockEntity be) {
        return AutoBuildPlacementExecutor.collectItemHandlers(be);
    }

    private static List<IFluidHandler> collectFluidHandlers(BlockEntity be) {
        return AutoBuildPlacementExecutor.collectFluidHandlers(be);
    }

    private static Rotation computeRotation(BlockPattern pattern, IMultiController controller, Direction currentFacing) {
        Direction base = null;
        if (pattern instanceof BlockPatternBaseFacingAccess access) {
            base = access.mbd2thread$getBaseFacing();
        }
        if (base == null) return Rotation.NONE;
        return horizontalRotation(base, currentFacing);
    }

    private static Rotation computeRotationFromShapeInfo(IMultiController controller, Direction currentFacing) {
        if (!(controller instanceof MBDMultiblockMachine multiblock)) return Rotation.NONE;
        MultiblockMachineDefinition definition = multiblock.getDefinition();
        if (definition == null) return Rotation.NONE;
        MultiblockShapeInfo[] infos;
        try {
            infos = definition.shapeInfoFactory().apply(definition);
        } catch (Throwable ignored) {
            return Rotation.NONE;
        }
        if (infos == null || infos.length == 0) return Rotation.NONE;
        Direction baseFacing = findControllerFacing(infos[0]);
        if (baseFacing == null) return Rotation.NONE;
        return horizontalRotation(baseFacing, currentFacing);
    }

    private static Direction findControllerFacing(MultiblockShapeInfo info) {
        if (info == null || info.getBlocks() == null) return null;
        for (var xSlice : info.getBlocks()) {
            if (xSlice == null) continue;
            for (var ySlice : xSlice) {
                if (ySlice == null) continue;
                for (var blockInfo : ySlice) {
                    if (blockInfo instanceof ControllerBlockInfo controllerBlockInfo) {
                        Direction facing = controllerBlockInfo.getFacing();
                        if (facing == null || facing.getAxis() == Direction.Axis.Y) return Direction.NORTH;
                        return facing;
                    }
                }
            }
        }
        return null;
    }

    private static Rotation horizontalRotation(Direction from, Direction to) {
        if (from == null || to == null) return Rotation.NONE;
        if (from.getAxis() == Direction.Axis.Y || to.getAxis() == Direction.Axis.Y) return Rotation.NONE;
        int fromIndex = horizontalIndex(from);
        int toIndex = horizontalIndex(to);
        int steps = (toIndex - fromIndex + 4) & 3;
        return switch (steps) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int horizontalIndex(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }
}
