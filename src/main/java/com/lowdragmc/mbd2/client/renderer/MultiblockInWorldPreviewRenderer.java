package com.lowdragmc.mbd2.client.renderer;


import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.client.scene.forge.WorldSceneRendererImpl;
import com.lowdragmc.lowdraglib.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.client.MultiblockDebugOverlay;
import com.lowdragmc.mbd2.client.renderer.OverlayRenderUtil;
import com.lowdragmc.mbd2.common.block.MBDMachineBlock;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import lombok.Getter;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.world.level.block.RenderShape.INVISIBLE;

/**
 * Client-only renderer and cache manager for in-world multiblock structure previews.
 *
 * <p>The renderer builds a {@link TrackedDummyWorld} from a controller pattern, compiles the static block geometry into
 * shared {@link VertexBuffer}s, and renders block-entity renderers separately before translucent geometry. It also owns
 * short-lived debug overlays for invalid pattern positions. The business goal is to let players inspect expected
 * multiblock layouts in the real world without placing the preview blocks.</p>
 *
 * <p>Most state is static because only one preview is shown at a time. Public methods are intended for the Minecraft
 * client thread; {@link #prepareBuffers(TrackedDummyWorld, Collection, int)} starts a worker thread for CPU-side buffer
 * construction and schedules GPU uploads through {@link RenderSystem#recordRenderCall}. Calling {@link #cleanPreview()}
 * invalidates the current cache and lets the next preview rebuild it.</p>
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

    @Getter(lazy = true)
    private final static VertexBuffer[] BUFFERS = initBuffers();
    @Nullable
    private static TrackedDummyWorld LEVEL = null;
    @Nullable
    private static Thread THREAD = null;
    @Nullable
    private static Set<BlockPos> BLOCK_ENTITIES;
    private final static AtomicInteger PREVIEW_LEFT_TICK = new AtomicInteger(-1);
    @Nullable
    private static BlockPos PATTERN_ERROR_POS = null;
    private final static AtomicInteger PATTERN_ERROR_LEFT_TICK = new AtomicInteger(-1);

    /**
     * Allocates one static vertex buffer for each chunk render layer.
     *
     * <p>The result is cached by Lombok's lazy getter and reused until Minecraft invalidates the buffers.</p>
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

    private final static AtomicReference<CacheState> CACHE_STATE = new AtomicReference<>(CacheState.UNUSED);

    @Nullable
    private static BlockPos LAST_POS = null;
    private static int LAST_LAYER = -1;
    private static int LAST_PATTERN = -1;
    private static int NEXT_PATTERN = -1;

    /**
     * Clears the current preview world, buffer-cache state, layer selection, and pattern cycling state.
     *
     * <p>This does not delete the lazily created {@link VertexBuffer} objects; it marks their contents unused and resets
     * the state that decides whether rendering should occur.</p>
     */
    public static void cleanPreview() {
        CACHE_STATE.set(CacheState.UNUSED);
        LEVEL = null;
        BLOCK_ENTITIES = null;
        PREVIEW_LEFT_TICK.set(-1);
        LAST_POS = null;
        LAST_LAYER = -1;
        LAST_PATTERN = -1;
        NEXT_PATTERN = -1;
    }

    /**
     * Clears the preview only when it belongs to {@code pos}.
     *
     * @param pos controller position whose preview should be removed
     */
    public static void removePreview(BlockPos pos) {
        if (LAST_POS != null && LAST_POS.equals(pos)) {
            cleanPreview();
        }
    }

    /**
     * Clears the highlighted invalid pattern block and its countdown timer.
     */
    public static void clearPatternError() {
        PATTERN_ERROR_POS = null;
        PATTERN_ERROR_LEFT_TICK.set(-1);
    }

    /**
     * Shows a temporary red overlay at the pattern mismatch position.
     *
     * @param pos      world position to highlight
     * @param duration number of client ticks to keep the overlay; non-positive values expire on the next tick path
     */
    public static void showPatternErrorPos(BlockPos pos, int duration) {
        PATTERN_ERROR_POS = pos;
        PATTERN_ERROR_LEFT_TICK.set(duration);
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
        var front = controller.getFrontFacing().orElse(Direction.NORTH);
        int patternIndex = getPreviewPatternIndex(pos, controller);
        var shapeInfos = controller.getDefinition().getPatternShapeInfos(controller, patternIndex);
        if (shapeInfos.length == 0) return;
        var shapeInfo = shapeInfos[0];
        LAST_PATTERN = patternIndex;

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        IMultiController controllerBase = null;
        LEVEL = new TrackedDummyWorld();

        var blocks = shapeInfo.getBlocks();
        BlockPos controllerPatternPos = null;
        var controllerPatternFront = Direction.NORTH;
        var maxY = 0;

        // find the pos of controller
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            maxY = Math.max(maxY, aisle.length);
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    // if its controller record its position offset.
                    if (column[z] instanceof ControllerBlockInfo info) {
                        controllerPatternPos = new BlockPos(x, y, z);
                        controllerPatternFront = info.getFacing();
                    } else {
                        var blockState = column[z].getBlockState();
                        if (blockState != null && blockState.getBlock() instanceof MBDMachineBlock machineBlock &&
                                machineBlock.getDefinition() instanceof MultiblockMachineDefinition definition) {
                            controllerPatternPos = new BlockPos(x, y, z);
                            if (definition.blockProperties().rotationState().property.isPresent()) {
                                controllerPatternFront = blockState.getValue(definition.blockProperties().rotationState().property.get());
                            }
                        }
                    }
                }
            }
        }

        if (controllerPatternPos == null) { // if there is no controller found
            return;
        }

        if (LAST_POS != null && LAST_POS.equals(pos)) {
            LAST_LAYER++;
            if (LAST_LAYER >= maxY) {
                LAST_LAYER = -1;
                NEXT_PATTERN = getNextPatternIndex(controller, patternIndex);
            }
        } else {
            LAST_LAYER = -1;
            NEXT_PATTERN = patternIndex;
        }
        LAST_POS = pos;

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                if (LAST_LAYER != -1 && LAST_LAYER != y) {
                    continue;
                }
                for (int z = 0; z < column.length; z++) {
                    var blockState = column[z].getBlockState();
                    var offset = new BlockPos(x, y, z).subtract(controllerPatternPos);
                    if (blockState == null || offset.equals(new BlockPos(0, 0, 0))) continue;

                    // rotation
                    offset = switch (controllerPatternFront) {
                        case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
                        case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
                        case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
                        default -> offset.rotate(Rotation.NONE);
                    };
                    offset = switch (front) {
                        case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
                        case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
                        case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
                        default -> offset.rotate(Rotation.NONE);
                    };


                    // TODO rotation by front axis in the future
                    offset = rotateByFrontAxis(offset, front, Rotation.NONE);

                    if (blockState.getBlock() instanceof MBDMachineBlock machineBlock) {
                        var rotationState = machineBlock.getRotationState();
                        if (rotationState != RotationState.NONE && rotationState.property.isPresent()) {
                            var face = blockState.getValue(rotationState.property.get());
                            if (face.getAxis() != Direction.Axis.Y) {
                                face = switch (front) {
                                    case NORTH, UP, DOWN -> front;
                                    case SOUTH -> face.getOpposite();
                                    case WEST -> face.getCounterClockWise();
                                    case EAST -> face.getClockWise();
                                };
                            }
                            if (rotationState.test(face)) {
                                blockState = blockState.setValue(rotationState.property.get(), face);
                            }
                        }
                    }

                    BlockPos realPos = pos.offset(offset);

                    if (column[z].getBlockEntity(realPos) instanceof IMachineBlockEntity holder &&
                            holder.getMetaMachine() instanceof IMultiController cont) {
                        holder.getSelf().setLevel(LEVEL);
                        controllerBase = cont;
                    } else {
                        blockMap.put(realPos, BlockInfo.fromBlockState(blockState));
                    }
                }
            }
        }

        LEVEL.addBlocks(blockMap);
        if (controllerBase != null) {
            LEVEL.setInnerBlockEntity(controllerBase.getHolder());
        }

        prepareBuffers(LEVEL, blockMap.keySet(), duration);
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
        int matched = controller.getMultiblockState().getMatchedPatternIndex();
        if (matched >= 0 && matched < patternCount) return matched;
        if (LAST_POS != null && LAST_POS.equals(pos) && NEXT_PATTERN >= 0 && NEXT_PATTERN < patternCount) {
            return NEXT_PATTERN;
        }
        if (LAST_POS != null && LAST_POS.equals(pos) && LAST_PATTERN >= 0 && LAST_PATTERN < patternCount) {
            return LAST_PATTERN;
        }
        return 0;
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
        int matched = controller.getMultiblockState().getMatchedPatternIndex();
        if (matched >= 0 && matched < patternCount) return matched;
        if (LAST_POS != null && LAST_POS.equals(pos) && LAST_PATTERN >= 0 && LAST_PATTERN < patternCount) {
            return LAST_PATTERN;
        }
        return getPreviewPatternIndex(pos, controller);
    }

    private static int getNextPatternIndex(MBDMultiblockMachine controller, int current) {
        int patternCount = controller.getDefinition().getPatterns(controller).length;
        if (patternCount <= 1) return 0;
        int matched = controller.getMultiblockState().getMatchedPatternIndex();
        if (matched >= 0 && matched < patternCount) return matched;
        return (current + 1) % patternCount;
    }

    /**
     * Rotates a pattern offset around the axis represented by the controller front.
     *
     * @param pos      unrotated controller-relative offset
     * @param front    controller front direction that defines the rotation axis
     * @param rotation additional rotation around that axis
     * @return transformed offset in controller-relative coordinates
     */
    private static BlockPos rotateByFrontAxis(BlockPos pos, Direction front, Rotation rotation) {
        if (front.getAxis() == Direction.Axis.X) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(-pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * -pos.getY());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getX(), front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getY());
                default -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            };
        } else if (front.getAxis() == Direction.Axis.Y) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -front.getAxisDirection().getStep() * pos.getX());
                case CLOCKWISE_180 -> new BlockPos(front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        pos.getY());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getX());
                default -> new BlockPos(-front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -pos.getY());
            };
        } else if (front.getAxis() == Direction.Axis.Z) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * -pos.getY(),
                        front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                default -> pos;
            };
        }
        return pos;
    }

    /**
     * Advances preview and pattern-error lifetimes once per client tick.
     *
     * <p>When a countdown reaches zero the corresponding static state is cleared. The method also ticks the multiblock
     * debug overlay so its positions remain synchronized with preview rendering.</p>
     */
    public static void onClientTick() {
        if (PREVIEW_LEFT_TICK.get() > 0) {
            if (PREVIEW_LEFT_TICK.decrementAndGet() <= 0) {
                cleanPreview();
            }
        }
        if (PATTERN_ERROR_LEFT_TICK.get() > 0) {
            if (PATTERN_ERROR_LEFT_TICK.decrementAndGet() <= 0) {
                clearPatternError();
            }
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
        if (PATTERN_ERROR_POS != null) {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);

            RenderUtils.renderBlockOverLay(poseStack, PATTERN_ERROR_POS, 0.6f, 0, 0, 1.01f);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();

            poseStack.popPose();
        }
        if (CACHE_STATE.get() == CacheState.COMPILED && LEVEL != null) {
            poseStack.pushPose();
            Vec3 projectedView = camera.getPosition();
            poseStack.translate(-projectedView.x, -projectedView.y, -projectedView.z);

            for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                var layer = RenderType.chunkBufferLayers().get(i);
                // render TESR before translucent
                if (layer == RenderType.translucent() && BLOCK_ENTITIES != null) { // render tesr before translucent
                    var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                    for (BlockPos pos : BLOCK_ENTITIES) {
                        BlockEntity tile = LEVEL.getBlockEntity(pos);
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

                VertexBuffer vertexbuffer = getBUFFERS()[i];
                // some of stupid mod doesn't check if the buffer is invalid
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
     * Rebuilds the shared preview buffers for a dummy-world block set.
     *
     * <p>Any existing worker thread is interrupted before a new one starts. CPU-side block geometry is built off the
     * render thread, while VBO uploads are scheduled on the render thread. On successful completion this records
     * block-entity renderer positions, marks the cache compiled, and starts the preview lifetime countdown.</p>
     *
     * @param level          dummy world containing preview blocks and block entities
     * @param renderedBlocks world positions to include in the preview
     * @param duration       preview lifetime in client ticks after compilation completes
     */
    private static void prepareBuffers(TrackedDummyWorld level, Collection<BlockPos> renderedBlocks, int duration) {
        if (THREAD != null) {
            THREAD.interrupt();
        }
        CACHE_STATE.set(CacheState.COMPILING);
        // call it to init the buffers
        getBUFFERS();
        THREAD = new Thread(() -> {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            ModelBlockRenderer.enableCaching();
            PoseStack poseStack = new PoseStack();
            var randomSource = RandomSource.createNewThreadLocalInstance();
            for (int i = 0; i < RenderType.chunkBufferLayers().size(); i++) {
                if (Thread.interrupted())
                    return;
                var layer = RenderType.chunkBufferLayers().get(i);
                var buffer = new BufferBuilder(layer.bufferSize());
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                renderBlocks(level, poseStack, dispatcher, layer, new WorldSceneRenderer.VertexConsumerWrapper(buffer),
                        renderedBlocks, randomSource);
                var builder = buffer.end();
                var vertexBuffer = getBUFFERS()[i];
                Runnable toUpload = () -> {
                    if (!vertexBuffer.isInvalid()) {
                        vertexBuffer.bind();
                        vertexBuffer.upload(builder);
                        VertexBuffer.unbind();
                    }
                };
                CompletableFuture.runAsync(toUpload, runnable -> {
                    RenderSystem.recordRenderCall(runnable::run);
                });

            }
            ModelBlockRenderer.clearCache();

            // record all BlockEntities having TESR.
            Set<BlockPos> poses = new HashSet<>();
            for (BlockPos pos : renderedBlocks) {
                if (Thread.interrupted())
                    return;
                BlockEntity tile = level.getBlockEntity(pos);
                if (tile != null) {
                    if (Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
                        poses.add(pos);
                    }
                }
            }

            if (Thread.interrupted())
                return;
            BLOCK_ENTITIES = poses;
            CACHE_STATE.set(CacheState.COMPILED);
            THREAD = null;
            PREVIEW_LEFT_TICK.set(duration);
        });
        THREAD.start();
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
     */
    private static void renderBlocks(TrackedDummyWorld level, PoseStack poseStack, BlockRenderDispatcher dispatcher,
                                     RenderType layer, WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer,
                                     Collection<BlockPos> renderedBlocks, RandomSource randomSource) {
        for (BlockPos pos : renderedBlocks) {
            BlockState state = level.getBlockState(pos);
            FluidState fluidState = state.getFluidState();
            Block block = state.getBlock();
            BlockEntity te = level.getBlockEntity(pos);

            if (block == Blocks.AIR) continue;

            // render blocks
            if (state.getRenderShape() != INVISIBLE && WorldSceneRendererImpl.canRenderInLayer(dispatcher, state, pos, level, layer, randomSource)) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.8f, 0.8f, 0.8f);
                poseStack.translate(-0.5, -0.5, -0.5);

                level.setRenderFilter(p -> p.equals(pos));
                WorldSceneRendererImpl.renderBlocksForge(dispatcher, state, pos, level, poseStack, wrapperBuffer, randomSource, layer);
                level.setRenderFilter(p -> true);
                poseStack.popPose();
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
    }
}
