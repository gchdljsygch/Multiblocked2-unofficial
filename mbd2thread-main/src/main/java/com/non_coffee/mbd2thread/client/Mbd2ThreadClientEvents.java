package com.non_coffee.mbd2thread.client;

import com.non_coffee.mbd2thread.Mbd2Thread;
import com.non_coffee.mbd2thread.client.screen.MbdGadgetModeWheelScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Mbd2Thread.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Mbd2ThreadClientEvents {
    public static final String KEY_CATEGORY = "key.categories.mbd2thread";
    public static final KeyMapping OPEN_GADGET_WHEEL = new KeyMapping(
            "key.mbd2thread.open_gadget_wheel",
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    );

    private Mbd2ThreadClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GADGET_WHEEL);
    }

    @Mod.EventBusSubscriber(modid = Mbd2Thread.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (OPEN_GADGET_WHEEL.consumeClick()) {
                if (mc.screen instanceof MbdGadgetModeWheelScreen) {
                    mc.setScreen(null);
                } else {
                    mc.setScreen(new MbdGadgetModeWheelScreen());
                }
            }
        }
    }
}

