package com.lowdragmc.mbd2.client.renderer;


import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.client.scene.forge.WorldSceneRendererImpl;
import com.lowdragmc.lowdraglib.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.client.MultiblockDebugOverlay;
import com.lowdragmc.mbd2.client.MultiblockPreviewLayout;
import com.lowdragmc.mbd2.client.renderer.OverlayRenderUtil;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static net.minecraft.world.level.block.RenderShape.INVISIBLE;

/**
 * Client-only renderer and cache manager for in-world multiblock structure previews.
 *
 * <p>The renderer builds a {@link TrackedDummyWorld} from a controller pattern, compiles the static block geometry into
 * shared {@link VertexBuffer}s, and renders block-entity renderers separately before translucent geometry. It also owns
 * short-lived debug overlays for invalid pattern positions. The business goal is to let players inspect expected
 * multiblock layouts in the real world without placing the preview blocks.</p>
 *
 * <p>Only one preview is shown at a time. CPU-side compilation is serialized on a daemon worker, while every queued GPU
 * upload is guarded by a monotonically increasing epoch. This prevents cancelled work from replacing a newer preview.
 * Calling {@link #cleanPreview()} invalidates the active epoch and cancels any pending compilation.</p>
 */
@OnlyIn(Dist.CLIENT)
public class MultiblockInWorldPreviewRenderer {

    /**
     * Lifecycle state for the shared preview vertex-buffer cache.
     */
    private enum CacheState {
        /**
         * No preview is ready or being built.
         */
        UNUSED,
        /**
         * A worker thread is building CPU-side buffers and scheduling uploads.
         */
        COMPILING,
        /**
         * Vertex buffers and optional block-entity positions are ready to render.
         */
        COMPILED
    }

    private static final Object PREVIEW_LOCK = new Object();
    private static final ExecutorService PREVIEW_COMPILER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MBD2 Multiblock Preview Compiler");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong PREVIEW_EPOCH = new AtomicLong();
    @Nullable
    private static VertexBuffer[] BUFFERS;
    private static volatile PreviewSnapshot PREVIEW = PreviewSnapshot.unused(0);
    @Nullable
    private static Future<?> compilationFuture;
    private static volatile int previewTicksRemaining = -1;
    @Nullable
    private static volatile BlockPos patternErrorPos;
    private static volatile int patternErrorTicksRemaining = -1;

    /**
     * Allocates one static vertex buffer for each chunk render layer.
     *
     * <p>Must run on the render thread. Invalid buffers are closed and rebuilt before the next upload.</p>
     *
     * @return render-layer-aligned vertex-buffer array
     */
    private static VertexBuffer[] initBuffers() {
        List<RenderType> layers = RenderType.chunkBufferLayers();
        var buffers = new VertexBuffer[layers.size()];
        for (int j = 0; j < layers.size(); ++j) {
            buffers[j] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        return buffers;
    }

    private static VertexBuffer[] ensureVertexBuffers() {
        RenderSystem.assertOnRenderThread();
        if (hasInvalidVertexBuffers(BUFFERS)) {
            closeVertexBuffers(BUFFERS);
            BUFFERS = initBuffers();
        }
        return BUFFERS;
    }

    private static boolean hasInvalidVertexBuffers(@Nullable VertexBuffer[] buffers) {
        if (buffers == null || buffers.length != RenderType.chunkBufferLayers().size()) {
            return true;
        }
        for (VertexBuffer buffer : buffers) {
            if (buffer == null || buffer.isInvalid()) {
                return true;
            }
        }
        return false;
    }

    private static void closeVertexBuffers(@Nullable VertexBuffer[] buffers) {
        if (buffers == null) return;
        for (VertexBuffer buffer : buffers) {
            if (buffer != null) {
                buffer.close();
            }
        }
    }

    @Nullable
    private static BlockPos lastPos;
    private static int lastLayer = -1;
    private static int lastPattern = -1;
    private static int nextPattern = -1;

    private record PreviewKey(BlockPos controllerPos, MBDMultiblockMachine controller, int patternIndex, int layer,
                              Direction controllerFacing, BlockPos patternControllerPos,
                              Direction patternControllerFacing) {
    }

    private record PreviewSnapshot(long epoch, CacheState cacheState, @Nullable PreviewKey key,
                                   @Nullable TrackedDummyWorld level, List<BlockPos> renderedBlocks,
                                   Set<BlockPos> blockEntityPositions, int duration) {
        private static PreviewSnapshot unused(long epoch) {
            return new PreviewSnapshot(epoch, CacheState.UNUSED, null, null, List.of(), Set.of(), -1);
        }

        private static PreviewSnapshot compiling(long epoch, PreviewKey key, TrackedDummyWorld level,
                                                 List<BlockPos> renderedBlocks, int duration) {
            return new PreviewSnapshot(epoch, CacheState.COMPILING, key, level, renderedBlocks, Set.of(), duration);
        }

        private boolean matches(PreviewKey key) {
            return this.key != null && this.key.equals(key);
        }

        private PreviewSnapshot withDuration(int duration) {
            return new PreviewSnapshot(epoch, cacheState, key, level, renderedBlocks, blockEntityPositions, duration);
        }

        private PreviewSnapshot compiled(Set<BlockPos> blockEntityPositions) {
            return new PreviewSnapshot(epoch, CacheState.COMPILED, key, level, renderedBlocks,
                    Set.copyOf(blockEntityPositions), duration);
        }
    }

    private record PreparedBuffer(int layerIndex, BufferBuilder.RenderedBuffer buffer) {
    }

    /**
     * Clears the current preview world, buffer-cache state, layer selection, and pattern cycling state.
     *
     * <p>The cached vertex buffers remain allocated for the next preview, but all in-flight CPU and GPU work is made
     * obsolete before the shared snapshot is cleared.</p>
     */
    public static void cleanPreview() {
        synchronized (PREVIEW_LOCK) {
            invalidatePreviewLocked();
            previewTicksRemaining = -1;
        }
        lastPos = null;
        lastLayer = -1;
        lastPattern = -1;
        nextPattern = -1;
    }

    private static void invalidatePreviewLocked() {
        PREVIEW_EPOCH.incrementAndGet();
        if (compilationFuture != null) {
            compilationFuture.cancel(true);
            compilationFuture = null;
        }
        PREVIEW = PreviewSnapshot.unused(PREVIEW_EPOCH.get());
    }

    /**
     * Clears the preview only when it belongs to {@code pos}.
     *
     * @param pos controller position whose preview should be removed
     */
    public static void removePreview(BlockPos pos) {
        if (lastPos != null && lastPos.equals(pos)) {
            cleanPreview();
        }
    }

    /**
     * Clears the highlighted invalid pattern block and its countdown timer.
     */
    public static void clearPatternError() {
        patternErrorPos = null;
        patternErrorTicksRemaining = -1;
    }

    /**
     * Shows a temporary red overlay at the pattern mismatch position.
     *
     * @param pos      world position to highlight
     * @param duration number of client ticks to keep the overlay; non-positive values expire on the next tick path
     */
    public static void showPatternErrorPos(BlockPos pos, int duration) {
        patternErrorPos = pos;
        patternErrorTicksRemaining = duration;
    }

    /**
     * Builds and displays a multiblock preview for a controller.
     *
     * <p>The method chooses the active or next pattern, maps the pattern's controller-relative coordinates to world
     * coordinates using the controller front, stores preview blocks in a dummy world, and starts buffer preparation.
     * Repeated calls for the same controller cycle through visible Y layers and then through alternate patterns. If the
     * pattern has no controller marker or contains no shape info, the call is a no-op.</p>
     *
     * @param pos        world position of the controller block
     * @param controller controller machine that supplies front facing and pattern definitions
     * @param duration   preview lifetime in client ticks after the buffers finish compiling
     */
    public static void showPreview(BlockPos pos, MBDMultiblockMachine controller, int duration) {
        if (duration <= 0) {
            cleanPreview();
            return;
        }
        var front = controller.getFrontFacing().orElse(Direction.NORTH);
        int patternIndex = getPreviewPatternIndex(pos, controller);
        var shapeInfos = controller.getDefinition().getPatternShapeInfos(controller, patternIndex);
        if (shapeInfos.length == 0) return;
        var shapeInfo = shapeInfos[0];
        var blocks = shapeInfo.getBlocks();
        var controllerMarker = MultiblockPreviewLayout.findControllerMarker(blocks);
        if (controllerMarker == null) {
            return;
        }

        int maxY = Arrays.stream(blocks).mapToInt(aisle -> aisle.length).max().orElse(0);
        int layer = advancePreviewLayer(pos, controller, patternIndex, maxY);
        PreviewKey key = new PreviewKey(pos, controller, patternIndex, layer, front,
                controllerMarker.patternPosition(), controllerMarker.patternFacing());
        if (refreshCachedPreview(key, duration)) return;

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        IMultiController controllerBase = null;
        TrackedDummyWorld previewLevel = new TrackedDummyWorld();

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (layer != -1 && layer != y) {
                    continue;
                }
                for (int z = 0; z < column.length; z++) {
                    BlockInfo blockInfo = column[z];
                    if (blockInfo == null) continue;
                    BlockState blockState = blockInfo.getBlockState();
                    if (blockState == null) continue;

                    BlockPos offset = MultiblockPreviewLayout.offsetFromController(x, y, z, controllerMarker, front);
                    if (offset.equals(BlockPos.ZERO)) continue;
                    blockState = MultiblockPreviewLayout.orientMachineState(blockState, front);

                    BlockPos realPos = pos.offset(offset);

                    if (blockInfo.getBlockEntity(realPos) instanceof IMachineBlockEntity holder &&
                            holder.getMetaMachine() instanceof IMultiController cont) {
                        holder.getSelf().setLevel(previewLevel);
                        controllerBase = cont;
                    } else {
                        blockMap.put(realPos, BlockInfo.fromBlockState(blockState));
                    }
                }
            }
        }

        previewLevel.addBlocks(blockMap);
        if (controllerBase != null) {
            previewLevel.setInnerBlockEntity(controllerBase.getHolder());
        }

        prepareBuffers(previewLevel, blockMap.keySet(), duration, key);
    }

    private static int advancePreviewLayer(BlockPos pos, MBDMultiblockMachine controller, int patternIndex, int maxY) {
        if (lastPos != null && lastPos.equals(pos)) {
            lastLayer++;
            if (lastLayer >= maxY) {
                lastLayer = -1;
                nextPattern = getNextPatternIndex(controller, patternIndex);
            }
        } else {
            lastLayer = -1;
            nextPattern = patternIndex;
        }
        lastPos = pos;
        lastPattern = patternIndex;
        return lastLayer;
    }

    private static boolean refreshCachedPreview(PreviewKey key, int duration) {
        synchronized (PREVIEW_LOCK) {
            PreviewSnapshot current = PREVIEW;
            if (!current.matches(key)) return false;
            PREVIEW = current.withDuration(duration);
            if (current.cacheState() == CacheState.COMPILED) {
                previewTicksRemaining = duration;
            }
            return true;
        }
    }

    /**
     * Chooses the pattern index that the next preview build should render.
     *
     * <p>A currently matched multiblock pattern always wins. Otherwise repeated preview requests for the same
     * controller continue the locally cached pattern cycle.</p>
     *
     * @param pos        controller block position
     * @param controller controller whose pattern list is inspected
     * @return valid pattern index in {@code 0..patternCount-1}, or {@code 0} when only one pattern exists
     */
    public static int getPreviewPatternIndex(BlockPos pos, MBDMultiblockMachine controller) {
        int patternCount = controller.getDefinition().getPatterns(controller).length;
        if (patternCount <= 1) return 0;
        int matched = getMatchedPatternIndex(controller, patternCount);
        if (matched >= 0) return matched;
        int cached = getCachedPatternIndex(pos, patternCount, true);
        return cached >= 0 ? cached : 0;
    }

    /**
     * Returns the pattern currently represented by the visible preview.
     *
     * @param pos        controller block position
     * @param controller controller whose pattern list is inspected
     * @return valid pattern index in {@code 0..patternCount-1}, using the last rendered pattern when possible
     */
    public static int getCurrentPreviewPatternIndex(BlockPos pos, MBDMultiblockMachine controller) {
        int patternCount = controller.getDefinition().getPatterns(controller).length;
        if (patternCount <= 1) return 0;
        int matched = getMatchedPatternIndex(controller, patternCount);
        if (matched >= 0) return matched;
        int cached = getCachedPatternIndex(pos, patternCount, false);
        if (cached >= 0) return cached;
        return getPreviewPatternIndex(pos, controller);
    }

    private static int getNextPatternIndex(MBDMultiblockMachine controller, int current) {
        int patternCount = controller.getDefinition().getPatterns(controller).length;
        if (patternCount <= 1) return 0;
        int matched = getMatchedPatternIndex(controller, patternCount);
        if (matched >= 0) return matched;
        return (current + 1) % patternCount;
    }

    private static int getMatchedPatternIndex(MBDMultiblockMachine controller, int patternCount) {
        int matched = controller.getMultiblockState().getMatchedPatternIndex();
        return isValidPatternIndex(matched, patternCount) ? matched : -1;
    }

    private static int getCachedPatternIndex(BlockPos pos, int patternCount, boolean includeNext) {
        if (lastPos == null || !lastPos.equals(pos)) return -1;
        if (includeNext && isValidPatternIndex(nextPattern, patternCount)) return nextPattern;
        return isValidPatternIndex(lastPattern, patternCount) ? lastPattern : -1;
    }

    private static boolean isValidPatternIndex(int index, int patternCount) {
        return index >= 0 && index < patternCount;
    }

    /**
     * Advances preview and pattern-error lifetimes once per client tick.
     *
     * <p>When a countdown reaches zero the corresponding static state is cleared. The method also ticks the multiblock
     * debug overlay so its positions remain synchronized with preview rendering.</p>
     */
    public static void onClientTick() {
        if (previewTicksRemaining > 0 && --previewTicksRemaining <= 0) {
            cleanPreview();
        }
        if (patternErrorTicksRemaining > 0 && --patternErrorTicksRemaining <= 0) {
            clearPatternError();
        }
        MultiblockDebugOverlay.tick();
    }

    /**
     * Renders all active in-world preview overlays and compiled preview buffers.
     *
     * <p>The pose stack is translated from camera-relative coordinates into world coordinates for the duration of each
     * render section. The method mutates render state for depth, blend, shader uniforms, and vertex-buffer bindings, and
     * restores each layer's render state before continuing.</p>
     *
     * @param poseStack    active world render pose stack
     * @param camera       camera used to subtract the projected view position
     * @param partialTicks render interpolation fraction forwarded to block-entity renderers
     */
    public static void renderInWorldPreview(PoseStack poseStack, Camera camera, float partialTicks) {
        Set<BlockPos> positions = MultiblockDebugOverlay.getPositions();
        if (positions != null) {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            for (BlockPos pos : positions) {
                OverlayRenderUtil.renderSolidBlockOverlay(poseStack, pos, 1.0f, 0.0f, 0.0f, 0.35f, 1.01f);
            }
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();

            poseStack.popPose();
        }
        BlockPos currentPatternErrorPos = patternErrorPos;
        if (currentPatternErrorPos != null) {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);

            RenderUtils.renderBlockOverLay(poseStack, currentPatternErrorPos, 0.6f, 0, 0, 1.01f);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();

            poseStack.popPose();
        }
        PreviewSnapshot preview = PREVIEW;
        TrackedDummyWorld previewLevel = preview.level();
        if (preview.cacheState() != CacheState.COMPILED || previewLevel == null) {
            return;
        }
        VertexBuffer[] vertexBuffers = BUFFERS;
        if (hasInvalidVertexBuffers(vertexBuffers)) {
            PreviewKey key = preview.key();
            if (key != null) {
                int duration = previewTicksRemaining > 0 ? previewTicksRemaining : preview.duration();
                prepareBuffers(previewLevel, preview.renderedBlocks(), duration, key);
            }
            return;
        }

        {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

            for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                var layer = RenderType.chunkBufferLayers().get(i);
                // render TESR before translucent
                if (layer == RenderType.translucent() && !preview.blockEntityPositions().isEmpty()) {
                    var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                    for (BlockPos pos : preview.blockEntityPositions()) {
                        BlockEntity tile = previewLevel.getBlockEntity(pos);
                        if (tile != null) {
                            poseStack.pushPose();
                            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                            BlockEntityRenderer<BlockEntity> ber = Minecraft.getInstance()
                                    .getBlockEntityRenderDispatcher().getRenderer(tile);
                            if (ber != null) {
                                if (tile.hasLevel() && tile.getType().isValid(tile.getBlockState())) {
                                    ber.render(tile, partialTicks, poseStack, buffers, 0xF000F0,
                                            OverlayTexture.NO_OVERLAY);
                                }
                            }
                            poseStack.popPose();
                        }
                    }
                    buffers.endBatch();
                }

                VertexBuffer vertexbuffer = vertexBuffers[i];
                if (vertexbuffer.isInvalid() || vertexbuffer.getFormat() == null) continue;

                // render cache vbo
                layer.setupRenderState();
                poseStack.pushPose();
                ShaderInstance shaderInstance = RenderSystem.getShader();

                for (int j = 0; j < 12; ++j) {
                    int k = RenderSystem.getShaderTexture(j);
                    shaderInstance.setSampler("Sampler" + j, k);
                }

                // setup shader uniform
                if (shaderInstance.MODEL_VIEW_MATRIX != null) {
                    shaderInstance.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
                }

                if (shaderInstance.PROJECTION_MATRIX != null) {
                    shaderInstance.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                }

                if (shaderInstance.COLOR_MODULATOR != null) {
                    shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                }

                if (shaderInstance.FOG_START != null) {
                    shaderInstance.FOG_START.set(Float.MAX_VALUE);
                }

                if (shaderInstance.FOG_END != null) {
                    shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
                }

                if (shaderInstance.FOG_COLOR != null) {
                    shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                }

                if (shaderInstance.FOG_SHAPE != null) {
                    shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                }

                if (shaderInstance.TEXTURE_MATRIX != null) {
                    shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
                }

                if (shaderInstance.GAME_TIME != null) {
                    shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
                }

                RenderSystem.setupShaderLights(shaderInstance);
                shaderInstance.apply();

                RenderSystem.setShaderColor(1, 1, 1, 1);
                if (layer == RenderType.translucent()) { // TRANSLUCENT
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    RenderSystem.depthMask(false);
                } else { // SOLID
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableBlend();
                    RenderSystem.depthMask(true);
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                vertexbuffer.bind();
                vertexbuffer.draw();

                poseStack.popPose();

                shaderInstance.clear();
                VertexBuffer.unbind();
                layer.clearRenderState();
            }
            poseStack.popPose();
        }
    }

    /**
     * Starts a new preview-buffer generation for a dummy-world block set.
     *
     * <p>CPU-side work is serialized by the compiler executor. Both the worker and the render-thread upload verify the
     * generation epoch, so cancellation cannot publish stale buffers or block-entity positions.</p>
     *
     * @param level          dummy world containing preview blocks and block entities
     * @param renderedBlocks world positions to include in the preview
     * @param duration       preview lifetime in client ticks after compilation completes
     * @param key            identity of the preview geometry being compiled
     */
    private static void prepareBuffers(TrackedDummyWorld level, Collection<BlockPos> renderedBlocks, int duration,
                                       PreviewKey key) {
        List<BlockPos> copiedBlocks = List.copyOf(renderedBlocks);
        synchronized (PREVIEW_LOCK) {
            if (compilationFuture != null) {
                compilationFuture.cancel(true);
            }
            long epoch = PREVIEW_EPOCH.incrementAndGet();
            PREVIEW = PreviewSnapshot.compiling(epoch, key, level, copiedBlocks, duration);
            previewTicksRemaining = -1;
            compilationFuture = PREVIEW_COMPILER.submit(() -> compileBuffers(epoch, level, copiedBlocks));
        }
    }

    private static void compileBuffers(long epoch, TrackedDummyWorld level, List<BlockPos> renderedBlocks) {
        List<PreparedBuffer> preparedBuffers = new ArrayList<>(RenderType.chunkBufferLayers().size());
        boolean queuedForUpload = false;
        try {
            if (isCompilationCancelled(epoch)) return;
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            PoseStack poseStack = new PoseStack();
            var randomSource = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();
            try {
                for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                    if (isCompilationCancelled(epoch)) return;
                    var layer = RenderType.chunkBufferLayers().get(i);
                    var buffer = new BufferBuilder(layer.bufferSize());
                    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                    if (!renderBlocks(level, poseStack, dispatcher, layer,
                            new WorldSceneRenderer.VertexConsumerWrapper(buffer), renderedBlocks, randomSource,
                            () -> isCompilationCancelled(epoch))) {
                        buffer.end().release();
                        return;
                    }
                    preparedBuffers.add(new PreparedBuffer(i, buffer.end()));
                }
            } finally {
                ModelBlockRenderer.clearCache();
            }

            Set<BlockPos> blockEntityPositions = collectBlockEntityPositions(epoch, level, renderedBlocks);
            if (blockEntityPositions == null || isCompilationCancelled(epoch)) return;

            RenderSystem.recordRenderCall(() -> uploadCompiledBuffers(epoch, preparedBuffers, blockEntityPositions));
            queuedForUpload = true;
        } catch (Throwable throwable) {
            if (!isCompilationCancelled(epoch)) {
                MBD2.LOGGER.error("Failed to compile multiblock preview buffers", throwable);
                RenderSystem.recordRenderCall(() -> failCompilation(epoch));
            }
        } finally {
            if (!queuedForUpload) {
                releasePreparedBuffers(preparedBuffers);
            }
        }
    }

    @Nullable
    private static Set<BlockPos> collectBlockEntityPositions(long epoch, TrackedDummyWorld level,
                                                              Collection<BlockPos> renderedBlocks) {
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos pos : renderedBlocks) {
            if (isCompilationCancelled(epoch)) return null;
            BlockEntity tile = level.getBlockEntity(pos);
            if (tile != null && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
                positions.add(pos);
            }
        }
        return positions;
    }

    private static void uploadCompiledBuffers(long epoch, List<PreparedBuffer> preparedBuffers,
                                              Set<BlockPos> blockEntityPositions) {
        try {
            if (!isCurrentEpoch(epoch)) return;
            VertexBuffer[] vertexBuffers = ensureVertexBuffers();
            while (!preparedBuffers.isEmpty()) {
                if (!isCurrentEpoch(epoch)) return;
                PreparedBuffer preparedBuffer = preparedBuffers.remove(0);
                uploadBuffer(vertexBuffers[preparedBuffer.layerIndex()], preparedBuffer.buffer());
            }
            if (isCurrentEpoch(epoch)) {
                completeCompilation(epoch, blockEntityPositions);
            }
        } catch (Throwable throwable) {
            if (isCurrentEpoch(epoch)) {
                MBD2.LOGGER.error("Failed to upload multiblock preview buffers", throwable);
                failCompilation(epoch);
            }
        } finally {
            releasePreparedBuffers(preparedBuffers);
        }
    }

    private static void uploadBuffer(VertexBuffer vertexBuffer, BufferBuilder.RenderedBuffer buffer) {
        boolean ownershipTransferred = false;
        try {
            if (vertexBuffer.isInvalid()) return;
            vertexBuffer.bind();
            ownershipTransferred = true;
            vertexBuffer.upload(buffer);
        } finally {
            if (ownershipTransferred) {
                VertexBuffer.unbind();
            } else {
                buffer.release();
            }
        }
    }

    private static void completeCompilation(long epoch, Set<BlockPos> blockEntityPositions) {
        synchronized (PREVIEW_LOCK) {
            PreviewSnapshot current = PREVIEW;
            if (current.epoch() != epoch || current.cacheState() != CacheState.COMPILING || !isCurrentEpoch(epoch)) {
                return;
            }
            compilationFuture = null;
            if (current.duration() <= 0) {
                invalidatePreviewLocked();
                previewTicksRemaining = -1;
                return;
            }
            PREVIEW = current.compiled(blockEntityPositions);
            previewTicksRemaining = current.duration();
        }
    }

    private static void failCompilation(long epoch) {
        synchronized (PREVIEW_LOCK) {
            if (PREVIEW.epoch() == epoch && isCurrentEpoch(epoch)) {
                invalidatePreviewLocked();
                previewTicksRemaining = -1;
            }
        }
    }

    private static boolean isCompilationCancelled(long epoch) {
        return Thread.currentThread().isInterrupted() || !isCurrentEpoch(epoch);
    }

    private static boolean isCurrentEpoch(long epoch) {
        return PREVIEW_EPOCH.get() == epoch;
    }

    private static void releasePreparedBuffers(List<PreparedBuffer> preparedBuffers) {
        for (PreparedBuffer preparedBuffer : preparedBuffers) {
            preparedBuffer.buffer().release();
        }
        preparedBuffers.clear();
    }

    /**
     * Emits block and fluid geometry for one render layer into a CPU-side buffer.
     *
     * @param level          dummy world used for block and fluid rendering
     * @param poseStack      temporary pose stack owned by the buffer-build worker
     * @param dispatcher     Minecraft block renderer
     * @param layer          render layer currently being compiled
     * @param wrapperBuffer  vertex consumer wrapper receiving geometry
     * @param renderedBlocks block positions to compile
     * @param randomSource   thread-local random source for model rendering
     * @param cancelled      returns whether the active preview generation has been cancelled
     * @return {@code false} when compilation was cancelled before the layer completed
     */
    private static boolean renderBlocks(TrackedDummyWorld level, PoseStack poseStack, BlockRenderDispatcher dispatcher,
                                        RenderType layer, WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer,
                                        Collection<BlockPos> renderedBlocks, RandomSource randomSource,
                                        BooleanSupplier cancelled) {
        for (BlockPos pos : renderedBlocks) {
            if (cancelled.getAsBoolean()) return false;
            BlockState state = level.getBlockState(pos);
            FluidState fluidState = state.getFluidState();
            Block block = state.getBlock();

            if (block == Blocks.AIR) continue;

            // render blocks
            if (state.getRenderShape() != INVISIBLE && WorldSceneRendererImpl.canRenderInLayer(dispatcher, state, pos, level, layer, randomSource)) {
                poseStack.pushPose();
                try {
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                    poseStack.translate(0.5, 0.5, 0.5);
                    poseStack.scale(0.8f, 0.8f, 0.8f);
                    poseStack.translate(-0.5, -0.5, -0.5);

                    level.setRenderFilter(p -> p.equals(pos));
                    try {
                        WorldSceneRendererImpl.renderBlocksForge(dispatcher, state, pos, level, poseStack, wrapperBuffer,
                                randomSource, layer);
                    } finally {
                        level.setRenderFilter(p -> true);
                    }
                } finally {
                    poseStack.popPose();
                }
            }

            // render fluids
            if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                wrapperBuffer.addOffset((pos.getX() - (pos.getX() & 15)), (pos.getY() - (pos.getY() & 15)),
                        (pos.getZ() - (pos.getZ() & 15)));
                dispatcher.renderLiquid(pos, level, wrapperBuffer, state, fluidState);
            }

            wrapperBuffer.clerOffset();
            wrapperBuffer.clearColor();
        }
        return true;
    }
}
