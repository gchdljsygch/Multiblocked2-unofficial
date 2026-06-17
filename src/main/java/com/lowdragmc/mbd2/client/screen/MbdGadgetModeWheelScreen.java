package com.lowdragmc.mbd2.client.screen;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.common.network.MBD2Network;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderBuildModePacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderPatternPacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetGadgetModePacket;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client screen that lets a player choose MBD gadget modes from a radial menu.
 *
 * <p>The screen is non-pausing and closes immediately after a selection or when no gadget is held. Selection sends a
 * small client-to-server packet for the target hand; the screen itself does not mutate item NBT locally. All rendering
 * and input handling run on the Minecraft client thread.</p>
 */
public class MbdGadgetModeWheelScreen extends Screen {
    private static final int RADIUS = 46;
    private static final int ENTRY_BOX = 40;

    private final List<Entry> entries = new ArrayList<>();
    @Nullable
    private InteractionHand targetHand;

    /**
     * Creates an empty wheel; entries are populated during {@link #init()} from the currently held gadget.
     */
    public MbdGadgetModeWheelScreen() {
        super(Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.title"));
    }

    /**
     * Rebuilds menu entries for the currently held gadget.
     *
     * <p>If the player no longer holds an {@link MBDGadgetsItem}, the screen closes without sending packets.</p>
     */
    @Override
    protected void init() {
        entries.clear();
        targetHand = findTargetHand();
        if (targetHand == null) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        ItemStack held = getHeld(targetHand);
        entries.add(Entry.mode(0,
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.mode.builder"),
                held.copyWithCount(1)));
        entries.add(Entry.mode(1,
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.mode.recipe_debugger"),
                held.copyWithCount(1)));
        entries.add(Entry.mode(2,
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.mode.multiblock_debugger"),
                held.copyWithCount(1)));

        if (BuilderMaterialBindings.isBuilder(held)) {
            boolean slowBuild = BuilderMaterialBindings.isSlowBuild(held);
            int patternIndex = BuilderMaterialBindings.getPatternIndex(held);
            entries.add(Entry.builderToggle(
                    Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_mode"),
                    slowBuild,
                    held.copyWithCount(1)));
            entries.add(Entry.builderPattern(
                    Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern.previous"),
                    patternIndex,
                    -1,
                    held.copyWithCount(1)));
            entries.add(Entry.builderPattern(
                    Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern.next"),
                    patternIndex,
                    1,
                    held.copyWithCount(1)));
        }
    }

    /**
     * Draws the radial entries and highlights the entry under the mouse.
     *
     * @param graphics     GUI graphics context
     * @param mouseX       current mouse x coordinate
     * @param mouseY       current mouse y coordinate
     * @param partialTicks frame interpolation value supplied by Minecraft
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics);
        int cx = width / 2;
        int cy = height / 2;

        int selected = getSelectedIndex(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            double angle = (Math.PI * 2D) * i / (double) entries.size() - Math.PI / 2D;
            int ex = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int ey = cy + (int) Math.round(Math.sin(angle) * RADIUS);

            int x0 = ex - ENTRY_BOX / 2;
            int y0 = ey - ENTRY_BOX / 2;
            int x1 = ex + ENTRY_BOX / 2;
            int y1 = ey + ENTRY_BOX / 2;
            int bg = i == selected ? 0xEE3A3A3A : 0xA0101010;
            graphics.fill(x0, y0, x1, y1, bg);
            graphics.renderOutline(x0, y0, ENTRY_BOX, ENTRY_BOX, i == selected ? 0xFFFFD080 : 0xFF404040);

            if (!e.icon.isEmpty()) {
                graphics.renderItem(e.icon, ex - 8, y0 + 3);
            }
            if (e.type == EntryType.BUILDER_TOGGLE || e.type == EntryType.BUILDER_PATTERN) {
                graphics.pose().pushPose();
                graphics.pose().translate(ex, y0 + 22, 0);
                graphics.pose().scale(0.65f, 0.65f, 1);
                graphics.drawCenteredString(font, e.label, 0, 0, 0xFFFFFF);
                Component state = e.type == EntryType.BUILDER_TOGGLE
                        ? (e.builderSlow
                           ? Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_mode.slow")
                           : Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_mode.instant"))
                        : Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern.index", e.patternIndex + 1);
                graphics.drawCenteredString(font, state, 0, 10, 0xD0D0D0);
                graphics.pose().popPose();
            } else {
                graphics.pose().pushPose();
                graphics.pose().translate(ex, y0 + ENTRY_BOX - 14, 0);
                graphics.pose().scale(0.75f, 0.75f, 1);
                graphics.drawCenteredString(font, e.label, 0, 0, 0xFFFFFF);
                graphics.pose().popPose();
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * Applies the selected entry on left click and closes the wheel.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @param button GLFW mouse button
     * @return {@code true} when the click is consumed by the wheel
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int selected = getSelectedIndex((int) mouseX, (int) mouseY);
        if (selected >= 0 && selected < entries.size() && targetHand != null) {
            applySelection(entries.get(selected), targetHand);
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    /**
     * Closes the wheel when Escape is pressed.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Keeps gameplay simulation running while the radial menu is open.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Finds the wheel entry whose fixed box contains the mouse.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @return selected entry index, or {@code -1} when the mouse is outside all entries
     */
    private int getSelectedIndex(int mouseX, int mouseY) {
        int cx = width / 2;
        int cy = height / 2;
        int best = -1;
        double bestDist2 = Double.MAX_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            double angle = (Math.PI * 2D) * i / (double) entries.size() - Math.PI / 2D;
            int ex = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int ey = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            int x0 = ex - ENTRY_BOX / 2;
            int y0 = ey - ENTRY_BOX / 2;
            int x1 = ex + ENTRY_BOX / 2;
            int y1 = ey + ENTRY_BOX / 2;
            if (mouseX < x0 || mouseX >= x1 || mouseY < y0 || mouseY >= y1) continue;
            double dx = mouseX - ex;
            double dy = mouseY - ey;
            double dist2 = dx * dx + dy * dy;
            if (dist2 < bestDist2) {
                best = i;
                bestDist2 = dist2;
            }
        }
        return best;
    }

    /**
     * Finds which hand currently holds an MBD gadget.
     *
     * @return main or off hand, or {@code null} when neither hand holds the gadget
     */
    @Nullable
    private static InteractionHand findTargetHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (mc.player.getMainHandItem().getItem() instanceof MBDGadgetsItem) return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().getItem() instanceof MBDGadgetsItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /**
     * Returns the current item stack in a hand.
     *
     * @param hand hand to inspect
     * @return held stack, or {@link ItemStack#EMPTY} if the player is unavailable
     */
    private static ItemStack getHeld(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getItemInHand(hand);
    }

    /**
     * Sends the packet that corresponds to a selected wheel entry.
     *
     * @param entry selected menu entry
     * @param hand  hand containing the gadget; encoded as main-hand {@code 0} or off-hand {@code 1}
     */
    private static void applySelection(Entry entry, InteractionHand hand) {
        if (entry.type == EntryType.MODE) {
            MBD2Network.NETWORK.sendToServer(new C2SSetGadgetModePacket(hand == InteractionHand.OFF_HAND ? 1 : 0, entry.modeDamage));
            return;
        }
        if (entry.type == EntryType.BUILDER_TOGGLE) {
            boolean next = !entry.builderSlow;
            MBD2Network.NETWORK.sendToServer(new C2SSetBuilderBuildModePacket(hand == InteractionHand.OFF_HAND ? 1 : 0, next));
            return;
        }
        if (entry.type == EntryType.BUILDER_PATTERN) {
            long next = (long) entry.patternIndex + entry.patternDelta;
            int nextPattern = (int) Math.max(0, Math.min(Integer.MAX_VALUE, next));
            MBD2Network.NETWORK.sendToServer(new C2SSetBuilderPatternPacket(hand == InteractionHand.OFF_HAND ? 1 : 0, nextPattern));
        }
    }

    /**
     * Type of server-side gadget action represented by a wheel entry.
     */
    private enum EntryType {
        /**
         * Changes the base gadget mode by setting the item damage value.
         */
        MODE,
        /**
         * Toggles builder behavior between slow and instant build.
         */
        BUILDER_TOGGLE,
        /**
         * Moves the builder pattern index by a signed delta.
         */
        BUILDER_PATTERN
    }

    /**
     * Immutable render and action data for one radial menu entry.
     *
     * @param type         action type
     * @param modeDamage   gadget damage value for mode entries
     * @param builderSlow  current builder build mode for toggle entries
     * @param patternIndex current builder pattern index
     * @param patternDelta signed pattern index delta for pattern entries
     * @param label        text drawn under the icon
     * @param icon         single-item icon stack used for rendering
     */
    private record Entry(EntryType type, int modeDamage, boolean builderSlow, int patternIndex, int patternDelta,
                         Component label, ItemStack icon) {
        /**
         * Creates a base gadget-mode entry and stamps the icon with the target damage value.
         */
        private static Entry mode(int modeDamage, Component label, ItemStack icon) {
            icon.setDamageValue(modeDamage);
            return new Entry(EntryType.MODE, modeDamage, false, 0, 0, label, icon);
        }

        /**
         * Creates a builder-mode toggle entry.
         */
        private static Entry builderToggle(Component label, boolean slow, ItemStack icon) {
            icon.setDamageValue(0);
            return new Entry(EntryType.BUILDER_TOGGLE, -1, slow, 0, 0, label, icon);
        }

        /**
         * Creates a builder-pattern navigation entry.
         */
        private static Entry builderPattern(Component label, int patternIndex, int patternDelta, ItemStack icon) {
            icon.setDamageValue(0);
            return new Entry(EntryType.BUILDER_PATTERN, -1, false, patternIndex, patternDelta, label, icon);
        }
    }
}
