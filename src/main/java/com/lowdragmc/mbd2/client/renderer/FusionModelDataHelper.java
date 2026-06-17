package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.blockentity.ProxyPartBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.core.mixins.client.ModelManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Shared client-side helpers for Fusion-style model connection and model-data propagation.
 *
 * <p>The class keeps short-lived render context in {@link ThreadLocal}s so baked model code can ask which model and
 * block position is currently being rendered without changing the Forge model API. The context must be installed with
 * try-with-resources using {@link #pushModelContext(BlockPos, ResourceLocation, BakedModel)} or
 * {@link #suppressFace(Direction)} so nested renders restore the previous state. The model-manager index is rebuilt
 * lazily and guarded by synchronization because it is shared across render calls.</p>
 *
 * <p>The business goal is to make machine and proxy-part blocks connect by the effective rendered model path, including
 * controller-provided render models, instead of relying only on vanilla block-state model locations.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FusionModelDataHelper {

    private static final ThreadLocal<ResourceLocation> CURRENT_MODEL_LOCATION = new ThreadLocal<>();
    private static final ThreadLocal<BakedModel> CURRENT_BAKED_MODEL = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> CURRENT_MODEL_POS = new ThreadLocal<>();
    private static final ThreadLocal<Direction> SUPPRESSED_FACE = new ThreadLocal<>();
    private static final java.util.Set<String> DEBUGGED_MESSAGES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<BakedModel, java.util.Set<ResourceLocation>> MODEL_LOCATIONS_BY_BAKED_MODEL =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile Object indexedModelManager;

    /**
     * Installs the active model location, baked model, and block position for the current render call.
     *
     * <p>The returned context restores the exact previous thread-local values, including clearing values that were
     * absent before the call. Always close the result, preferably with try-with-resources.</p>
     *
     * @param pos           position of the block whose model is being rendered
     * @param modelLocation effective model resource being rendered
     * @param bakedModel    baked model instance used for the render
     * @return closeable context handle that restores the previous render context
     */
    public static ModelContext pushModelContext(BlockPos pos, ResourceLocation modelLocation, BakedModel bakedModel) {
        var previousModelLocation = CURRENT_MODEL_LOCATION.get();
        var previousBakedModel = CURRENT_BAKED_MODEL.get();
        var previousModelPos = CURRENT_MODEL_POS.get();
        CURRENT_MODEL_LOCATION.set(modelLocation);
        CURRENT_BAKED_MODEL.set(bakedModel);
        CURRENT_MODEL_POS.set(pos);
        return () -> {
            setOrRemove(CURRENT_MODEL_LOCATION, previousModelLocation);
            setOrRemove(CURRENT_BAKED_MODEL, previousBakedModel);
            setOrRemove(CURRENT_MODEL_POS, previousModelPos);
        };
    }

    /**
     * Suppresses one face while the current thread renders a connected model.
     *
     * <p>This is used by model loaders that need to avoid recursively rendering a face that is already being handled.
     * The returned context must be closed to restore the previous suppressed face.</p>
     *
     * @param face face to hide for the current render thread
     * @return closeable context handle that restores the previous suppressed face
     */
    public static ModelContext suppressFace(Direction face) {
        var previousFace = SUPPRESSED_FACE.get();
        SUPPRESSED_FACE.set(face);
        return () -> setOrRemove(SUPPRESSED_FACE, previousFace);
    }

    /**
     * Checks whether {@code face} is currently suppressed on this render thread.
     *
     * @param face face to test
     * @return {@code true} when {@code face} matches the active suppression context
     */
    public static boolean isSuppressedFace(Direction face) {
        var suppressedFace = SUPPRESSED_FACE.get();
        return suppressedFace != null && suppressedFace == face;
    }

    private static <T> void setOrRemove(ThreadLocal<T> threadLocal, @Nullable T value) {
        if (value == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(value);
        }
    }

    /**
     * Extracts the original Forge model data from LDL's renderer wrapper context.
     *
     * @return original model data for the current render call, or {@link ModelData#EMPTY} when none is installed
     */
    public static ModelData getCurrentModelData() {
        var currentModelData = LDLRendererModel.RendererBakedModel.CURRENT_MODEL_DATA.get();
        if (currentModelData == null) {
            return ModelData.EMPTY;
        }
        var originalModelData = currentModelData.get(LDLRendererModel.RendererBakedModel.MODEL_DATA);
        return originalModelData == null ? ModelData.EMPTY : originalModelData;
    }

    /**
     * Wraps a level so same-model neighboring machines expose the center block state during model-data calculation.
     *
     * @param level         original level/tint context
     * @param pos           center block position being rendered
     * @param state         center block state to substitute for matching neighbors
     * @param modelLocation effective model path of the center block
     * @return delegating level used only for the current model-data calculation
     */
    public static BlockAndTintGetter wrapLevel(BlockAndTintGetter level, BlockPos pos, BlockState state, ResourceLocation modelLocation) {
        return new ModelConnectionLevel(level, pos, state, modelLocation);
    }

    /**
     * Determines whether two positions should connect because their effective model paths match.
     *
     * <p>When called inside {@link #pushModelContext(BlockPos, ResourceLocation, BakedModel)}, the center position is
     * compared against that active model path and the active baked model. Outside a render context it falls back to
     * comparing both positions' effective model locations.</p>
     *
     * @param level     level containing both positions
     * @param centerPos position of the currently rendered block
     * @param otherPos  neighboring position to test
     * @return {@code true} when the positions render with the same effective model
     */
    public static boolean shouldConnectByModelPath(BlockAndTintGetter level, BlockPos centerPos, BlockPos otherPos) {
        var currentModelLocation = CURRENT_MODEL_LOCATION.get();
        var currentBakedModel = CURRENT_BAKED_MODEL.get();
        var currentModelPos = CURRENT_MODEL_POS.get();
        var otherModel = getEffectiveBlockModel(level, otherPos);
        if (currentModelLocation != null && centerPos.equals(currentModelPos)) {
            var result = otherModel.filter(currentModelLocation::equals).isPresent() ||
                    matchesCurrentBakedModel(level, otherPos, currentModelLocation, currentBakedModel);
            debugConnection(centerPos, otherPos, currentModelLocation, otherModel.orElse(null), result, "context");
            return result;
        }
        var centerModel = getEffectiveBlockModel(level, centerPos);
        var result = centerModel.isPresent() && centerModel.equals(otherModel);
        debugConnection(centerPos, otherPos, centerModel.orElse(null), otherModel.orElse(null), result, "fallback");
        return result;
    }

    private static boolean matchesCurrentBakedModel(BlockAndTintGetter level, BlockPos pos, ResourceLocation currentModelLocation, @Nullable BakedModel currentBakedModel) {
        if (currentBakedModel == null) {
            return false;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getBlockRenderer() == null) {
            return false;
        }
        var otherState = level.getBlockState(pos);
        var otherBakedModel = minecraft.getBlockRenderer().getBlockModel(otherState);
        var result = otherBakedModel == currentBakedModel || otherBakedModel.equals(currentBakedModel);
        java.util.Set<ResourceLocation> otherModelLocations = java.util.Set.of();
        if (!result) {
            var modelManager = minecraft.getModelManager();
            if (modelManager != null && currentModelLocation != null) {
                var managedCurrentModel = modelManager.getModel(currentModelLocation);
                result = managedCurrentModel != modelManager.getMissingModel() &&
                        (otherBakedModel == managedCurrentModel || otherBakedModel.equals(managedCurrentModel));
                if (!result) {
                    indexModelManager(modelManager);
                    otherModelLocations = MODEL_LOCATIONS_BY_BAKED_MODEL.getOrDefault(otherBakedModel, java.util.Set.of());
                    result = otherModelLocations.contains(currentModelLocation);
                }
            }
        }
        if (result) {
            debugOnce("baked-model-hit-" + pos + "-" + currentModelLocation + "-" + currentBakedModel.getClass().getName(),
                    "effective model connection hit by baked model, pos={}, state={}, currentModel={}, bakedModel={}, indexedLocations={}",
                    pos, otherState, currentModelLocation, currentBakedModel.getClass().getName(), otherModelLocations);
        } else {
            debugOnce("baked-model-miss-" + pos + "-" + currentModelLocation + "-" + otherBakedModel.getClass().getName(),
                    "effective model connection miss by baked model, pos={}, state={}, currentModel={}, currentBakedModel={}, otherBakedModel={}, indexedLocations={}",
                    pos, otherState, currentModelLocation, currentBakedModel.getClass().getName(), otherBakedModel.getClass().getName(), otherModelLocations);
        }
        return result;
    }

    private static void indexModelManager(net.minecraft.client.resources.model.ModelManager modelManager) {
        if (indexedModelManager == modelManager) {
            return;
        }
        synchronized (MODEL_LOCATIONS_BY_BAKED_MODEL) {
            if (indexedModelManager == modelManager) {
                return;
            }
            MODEL_LOCATIONS_BY_BAKED_MODEL.clear();
            var bakedRegistry = ((ModelManagerAccessor) modelManager).mbd2$getBakedRegistry();
            for (var entry : bakedRegistry.entrySet()) {
                MODEL_LOCATIONS_BY_BAKED_MODEL
                        .computeIfAbsent(entry.getValue(), ignored -> new java.util.HashSet<>())
                        .add(normalizeModelLocation(entry.getKey()));
            }
            indexedModelManager = modelManager;
            debugOnce("baked-model-index-" + MODEL_LOCATIONS_BY_BAKED_MODEL.size(),
                    "indexed ModelManager baked models for Fusion connection, bakedModels={}, entries={}",
                    MODEL_LOCATIONS_BY_BAKED_MODEL.size(), bakedRegistry.size());
        }
    }

    private static ResourceLocation normalizeModelLocation(ResourceLocation location) {
        if (location instanceof net.minecraft.client.resources.model.ModelResourceLocation modelResourceLocation) {
            var path = modelResourceLocation.getPath();
            if (!path.startsWith("block/") && !path.startsWith("item/")) {
                return ResourceLocation.fromNamespaceAndPath(modelResourceLocation.getNamespace(), "block/" + path);
            }
        }
        return location;
    }

    /**
     * Returns the active model location for the current render thread.
     *
     * @return current model location, or {@code null} when no Fusion model context is active
     */
    public static ResourceLocation getCurrentModelLocation() {
        return CURRENT_MODEL_LOCATION.get();
    }

    /**
     * Returns the active suppressed face for the current render thread.
     *
     * @return suppressed face, or {@code null} when no face suppression is active
     */
    public static Direction getSuppressedFace() {
        return SUPPRESSED_FACE.get();
    }

    /**
     * Logs a Fusion debug message at most once for a stable key.
     *
     * @param key     unique message key; repeated calls with the same key are ignored
     * @param message SLF4J-style message pattern
     * @param args    message arguments
     */
    public static void debugOnce(String key, String message, Object... args) {
        if (DEBUGGED_MESSAGES.add(key)) {
            MBD2.LOGGER.info("[MBD2/Fusion] " + message, args);
        }
    }

    private static void debugConnection(BlockPos centerPos, BlockPos otherPos, @Nullable ResourceLocation centerModel, @Nullable ResourceLocation otherModel, boolean result, String source) {
        if (result) {
            debugOnce("connect-hit-" + source + "-" + centerModel + "-" + otherModel,
                    "model path connection hit via {}, center={}, other={}, centerModel={}, otherModel={}",
                    source, centerPos, otherPos, centerModel, otherModel);
        } else if (centerModel != null || otherModel != null) {
            debugOnce("connect-miss-" + source + "-" + centerModel + "-" + otherModel,
                    "model path connection miss via {}, center={}, other={}, centerModel={}, otherModel={}",
                    source, centerPos, otherPos, centerModel, otherModel);
        }
    }

    /**
     * Resolves the model path that should represent a block for connection decisions.
     *
     * <p>This overload handles wrapped model-connection levels and, if needed, falls back to the live client level so
     * model checks still work when the supplied level is a lightweight render view.</p>
     *
     * @param level level or render view containing {@code pos}
     * @param pos   position to inspect
     * @return machine render model, proxy controller render model, or vanilla state model location when known
     */
    public static java.util.Optional<ResourceLocation> getEffectiveBlockModel(BlockAndTintGetter level, BlockPos pos) {
        var model = getEffectiveBlockModel((BlockGetter) level, pos);
        if (model.isPresent()) {
            return model;
        }
        if (level instanceof ModelConnectionLevel modelConnectionLevel) {
            model = modelConnectionLevel.getEffectiveBlockModel(pos);
            if (model.isPresent()) {
                return model;
            }
        }
        var clientLevel = Minecraft.getInstance().level;
        if (clientLevel != null && clientLevel != level) {
            return getEffectiveBlockModel(clientLevel, pos);
        }
        return java.util.Optional.empty();
    }

    /**
     * Resolves the model path for a block from machine metadata, proxy metadata, or vanilla block state.
     *
     * @param level block getter containing {@code pos}
     * @param pos   position to inspect
     * @return effective model location used for connection comparisons
     */
    public static java.util.Optional<ResourceLocation> getEffectiveBlockModel(BlockGetter level, BlockPos pos) {
        var machineModel = IMachine.ofMachine(level, pos)
                .filter(MBDMachine.class::isInstance)
                .map(MBDMachine.class::cast)
                .flatMap(MBDMachine::getBlockModelLocationForRendering);
        if (machineModel.isPresent()) {
            return machineModel;
        }
        if (level.getBlockEntity(pos) instanceof ProxyPartBlockEntity proxyPart && proxyPart.getControllerPos() != null) {
            return IMachine.ofMachine(level, proxyPart.getControllerPos())
                    .filter(MBDMultiblockMachine.class::isInstance)
                    .map(MBDMultiblockMachine.class::cast)
                    .flatMap(MBDMachine::getBlockModelLocationForRendering);
        }
        return getStateModelLocation(level.getBlockState(pos));
    }

    private static java.util.Optional<ResourceLocation> getStateModelLocation(BlockState state) {
        var modelLocation = BlockModelShaper.stateToModelLocation(state);
        var normalizedLocation = normalizeModelLocation(modelLocation);
        return java.util.Optional.of(normalizedLocation);
    }

    /**
     * Level wrapper used only while a Fusion model asks Forge for model data.
     *
     * <p>For neighboring positions that resolve to the same effective model as the center block, {@link #getBlockState}
     * returns the center state. This makes connected baked models see compatible neighbor state without mutating the
     * real world. All other world, tint, lighting, fluid, and block-entity queries delegate directly.</p>
     *
     * @param delegate      original level/tint context
     * @param centerPos     rendered block position
     * @param centerState   state to expose for same-model neighbors
     * @param modelLocation effective model location of the center block
     */
    private record ModelConnectionLevel(BlockAndTintGetter delegate, BlockPos centerPos, BlockState centerState,
                                        ResourceLocation modelLocation) implements BlockAndTintGetter {

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!pos.equals(centerPos) && isSameModelMachine(pos)) {
                return centerState;
            }
            return delegate.getBlockState(pos);
        }

        private boolean isSameModelMachine(BlockPos pos) {
            return getEffectiveBlockModel(pos)
                    .filter(modelLocation::equals)
                    .isPresent();
        }

        private java.util.Optional<ResourceLocation> getEffectiveBlockModel(BlockPos pos) {
            return FusionModelDataHelper.getEffectiveBlockModel(delegate, pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getBrightness(LightLayer lightLayer, BlockPos pos) {
            return delegate.getBrightness(lightLayer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return delegate.getRawBrightness(pos, amount);
        }
    }

    /**
     * Closeable handle for restoring render-thread model context.
     *
     * <p>Implementations are intentionally tiny lambdas. Calling {@link #close()} has the side effect of restoring the
     * previous thread-local state captured when the context was created.</p>
     */
    public interface ModelContext extends AutoCloseable {

        /**
         * Restores the thread-local render context captured by this handle.
         */
        @Override
        void close();
    }
}
