package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.gui.editor.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.api.block.RotationState;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Persisted block-level properties for a machine definition.
 * <p>
 * These settings are applied when MBD2 creates the Minecraft block for a
 * definition. Many fields affect block registration or baked block behavior and
 * therefore require the definition/block to be recreated before changes are
 * visible in a running game. Runtime machine state such as renderer and shape is
 * configured separately through {@link MachineState}.
 */
@Getter
@Accessors(fluent = true)
@Builder
public class ConfigBlockProperties implements IPersistedSerializable, IConfigurable {
    /**
     * Render layer flags used when client render layers are assigned for the
     * machine block.
     */
    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class RenderTypes implements IPersistedSerializable {
        @Configurable(name = "config.block_properties.render_types.solid")
        private boolean solid;
        @Configurable(name = "config.block_properties.render_types.cutout")
        private boolean cutout = true;
        @Configurable(name = "config.block_properties.render_types.cutout_mipped")
        private boolean cutoutMipped;
        @Configurable(name = "config.block_properties.render_types.translucent")
        private boolean translucent;
    }

    @Configurable(name = "config.block_properties.render_types", subConfigurable = true, tips = {
            "config.block_properties.render_types.tooltip",
            "config.require_restart"})
    @Builder.Default
    private final RenderTypes renderTypes = new RenderTypes();

    @Configurable(name = "config.block_properties.use_ao", tips = "config.block_properties.use_ao.tooltip")
    @Builder.Default
    private boolean useAO = true;

    @Configurable(name = "config.block_properties.rotation_state", tips = {"config.block_properties.rotation_state.tooltip",
            "config.require_restart"})
    @Builder.Default
    private RotationState rotationState = RotationState.NON_Y_AXIS;

    @Configurable(name = "config.block_properties.has_collision", tips = {"config.block_properties.has_collision.tooltip",
            "config.require_restart"})
    @Builder.Default
    private boolean hasCollision = true;

    @Configurable(name = "config.block_properties.can_occlude", tips = {"config.block_properties.can_occlude.tooltip",
            "config.require_restart"})
    @Builder.Default
    private boolean canOcclude = true;

    @Configurable(name = "config.block_properties.ignited_by_lava", tips = {"config.block_properties.ignited_by_lava.tooltip",
            "config.require_restart"})
    @Builder.Default
    private boolean ignitedByLava = false;

    @Configurable(name = "config.block_properties.is_air", tips = {"config.block_properties.is_air.tooltip", "config.require_restart"})
    @Builder.Default
    private boolean isAir = false;

    @Configurable(name = "config.block_properties.is_suffocating", tips = {"config.block_properties.is_suffocating.tooltip",
            "config.require_restart"})
    @Builder.Default
    private boolean isSuffocating = false;

    @Configurable(name = "config.block_properties.emissive", tips = {"config.block_properties.emissive.tooltip",
            "config.require_restart"})
    @Builder.Default
    private boolean emissive = false;

    @Configurable(name = "config.block_properties.friction", tips = {"config.block_properties.friction.tooltip",
            "config.require_restart"})
    @NumberRange(range = {0, Float.MAX_VALUE})
    @Builder.Default
    private float friction = 0.6f;

    @Configurable(name = "config.block_properties.speed_factor", tips = {"config.block_properties.speed_factor.tooltip",
            "config.require_restart"})
    @NumberRange(range = {0, Float.MAX_VALUE})
    @Builder.Default
    private float speedFactor = 1.0f;

    @Configurable(name = "config.block_properties.jump_factor", tips = {"config.block_properties.jump_factor.tooltip",
            "config.require_restart"})
    @NumberRange(range = {0, Float.MAX_VALUE})
    @Builder.Default
    private float jumpFactor = 1.0f;

    @Configurable(name = "config.block_properties.destroy_time", tips = {"config.block_properties.destroy_time.tooltip",
            "config.require_restart"})
    @NumberRange(range = {0, Float.MAX_VALUE})
    @Builder.Default
    private float destroyTime = 1.5f;

    @Configurable(name = "config.block_properties.explosion_resistance", tips = {"config.block_properties.explosion_resistance.tooltip",
            "config.require_restart"})
    @NumberRange(range = {0, Float.MAX_VALUE})
    @Builder.Default
    private float explosionResistance = 6.0f;

    @Configurable(name = "config.block_properties.block_sound", subConfigurable = true)
    @Builder.Default
    private BlockSound blockSound = new BlockSound();

    @Configurable(name = "config.block_properties.transparent", tips = "config.block_properties.transparent.tooltip")
    @Builder.Default
    private boolean transparent = false;

    @Configurable(name = "config.block_properties.force_solid", tips = {"config.block_properties.force_solid.tooltip", "config.require_restart"})
    @Builder.Default
    private boolean forceSolid = false;

    @Configurable(name = "config.block_properties.replaceable", tips = {"config.block_properties.replaceable.tooltip", "config.require_restart"})
    @Builder.Default
    private boolean replaceable = false;

    @Configurable(name = "config.block_properties.no_particle_on_break", tips = {"config.block_properties.no_particle_on_break.tooltip", "config.require_restart"})
    @Builder.Default
    private boolean noParticleOnBreak = false;

    @Configurable(name = "config.block_properties.can_be_waterlogged", tips = "config.block_properties.can_be_waterlogged.tooltip")
    @Builder.Default
    private boolean canBeWaterlogged = false;

    @Configurable(name = "config.block_properties.collision_shape_full_block", tips = "config.block_properties.collision_shape_full_block.tooltip")
    @Builder.Default
    private boolean collisionShapeFullBlock = false;

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        IConfigurable.super.buildConfigurator(father);
        var index = 0;
        if (Editor.INSTANCE instanceof MachineEditor editor && editor.getCurrentProject() instanceof MultiblockMachineProject) {
            for (Configurator configurator : father.getConfigurators()) {
                if (configurator.getName().equals("config.block_properties.rotation_state") && configurator instanceof SelectorConfigurator<?> selector) {
                    father.removeConfigurator(selector);
                    var newSelector = new SelectorConfigurator<>(
                            "config.block_properties.rotation_state",
                            () -> rotationState,
                            r -> rotationState = r,
                            RotationState.NON_Y_AXIS,
                            true,
                            List.of(RotationState.NONE, RotationState.NON_Y_AXIS),
                            EnumAccessor::getEnumName);
                    newSelector.setTips("config.block_properties.rotation_state.tooltip", "config.require_restart");
                    father.addConfigurator(index, newSelector);
                    break;
                }
                index++;
            }
        }
    }

    /**
     * Applies this configuration to vanilla block behavior properties.
     * <p>
     * The method may mark the block as using dynamic shapes when any configured
     * machine state has a non-default or state-dependent shape.
     *
     * @param stateMachine state tree whose shapes should be considered
     * @param properties   base properties to mutate fluently
     * @return resulting block properties
     */
    public BlockBehaviour.Properties apply(StateMachine<?> stateMachine, BlockBehaviour.Properties properties) {
        if (forceSolid) {
            properties = properties.forceSolidOn();
        }
        if (replaceable) {
            properties = properties.replaceable();
        }
        if (noParticleOnBreak) {
            properties = properties.noParticlesOnBreak();
        }
        if (hasCollision) {
            properties = properties.noOcclusion();
        }
        if (!canOcclude) {
            properties = properties.noOcclusion();
        }
        if (ignitedByLava) {
            properties = properties.ignitedByLava();
        }
        if (isAir) {
            properties = properties.air();
        }
        if (isSuffocating) {
            properties = properties.isSuffocating((state, level, pos) -> true);
        }
        if (emissive) {
            properties = properties.emissiveRendering((state, level, pos) -> true);
        }
        properties = properties.friction(friction);
        properties = properties.speedFactor(speedFactor);
        properties = properties.jumpFactor(jumpFactor);
        properties = properties.destroyTime(destroyTime);
        properties = properties.explosionResistance(explosionResistance);
        properties = properties.sound(blockSound.createSoundType());
        // check dynamic shape
        VoxelShape shape = null;
        for (var state : stateMachine.states.values()) {
            var stateShape = state.getShape(Direction.NORTH);
            if (shape == null) {
                shape = stateShape;
            } else if (shape != stateShape) {
                properties.dynamicShape();
                break;
            }
        }
        if (shape != Shapes.block() || !shape.isEmpty()) {
            properties.dynamicShape();
        }
        return properties;
    }

    /**
     * Persisted sound-event ids and volume/pitch values used to build the
     * block's {@link ForgeSoundType}.
     * <p>
     * Sound events are resolved lazily from Forge registries. Missing ids fall
     * back to {@link SoundEvents#EMPTY}.
     */
    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class BlockSound implements IPersistedSerializable, IConfigurable {
        @Configurable(name = "config.block_properties.block_sound.volume", tips = "config.require_restart")
        @NumberRange(range = {0, Float.MAX_VALUE})
        private float volumeIn = 1;
        @Configurable(name = "config.block_properties.block_sound.pitch", tips = "config.require_restart")
        @NumberRange(range = {0, Float.MAX_VALUE})
        private float pitchIn = 1;
        @Persisted
        private ResourceLocation breakSound = SoundEvents.STONE_BREAK.getLocation();
        @Persisted
        private ResourceLocation stepSound = SoundEvents.STONE_STEP.getLocation();
        @Persisted
        private ResourceLocation placeSound = SoundEvents.STONE_PLACE.getLocation();
        @Persisted
        private ResourceLocation hitSound = SoundEvents.STONE_HIT.getLocation();
        @Persisted
        private ResourceLocation fallSound = SoundEvents.STONE_FALL.getLocation();

        // runtime
        private SoundEvent breakSoundEvent;
        private SoundEvent stepSoundEvent;
        private SoundEvent placeSoundEvent;
        private SoundEvent hitSoundEvent;
        private SoundEvent fallSoundEvent;

        /**
         * Creates the Forge sound type used by block properties.
         *
         * @return sound type backed by this object's lazy sound suppliers
         */
        public ForgeSoundType createSoundType() {
            return new ForgeSoundType(1.0f, 1.0f,
                    this::getBreakSoundEvent,
                    this::getStepSoundEvent,
                    this::getPlaceSoundEvent,
                    this::getHitSoundEvent,
                    this::getFallSoundEvent);
        }

        /**
         * Resolves the break sound event.
         *
         * @return registered sound event or {@link SoundEvents#EMPTY}
         */
        public SoundEvent getBreakSoundEvent() {
            if (breakSoundEvent == null) {
                breakSoundEvent = Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(breakSound)).orElse(SoundEvents.EMPTY);
            }
            return breakSoundEvent;
        }

        /**
         * Resolves the step sound event.
         *
         * @return registered sound event or {@link SoundEvents#EMPTY}
         */
        public SoundEvent getStepSoundEvent() {
            if (stepSoundEvent == null) {
                stepSoundEvent = Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(stepSound)).orElse(SoundEvents.EMPTY);
            }
            return stepSoundEvent;
        }

        /**
         * Resolves the place sound event.
         *
         * @return registered sound event or {@link SoundEvents#EMPTY}
         */
        public SoundEvent getPlaceSoundEvent() {
            if (placeSoundEvent == null) {
                placeSoundEvent = Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(placeSound)).orElse(SoundEvents.EMPTY);
            }
            return placeSoundEvent;
        }

        /**
         * Resolves the hit sound event.
         *
         * @return registered sound event or {@link SoundEvents#EMPTY}
         */
        public SoundEvent getHitSoundEvent() {
            if (hitSoundEvent == null) {
                hitSoundEvent = Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(hitSound)).orElse(SoundEvents.EMPTY);
            }
            return hitSoundEvent;
        }

        /**
         * Resolves the fall sound event.
         *
         * @return registered sound event or {@link SoundEvents#EMPTY}
         */
        public SoundEvent getFallSoundEvent() {
            if (fallSoundEvent == null) {
                fallSoundEvent = Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(fallSound)).orElse(SoundEvents.EMPTY);
            }
            return fallSoundEvent;
        }

        @Override
        public void buildConfigurator(ConfiguratorGroup father) {
            IConfigurable.super.buildConfigurator(father);
            father.addConfigurators(
                    createSoundConfigurator("config.block_properties.block_sound.break", this::breakSound, this::breakSound),
                    createSoundConfigurator("config.block_properties.block_sound.step", this::stepSound, this::stepSound),
                    createSoundConfigurator("config.block_properties.block_sound.place", this::placeSound, this::placeSound),
                    createSoundConfigurator("config.block_properties.block_sound.hit", this::hitSound, this::hitSound),
                    createSoundConfigurator("config.block_properties.block_sound.fall", this::fallSound, this::fallSound)
            );
        }

        /**
         * Creates a client-side searchable sound selector configurator.
         *
         * @param name   translation key/name for the configurator
         * @param setter setter for the selected sound id
         * @param getter getter for the current sound id
         * @return configurator that previews selected sounds in the UI
         */
        @OnlyIn(Dist.CLIENT)
        public Configurator createSoundConfigurator(String name, Consumer<ResourceLocation> setter, Supplier<ResourceLocation> getter) {
            return new SearchComponentConfigurator<>(name, getter, sound -> {
                setter.accept(sound);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ForgeRegistries.SOUND_EVENTS.getValue(sound), 1.0F));
            }, SoundEvents.STONE_PLACE.getLocation(), true, (word, find) -> {
                for (var key : ForgeRegistries.SOUND_EVENTS.getKeys()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (key.toString().contains(word.toLowerCase())) {
                        find.accept(key);
                    }
                }
            }, Object::toString);
        }
    }
}
