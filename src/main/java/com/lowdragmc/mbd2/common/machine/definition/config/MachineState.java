package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib.utils.ShapeUtils;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.client.MachineSound;
import com.lowdragmc.mbd2.client.renderer.MBDClientRenderers;
import com.lowdragmc.mbd2.common.machine.definition.config.toggle.*;
import com.lowdragmc.mbd2.integration.geckolib.GeckolibRenderer;
import dev.latvian.mods.rhino.util.HideFromJS;
import lombok.*;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * One node in a machine definition's visual and behavioral state tree.
 * <p>
 * States inherit renderer, front renderer, shape, light level, render bounding
 * box, and machine sound values from their parent when the local toggle is
 * disabled or empty. Runtime machines switch between states by name while the
 * editor serializes the full tree below the root state.
 * <p>
 * Shape and rendering-box rotations are cached per direction after first use.
 * Mutating shape-related settings after runtime use should rebuild or
 * reinitialize the state tree instead of editing cached values in place.
 */
@Accessors(fluent = true)
@Getter
public class MachineState implements IConfigurable, IPersistedSerializable, Comparable<MachineState> {
    protected final String name;
    @NonNull
    protected List<MachineState> children;
    @Nullable
    protected MachineState parent;

    @Configurable(name = "config.machine_state.renderer", subConfigurable = true, tips =
            {"config.machine_state.renderer.tooltip.0", "config.machine_state.renderer.tooltip.1"})
    protected final ToggleRenderer renderer;

    @Configurable(name = "config.machine_state.front_renderer", subConfigurable = true, tips =
            {"config.machine_state.front_renderer.tooltip.0", "config.machine_state.front_renderer.tooltip.1"})
    protected final ToggleRenderer frontRenderer;

    @Configurable(name = "config.machine_state.shape", subConfigurable = true, tips =
            {"config.machine_state.shape.tooltip.0", "config.machine_state.shape.tooltip.1",
                    "config.machine_state.shape.tooltip.2", "config.machine_state.shape.tooltip.3",
                    "config.require_restart"})
    protected final ToggleShape shape;

    @Configurable(name = "config.machine_state.light", subConfigurable = true, tips =
            {"config.machine_state.light.tooltip.0", "config.machine_state.light.tooltip.1"})
    protected final ToggleLightValue lightLevel;

    @Configurable(name = "config.machine_state.rendering_box", subConfigurable = true, tips =
            {"config.machine_state.rendering_box.tooltip.0", "config.machine_state.rendering_box.tooltip.1",
                    "config.machine_state.rendering_box.tooltip.2"})
    protected final ToggleAABB renderingBox;
    @Configurable(name = "config.machine_state.is_global_visible", tips =
            "config.machine_state.is_global_visible.tooltip")
    @Setter
    protected boolean isGlobalVisible = false;
    @Configurable(name = "config.machine_state.rendering_radius", tips =
            "config.machine_state.rendering_radius.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    @Setter
    protected int renderingRadius = 64;
    @Configurable(name = "config.machine_state.machine_sound", subConfigurable = true, tips = {
            "config.machine_state.machine_sound.tooltip.0", "config.machine_state.machine_sound.tooltip.1",
            "config.machine_state.machine_sound.tooltip.2",
    })
    protected final ToggleMachineSound machineSound = new ToggleMachineSound();
    // runtime
    @Nullable
    private StateMachine<?> stateMachine;


    private final Map<Direction, VoxelShape> shapeCache = new EnumMap<>(Direction.class);
    private final Map<Direction, AABB> renderingBoxCache = new EnumMap<>(Direction.class);

    /**
     * Creates a state without a separate front renderer.
     *
     * @param name         unique state name within the owning state machine
     * @param children     child states that inherit from this state
     * @param renderer     optional block renderer override
     * @param shape        optional voxel shape override
     * @param lightLevel   optional light level override, normally {@code 0..15}
     * @param renderingBox optional render bounding box override
     */
    public MachineState(String name, @NonNull List<MachineState> children,
                        @Nullable IRenderer renderer,
                        @Nullable VoxelShape shape,
                        @Nullable Integer lightLevel,
                        @Nullable AABB renderingBox) {
        this(name, children, renderer, null, shape, lightLevel, renderingBox);
    }

    /**
     * Creates a state with optional block and front renderers.
     *
     * @param name          unique state name within the owning state machine
     * @param children      child states that inherit from this state
     * @param renderer      optional block renderer override
     * @param frontRenderer optional front-face renderer override
     * @param shape         optional voxel shape override
     * @param lightLevel    optional light level override, normally {@code 0..15}
     * @param renderingBox  optional render bounding box override
     */
    public MachineState(String name, @NonNull List<MachineState> children,
                        @Nullable IRenderer renderer,
                        @Nullable IRenderer frontRenderer,
                        @Nullable VoxelShape shape,
                        @Nullable Integer lightLevel,
                        @Nullable AABB renderingBox) {
        this.name = name;
        this.children = children;
        this.renderer = renderer == null ? new ToggleRenderer() : new ToggleRenderer(renderer);
        this.frontRenderer = frontRenderer == null ? new ToggleRenderer() : new ToggleRenderer(frontRenderer);
        this.shape = shape == null ? new ToggleShape() : new ToggleShape(shape);
        this.lightLevel = lightLevel == null ? new ToggleLightValue() : new ToggleLightValue(lightLevel);
        this.renderingBox = renderingBox == null ? new ToggleAABB() : new ToggleAABB(renderingBox);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = IPersistedSerializable.super.serializeNBT();
        tag.putString("name", name);
        var childrenList = new ListTag();
        for (var child : children) {
            childrenList.add(child.serializeNBT());
        }
        tag.put("children", childrenList);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        IPersistedSerializable.super.deserializeNBT(tag);
        var childrenList = tag.getList("children", 10);
        children = new ArrayList<>();
        for (int i = 0; i < childrenList.size(); i++) {
            var child = childrenList.getCompound(i);
            children.add(createFromTag(child));
        }
        if (this.stateMachine != null) {
            this.stateMachine.initStateMachine();
        }
    }

    /**
     * Returns whether this state has no parent.
     *
     * @return {@code true} for the root state
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Creates and attaches a new child state with default settings.
     *
     * @param name child state name
     * @return attached child state
     */
    public MachineState addChild(String name) {
        return addChild(newBuilder().name(name).build());
    }

    /**
     * Attaches an existing state as a child of this state.
     * <p>
     * Side effects: replaces the child list with a mutable copy, assigns parent/state-machine links when this state is
     * already initialized, and registers the new subtree with the owning {@link StateMachine}. The supplied state becomes
     * owned by this tree and should not be reused under another parent.
     *
     * @param state child state to attach
     * @return the attached child state
     */
    protected MachineState addChild(MachineState state) {
        children = new ArrayList<>(children);
        children.add(state);
        if (this.stateMachine != null) {
            state.parent = this;
            state.init(this.stateMachine);
        }
        return state;
    }

    /**
     * Removes a child by object identity and reinitializes the owning state
     * machine when attached.
     *
     * @param state child state to remove
     */
    public void removeChild(MachineState state) {
        children = this.children.stream().filter(s -> s != state).toList();
        if (this.stateMachine != null) {
            this.stateMachine.initStateMachine();
        }
        state.onRemoved();
    }

    private void onRemoved() {
        this.stateMachine = null;
        this.parent = null;
        this.children.forEach(MachineState::onRemoved);
    }

    /**
     * Registers this state and its descendants with a state machine.
     * <p>
     * Side effects: stores the owner, adds this state to the owner's lookup map, assigns parent links to children, and
     * recursively initializes the subtree. Call during state-machine construction or reinitialization on the owning
     * thread; the state tree is not synchronized.
     *
     * @param stateMachine state machine that owns this state tree
     */
    protected void init(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
        stateMachine.addState(this);
        for (MachineState child : children) {
            child.parent = this;
            child.init(stateMachine);
        }
    }

    /**
     * Returns the client renderer for this state assuming north-facing front.
     *
     * @return inherited or local renderer, optionally combined with a front
     * renderer
     */
    @OnlyIn(Dist.CLIENT)
    public IRenderer getRealRenderer() {
        return getRealRenderer(Direction.NORTH);
    }

    /**
     * Returns the client renderer for this state using a specific machine front.
     *
     * @param frontFacing machine front direction used to orient the front
     *                    renderer
     * @return inherited or local renderer, optionally combined with a front
     * renderer
     */
    @OnlyIn(Dist.CLIENT)
    public IRenderer getRealRenderer(Direction frontFacing) {
        var blockRenderer = getRenderer();
        var frontRenderer = getFrontRenderer();
        if (frontRenderer == IRenderer.EMPTY) {
            return blockRenderer;
        }
        return MBDClientRenderers.createMachineStateRenderer(blockRenderer, frontRenderer, frontFacing);
    }

    /**
     * Returns the inherited/local block renderer for this state.
     *
     * @return renderer, or {@link IRenderer#EMPTY} when no state in the parent
     * chain provides one
     */
    @OnlyIn(Dist.CLIENT)
    public IRenderer getRenderer() {
        if (!renderer.isEnable() || renderer.getValue() == null) {
            if (parent != null) {
                return parent.getRenderer();
            } else {
                return IRenderer.EMPTY;
            }
        }
        return renderer.getValue();
    }

    /**
     * Returns the inherited/local front renderer for this state.
     *
     * @return front renderer, or {@link IRenderer#EMPTY} when none is configured
     */
    @OnlyIn(Dist.CLIENT)
    public IRenderer getFrontRenderer() {
        if (!frontRenderer.isEnable() || frontRenderer.getValue() == null) {
            if (parent != null) {
                return parent.getFrontRenderer();
            } else {
                return IRenderer.EMPTY;
            }
        }
        return frontRenderer.getValue();
    }

    /**
     * Returns this state's inherited/local voxel shape rotated for a front
     * direction.
     *
     * @param direction front direction; {@code null} or north returns the base
     *                  shape
     * @return shape used for collision and selection
     */
    public VoxelShape getShape(@Nullable Direction direction) {
        if (!shape.isEnable() || shape.getValue() == null) {
            if (parent != null) {
                return parent.getShape(direction);
            } else {
                return Shapes.block();
            }
        }
        var value = shape.getValue();
        if (value.isEmpty() || value == Shapes.block() || direction == Direction.NORTH || direction == null)
            return value;
        return this.shapeCache.computeIfAbsent(direction, dir -> ShapeUtils.rotate(value, dir));
    }

    /**
     * Returns this state's inherited/local light level.
     *
     * @return light level, normally {@code 0..15}
     */
    public int getLightLevel() {
        if (!lightLevel.isEnable() || lightLevel.getValue() == null) {
            if (parent != null) {
                return parent.getLightLevel();
            } else {
                return 0;
            }
        }
        return lightLevel.getValue();
    }

    /**
     * Returns this state's inherited/local render bounding box rotated for a
     * front direction.
     *
     * @param direction front direction; {@code null} or north returns the base
     *                  box
     * @return render bounding box, or {@code null} to use the default shape box
     */
    @Nullable
    public AABB getRenderingBox(@Nullable Direction direction) {
        if (!renderingBox.isEnable() || renderingBox.getValue() == null) {
            if (parent != null) {
                return parent.getRenderingBox(direction);
            } else {
                return null;
            }
        }
        var value = renderingBox.getValue();
        return (direction == Direction.NORTH || direction == null) ? value : this.renderingBoxCache.computeIfAbsent(direction, dir -> ShapeUtils.rotate(value, dir));
    }

    /**
     * Creates the sound instance configured for this state.
     *
     * @param pos       block position where the sound should play
     * @param predicate live predicate used by looping sounds to decide whether
     *                  they should continue
     * @return sound instance, inherited sound, or {@code null} when disabled
     */
    @OnlyIn(Dist.CLIENT)
    @Nullable
    public MachineSound createMachineSound(BlockPos pos, BooleanSupplier predicate) {
        if (!machineSound.isEnable()) {
            if (parent != null) {
                return parent.createMachineSound(pos, predicate);
            } else {
                return null;
            }
        }
        return machineSound.createMachineSound(pos, predicate);
    }

    /**
     * Returns this state's depth in the state tree.
     *
     * @return root depth {@code 0}, increasing by one per parent edge
     */
    public int getDepth() {
        if (parent == null) {
            return 0;
        }
        return parent.getDepth() + 1;
    }

    @Override
    public int compareTo(@NotNull MachineState o) {
        return Integer.compare(this.getDepth(), o.getDepth());
    }

    /**
     * Creates a child state instance from serialized NBT.
     * <p>
     * Subclasses override {@link #newBuilder()} to preserve specialized state types during deserialization.
     *
     * @param tag serialized state data
     * @return deserialized state
     */
    protected MachineState createFromTag(CompoundTag tag) {
        var name = tag.getString("name");
        var state = newBuilder().name(name).build();
        state.deserializeNBT(tag);
        return state;
    }

    /**
     * Creates the builder used for child/default state construction.
     *
     * @return builder that creates this state's concrete type
     */
    protected Builder<? extends MachineState> newBuilder() {
        return MachineState.builder();
    }

    /**
     * Starts building a plain machine state.
     *
     * @return new builder with no name, children, or explicit render settings
     */
    public static Builder<? extends MachineState> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder for machine states.
     * <p>
     * String model helpers ignore invalid resource locations and use an empty
     * renderer on dedicated servers where client model renderers are
     * unavailable.
     */
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class Builder<T extends MachineState> {
        protected String name;
        protected List<MachineState> children = new ArrayList<>();
        @Nullable
        protected IRenderer renderer;
        @Nullable
        protected IRenderer frontRenderer;
        @Nullable
        protected VoxelShape shape;
        @Nullable
        protected Integer lightLevel;
        @Nullable
        protected AABB renderingBox;

        /**
         * Creates an empty builder.
         * <p>
         * The constructor is protected so callers use {@link MachineState#builder()} or subclass-specific factory
         * methods. Built states are mutable configuration objects and are not thread-safe while edited.
         */
        protected Builder() {
        }

        /**
         * Adds a child state to the state being built.
         *
         * @param child child state
         * @return this builder
         */
        public Builder<T> child(MachineState child) {
            children.add(child);
            return this;
        }

        /**
         * Uses a Fusion model renderer for the block renderer.
         *
         * @param modelPath model resource location
         * @return this builder
         */
        @HideFromJS
        public Builder<T> modelRenderer(ResourceLocation modelPath) {
            return renderer(createModelRenderer(modelPath));
        }

        /**
         * Uses a Fusion model renderer for the block renderer.
         *
         * @param modelPath model resource location string
         * @return this builder
         */
        public Builder<T> modelRenderer(String modelPath) {
            var location = ResourceLocation.tryParse(modelPath);
            if (location == null) {
                MBD2.LOGGER.warn("Ignored invalid machine state block model path '{}'", modelPath);
                return this;
            }
            return renderer(createModelRenderer(location));
        }

        /**
         * Uses a Fusion model renderer for the front renderer.
         *
         * @param modelPath model resource location
         * @return this builder
         */
        @HideFromJS
        public Builder<T> frontModelRenderer(ResourceLocation modelPath) {
            return frontRenderer(createModelRenderer(modelPath));
        }

        /**
         * Uses a Fusion model renderer for the front renderer.
         *
         * @param modelPath model resource location string
         * @return this builder
         */
        public Builder<T> frontModelRenderer(String modelPath) {
            var location = ResourceLocation.tryParse(modelPath);
            if (location == null) {
                MBD2.LOGGER.warn("Ignored invalid machine state front model path '{}'", modelPath);
                return this;
            }
            return frontRenderer(createModelRenderer(location));
        }

        /**
         * Creates a client renderer for a Fusion model path.
         * <p>
         * On dedicated servers this returns {@link IRenderer#EMPTY} because client renderer classes are unavailable.
         *
         * @param modelPath model resource location
         * @return Fusion model renderer on the client, otherwise {@link IRenderer#EMPTY}
         */
        protected IRenderer createModelRenderer(ResourceLocation modelPath) {
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                return IRenderer.EMPTY;
            }
            return MBDClientRenderers.createFusionModelRenderer(modelPath);
        }

        /**
         * Uses a Geckolib renderer when the Geckolib integration is loaded.
         *
         * @param modelPath     model resource location
         * @param texturePath   texture resource location
         * @param animationPath animation resource location
         * @return this builder
         */
        @HideFromJS
        public Builder<T> geckolibRenderer(ResourceLocation modelPath, ResourceLocation texturePath, ResourceLocation animationPath) {
            if (MBD2.isGeckolibLoaded()) {
                return renderer(new GeckolibRenderer(modelPath, texturePath, animationPath));
            }
            return this;
        }

        /**
         * Builds a machine state.
         *
         * @return new machine state instance
         */
        public T build() {
            return (T) new MachineState(name, children, renderer, frontRenderer, shape, lightLevel, renderingBox);
        }
    }
}
