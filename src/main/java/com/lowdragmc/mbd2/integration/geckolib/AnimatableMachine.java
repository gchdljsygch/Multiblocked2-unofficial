package com.lowdragmc.mbd2.integration.geckolib;

import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineCustomKeyframeEvent;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

/**
 * GeckoLib animatable facade that binds animation controllers to an MBD machine instance.
 */
public class AnimatableMachine implements GeoAnimatable {
    private static final double PAUSED_RENDER_GAP_TICKS = 20;
    private static final double MAX_PAUSED_GAME_TICK_GAP = 1;

    @Getter
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this, false);
    @Getter
    private final MBDMachine machine;
    @Getter
    private final GeckolibRenderer renderer;
    @Getter
    private final GeckolibRendererModel model;
    private double lastGameTick = -1;
    private double lastRenderTick = -1;
    private boolean wasPaused = false;

    public AnimatableMachine(MBDMachine machine, GeckolibRenderer renderer) {
        this.machine = machine;
        this.renderer = renderer;
        this.model = new GeckolibRendererModel(renderer);
    }

    public boolean prepareForRender() {
        return updateAnimationTick();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        var controller = new AnimationController<>(this, state -> {
            if (renderer.scheduleStateAnimation) {
                var stateName = machine.getMachineState().name();
                var animation = renderer.getRawAnimation(stateName);
                if (animation != null) {
                    return state.setAndContinue(animation);
                }
            }
            return PlayState.STOP;
        });
        controller.setCustomInstructionKeyframeHandler(frame -> MinecraftForge.EVENT_BUS.post(new MachineCustomKeyframeEvent(machine, frame).postCustomEvent()));
        for (var animation : renderer.animations) {
            var rawAnimation = renderer.getRawAnimation(animation.getName());
            if (rawAnimation != null) {
                controller.triggerableAnim(animation.getName(), rawAnimation);
            }
        }
        controllers.add(controller);
    }

    @Override
    public double getTick(Object object) {
        return RenderUtils.getCurrentTick();
    }

    private boolean updateAnimationTick() {
        var level = machine.getLevel();
        if (level == null) {
            return false;
        }
        var currentRenderTick = RenderUtils.getCurrentTick();
        var currentTick = level.getGameTime();
        if (lastGameTick < 0) {
            lastGameTick = currentTick;
            lastRenderTick = currentRenderTick;
            wasPaused = Minecraft.getInstance().isPaused();
        } else {
            var minecraft = Minecraft.getInstance();
            var isPaused = minecraft.isPaused();
            var gameTickGap = currentTick - lastGameTick;
            var renderTickGap = currentRenderTick - lastRenderTick;
            var hadPausedRenderGap = renderTickGap > PAUSED_RENDER_GAP_TICKS && gameTickGap <= MAX_PAUSED_GAME_TICK_GAP;
            lastGameTick = currentTick;
            lastRenderTick = currentRenderTick;
            if ((wasPaused && !isPaused) || (!isPaused && hadPausedRenderGap)) {
                return true;
            }
            wasPaused = isPaused;
        }
        return false;
    }
}
