package com.lowdragmc.mbd2.client;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.client.screen.MbdGadgetModeWheelScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MBDClientEvents {
    public static final String KEY_CATEGORY = "key.categories.mbd2";
    public static final KeyMapping OPEN_GADGET_WHEEL = new KeyMapping(
            "key.mbd2.open_gadget_wheel",
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    );

    private MBDClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GADGET_WHEEL);
    }

    @Mod.EventBusSubscriber(modid = MBD2.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
