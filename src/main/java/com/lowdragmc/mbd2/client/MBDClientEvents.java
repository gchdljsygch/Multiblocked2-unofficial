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

/**
 * Client mod-bus registrations for MBD key mappings.
 *
 * <p>The business goal is to expose a keyboard shortcut for the gadget mode
 * wheel. Mod-bus handlers register key bindings, while the nested Forge-bus
 * handler consumes key presses during client ticks.</p>
 */
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

    /**
     * Registers the gadget mode wheel key mapping with Minecraft.
     *
     * <p>Preconditions: called by Forge on the client mod event bus. Side
     * effects: adds {@link #OPEN_GADGET_WHEEL} to the client's key mapping
     * registry.</p>
     *
     * @param event key mapping registration event
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GADGET_WHEEL);
    }

    /**
     * Forge-bus client tick handler that opens or closes the gadget mode wheel.
     */
    @Mod.EventBusSubscriber(modid = MBD2.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        /**
         * Consumes queued key presses and toggles the gadget mode wheel screen.
         *
         * <p>Preconditions: called on the logical client during tick events.
         * Only the {@link TickEvent.Phase#END} phase is processed, and no action
         * is taken before a local player exists. Side effects: may replace the
         * current screen with {@link MbdGadgetModeWheelScreen} or close that
         * screen.</p>
         *
         * @param event client tick event
         */
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
