package com.lowdragmc.mbd2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;

/**
 * Client-side ticking sound instance bound to a machine position and liveness predicate.
 *
 * <p>The business goal is to play machine-state sounds for as long as the owning machine/state remains valid. The
 * predicate is checked every client sound tick and stops playback when it returns {@code false}. Instances are
 * client-only and should be created only while the Minecraft client level is available.</p>
 */
@OnlyIn(Dist.CLIENT)
public class MachineSound extends AbstractTickableSoundInstance {

    /**
     * Whether the sound should loop continuously.
     */
    public final boolean loop;
    /**
     * Whether repeated playback should be controlled externally instead of vanilla's continuous loop flag.
     */
    public final boolean loopWithShuffle;
    /**
     * Runtime predicate that keeps the sound alive while {@code true}.
     */
    public final BooleanSupplier predicate;

    /**
     * Creates a positioned machine sound.
     *
     * @param soundEvent      sound event to play
     * @param soundSource     vanilla sound category
     * @param predicate       liveness predicate checked every client tick
     * @param pos             block position used as the sound center
     * @param loop            {@code true} for repeated playback
     * @param loopWithShuffle {@code true} when looping should not use vanilla's continuous loop flag
     * @param delay           initial delay in client ticks before playback starts
     * @param volume          playback volume; vanilla generally expects non-negative values
     * @param pitch           playback pitch; vanilla generally expects positive values
     */
    public MachineSound(SoundEvent soundEvent, SoundSource soundSource, BooleanSupplier predicate, BlockPos pos, boolean loop, boolean loopWithShuffle, int delay, float volume, float pitch) {
        super(soundEvent, soundSource, Minecraft.getInstance().level.random);
        this.predicate = predicate;
        this.loop = loop;
        this.loopWithShuffle = loopWithShuffle;
        this.looping = loop && !loopWithShuffle;
        this.delay = delay;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = Attenuation.LINEAR;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    /**
     * Stops the sound once the owning machine/state is no longer valid.
     */
    @Override
    public void tick() {
        if (!isStopped() && !predicate.getAsBoolean()) {
            release();
        }
    }

    /**
     * Stops this sound instance.
     */
    public void release() {
        stop();
    }

    /**
     * Submits this sound to the Minecraft sound manager.
     *
     * <p>Side effects: starts client playback; callers should not reuse the same instance for overlapping playback.</p>
     */
    public void play() {
        Minecraft.getInstance().getSoundManager().play(this);
    }

}
