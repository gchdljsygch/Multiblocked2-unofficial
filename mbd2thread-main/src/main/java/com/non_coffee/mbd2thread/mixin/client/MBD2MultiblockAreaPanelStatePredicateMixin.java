package com.non_coffee.mbd2thread.mixin.client;

import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateBlocks;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateFluids;
import com.lowdragmc.mbd2.api.pattern.predicates.PredicateStates;
import com.lowdragmc.mbd2.common.gui.editor.PredicateResource;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.BlockPlaceholder;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockAreaPanel;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockPatternPanel;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mixin(value = MultiblockAreaPanel.class, remap = false)
public class MBD2MultiblockAreaPanelStatePredicateMixin {
    @Inject(method = "generatePattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2thread$generatePatternWithStatePredicates(CallbackInfo ci) {
        MultiblockAreaPanel self = (MultiblockAreaPanel) (Object) this;
        MultiblockMachineProject project = self.getProject();
        MultiblockPatternPanel patternPanel = self.getPatternPanel();
        Object runtime = findRuntime(self);
        if (runtime == null) {
            return;
        }
        BlockPos from = findAreaPos(runtime, true);
        BlockPos to = findAreaPos(runtime, false);
        if (from == null || to == null) {
            return;
        }

        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        Vector3i controllerOffset = findFieldByType(runtime, Vector3i.class);
        if (controllerOffset == null) {
            controllerOffset = new Vector3i(0, 0, 0);
        }
        BlockPos controllerPos = new BlockPos(
                controllerOffset.x + minX,
                controllerOffset.y + minY,
                controllerOffset.z + minZ
        );
        Direction controllerFace = findFieldByType(runtime, Direction.class);
        if (controllerFace == null) {
            controllerFace = Direction.NORTH;
        }

        BlockPlaceholder[][][] blockPlaceholders = new BlockPlaceholder[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        boolean addNewResource = false;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            ci.cancel();
            return;
        }

        PredicateResource predicateResource = project.getPredicateResource();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockPlaceholder holder;
                    if (blockPos.equals(controllerPos)) {
                        holder = BlockPlaceholder.controller(predicateResource).setFacing(controllerFace);
                    } else {
                        BlockState state = level.getBlockState(blockPos);
                        Block block = state.getBlock();
                        String id;
                        if (block instanceof LiquidBlock liquidBlock) {
                            var fluid = liquidBlock.getFluid().getSource();
                            id = Optional.ofNullable(ForgeRegistries.FLUIDS.getKey(fluid))
                                    .map(ResourceLocation::toString)
                                    .orElse("any");
                            if (!predicateResource.hasBuiltinResource(id)) {
                                predicateResource.addBuiltinResource(id, new PredicateFluids(fluid));
                                addNewResource = true;
                            }
                        } else if (block == Blocks.AIR) {
                            id = "any";
                        } else {
                            boolean isDefault = state.equals(block.defaultBlockState());
                            id = isDefault
                                    ? Optional.ofNullable(ForgeRegistries.BLOCKS.getKey(block)).map(ResourceLocation::toString).orElse("any")
                                    : toStateKey(state);
                            if (!predicateResource.hasBuiltinResource(id)) {
                                predicateResource.addBuiltinResource(id, isDefault ? new PredicateBlocks(block) : new PredicateStates(state));
                                addNewResource = true;
                            }
                        }
                        holder = BlockPlaceholder.create(predicateResource, Either.left(id));
                    }
                    blockPlaceholders[x - minX][y - minY][z - minZ] = holder;
                }
            }
        }

        if (addNewResource) {
            Editor.INSTANCE.getResourcePanel().rebuildResource(project.getPredicateResource().name());
        }
        project.setBlockPlaceholders(blockPlaceholders);
        patternPanel.onBlockPlaceholdersChanged();
        ci.cancel();
    }

    private static Object findRuntime(MultiblockAreaPanel panel) {
        for (var f : panel.getClass().getDeclaredFields()) {
            if (f.getType().getName().endsWith("$Runtime") || f.getType().getSimpleName().equals("Runtime")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(panel);
                    if (v != null) return v;
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static BlockPos findAreaPos(Object runtime, boolean from) {
        Object area = findArea(runtime);
        if (area == null) return null;
        BlockPos byName = findFieldByName(area, from ? "from" : "to", BlockPos.class);
        if (byName != null) return byName;
        List<java.lang.reflect.Field> blockPosFields = new ArrayList<>();
        for (var f : area.getClass().getDeclaredFields()) {
            if (BlockPos.class.isAssignableFrom(f.getType())) {
                blockPosFields.add(f);
            }
        }
        if (blockPosFields.size() < 2) return null;
        try {
            var f = from ? blockPosFields.get(0) : blockPosFields.get(1);
            f.setAccessible(true);
            return (BlockPos) f.get(area);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object findArea(Object runtime) {
        for (var f : runtime.getClass().getDeclaredFields()) {
            if (f.getType().getName().endsWith("$Area") || f.getType().getSimpleName().equals("Area")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(runtime);
                    if (v != null) return v;
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        for (var f : runtime.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object v = f.get(runtime);
                if (v == null) continue;
                int blockPosFields = 0;
                for (var af : v.getClass().getDeclaredFields()) {
                    if (BlockPos.class.isAssignableFrom(af.getType())) blockPosFields++;
                }
                if (blockPosFields >= 2) return v;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static <T> T findFieldByType(Object obj, Class<T> type) {
        for (var f : obj.getClass().getDeclaredFields()) {
            if (!type.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                return type.cast(f.get(obj));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static <T> T findFieldByName(Object obj, String name, Class<T> type) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            if (!type.isAssignableFrom(f.getType())) return null;
            f.setAccessible(true);
            return type.cast(f.get(obj));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toStateKey(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return "any";
        var values = state.getValues();
        if (values.isEmpty()) return id.toString();
        var props = new ArrayList<Property<?>>(values.keySet());
        props.sort(Comparator.comparing(Property::getName));
        StringBuilder sb = new StringBuilder(64);
        sb.append(id).append('[');
        for (int i = 0; i < props.size(); i++) {
            Property<?> p = props.get(i);
            sb.append(p.getName()).append('=').append(getValueName(state, p));
            if (i + 1 < props.size()) sb.append(',');
        }
        sb.append(']');
        return "state:" + sb;
    }

    private static <T extends Comparable<T>> String getValueName(BlockState state, Property<T> property) {
        property = java.util.Objects.requireNonNull(property);
        T v = state.getValue(property);
        return property.getName(java.util.Objects.requireNonNull(v));
    }
}
