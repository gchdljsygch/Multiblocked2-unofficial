package com.lowdragmc.mbd2.client.screen;

import com.lowdragmc.mbd2.client.MBDClientEvents;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.common.network.MBD2Network;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderBuildModePacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetBuilderPatternPacket;
import com.lowdragmc.mbd2.common.network.packets.C2SSetGadgetModePacket;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int PATTERN_BUTTON_SIZE = 12;
    private static final int PATTERN_INPUT_WIDTH = 34;
    private static final int PATTERN_CONTROL_HEIGHT = 14;
    private static final int PATTERN_CONTROL_GAP = 3;
    private static final int PATTERN_CONTROL_MARGIN = 8;
    private static final int PATTERN_INPUT_MAX_LENGTH = 10;

    private final List<Entry> entries = new ArrayList<>();
    @Nullable
    private InteractionHand targetHand;
    @Nullable
    private EditBox patternInput;
    private int selectedPatternIndex;

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

        boolean slowBuild = BuilderMaterialBindings.isSlowBuild(held);
        selectedPatternIndex = BuilderMaterialBindings.getPatternIndex(held);
        entries.add(Entry.builderToggle(
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_mode"),
                slowBuild,
                held.copyWithCount(1)));
        entries.add(Entry.builderPattern(
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern"),
                held.copyWithCount(1)));
        createPatternInput();
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
        updatePatternInputBounds();
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
                        : Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern.index", (long) selectedPatternIndex + 1);
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

        renderPatternControls(graphics, mouseX, mouseY);
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
        if (targetHand != null && isMouseOverPatternMinus(mouseX, mouseY)) {
            applyPatternInput(false);
            changePatternIndex(-1);
            return true;
        }
        if (targetHand != null && isMouseOverPatternPlus(mouseX, mouseY)) {
            applyPatternInput(false);
            changePatternIndex(1);
            return true;
        }
        if (patternInput != null && patternInput.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int selected = getSelectedIndex((int) mouseX, (int) mouseY);
        if (selected >= 0 && selected < entries.size() && targetHand != null) {
            Entry entry = entries.get(selected);
            if (entry.type == EntryType.BUILDER_PATTERN) {
                applyPatternInput(true);
            } else {
                applySelection(entry, targetHand);
            }
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        if (patternInput != null && patternInput.isFocused()) {
            applyPatternInput(true);
        }
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    /**
     * Closes the wheel when Escape, E, inventory, or the configured wheel key is pressed.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (patternInput != null && patternInput.isFocused()
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            applyPatternInput(true);
            mc.setScreen(null);
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keyCode == InputConstants.KEY_ESCAPE
                || keyCode == InputConstants.KEY_E
                || mc.options.keyInventory.isActiveAndMatches(key)
                || MBDClientEvents.OPEN_GADGET_WHEEL.isActiveAndMatches(key)) {
            if (patternInput != null && patternInput.isFocused()) {
                applyPatternInput(true);
            }
            mc.setScreen(null);
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
     * Creates the numeric pattern selector shown below the wheel.
     */
    private void createPatternInput() {
        patternInput = new EditBox(font, 0, 0, PATTERN_INPUT_WIDTH, PATTERN_CONTROL_HEIGHT,
                Component.translatable("screen." + MBD2.MOD_ID + ".gadget_wheel.builder_pattern.input"));
        patternInput.setMaxLength(PATTERN_INPUT_MAX_LENGTH);
        patternInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        updatePatternInputText();
        addRenderableWidget(patternInput);
        updatePatternInputBounds();
    }

    /**
     * Keeps the pattern input centered below the radial entries even after screen resize.
     */
    private void updatePatternInputBounds() {
        if (patternInput == null) return;

        if (findEntryIndex(EntryType.BUILDER_PATTERN) < 0) {
            patternInput.visible = false;
            return;
        }

        int groupWidth = PATTERN_BUTTON_SIZE * 2 + PATTERN_INPUT_WIDTH + PATTERN_CONTROL_GAP * 2;
        int groupLeft = width / 2 - groupWidth / 2;
        groupLeft = Math.max(4, Math.min(width - groupWidth - 4, groupLeft));

        int groupTop = height / 2 + RADIUS + ENTRY_BOX / 2 + PATTERN_CONTROL_MARGIN;
        groupTop = Math.max(4, Math.min(height - PATTERN_CONTROL_HEIGHT - 4, groupTop));

        patternInput.setX(groupLeft + PATTERN_BUTTON_SIZE + PATTERN_CONTROL_GAP);
        patternInput.setY(groupTop);
        patternInput.visible = true;
    }

    /**
     * Draws the two compact pattern step buttons. The input box itself is rendered by vanilla widget rendering.
     */
    private void renderPatternControls(GuiGraphics graphics, int mouseX, int mouseY) {
        if (patternInput == null || !patternInput.visible) return;
        renderPatternButton(graphics, patternMinusX(), patternInput.getY(), "-", isMouseOverPatternMinus(mouseX, mouseY));
        renderPatternButton(graphics, patternPlusX(), patternInput.getY(), "+", isMouseOverPatternPlus(mouseX, mouseY));
    }

    private void renderPatternButton(GuiGraphics graphics, int x, int y, String label, boolean hovered) {
        graphics.fill(x, y, x + PATTERN_BUTTON_SIZE, y + PATTERN_CONTROL_HEIGHT, hovered ? 0xEE3A3A3A : 0xC0101010);
        graphics.renderOutline(x, y, PATTERN_BUTTON_SIZE, PATTERN_CONTROL_HEIGHT, hovered ? 0xFFFFD080 : 0xFF606060);
        graphics.drawCenteredString(font, label, x + PATTERN_BUTTON_SIZE / 2, y + (PATTERN_CONTROL_HEIGHT - 8) / 2, 0xFFFFFF);
    }

    private boolean isMouseOverPatternMinus(double mouseX, double mouseY) {
        return patternInput != null
                && isInside(mouseX, mouseY, patternMinusX(), patternInput.getY(), PATTERN_BUTTON_SIZE, PATTERN_CONTROL_HEIGHT);
    }

    private boolean isMouseOverPatternPlus(double mouseX, double mouseY) {
        return patternInput != null
                && isInside(mouseX, mouseY, patternPlusX(), patternInput.getY(), PATTERN_BUTTON_SIZE, PATTERN_CONTROL_HEIGHT);
    }

    private int patternMinusX() {
        return patternInput == null ? 0 : patternInput.getX() - PATTERN_CONTROL_GAP - PATTERN_BUTTON_SIZE;
    }

    private int patternPlusX() {
        return patternInput == null ? 0 : patternInput.getX() + PATTERN_INPUT_WIDTH + PATTERN_CONTROL_GAP;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int findEntryIndex(EntryType type) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).type == type) return i;
        }
        return -1;
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
    private void applySelection(Entry entry, InteractionHand hand) {
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
            sendPatternIndex(hand, selectedPatternIndex);
        }
    }

    private void changePatternIndex(int delta) {
        setSelectedPatternIndex(clampPatternIndex((long) selectedPatternIndex + delta), true);
    }

    private void applyPatternInput(boolean notifyServer) {
        if (patternInput == null) return;
        String value = patternInput.getValue().trim();
        if (value.isEmpty()) {
            updatePatternInputText();
            return;
        }
        try {
            long visiblePatternNumber = Long.parseLong(value);
            setSelectedPatternIndex(clampPatternIndex(visiblePatternNumber - 1L), notifyServer);
        } catch (NumberFormatException ignored) {
            updatePatternInputText();
        }
    }

    private void setSelectedPatternIndex(int patternIndex, boolean notifyServer) {
        selectedPatternIndex = patternIndex;
        updatePatternInputText();
        if (notifyServer && targetHand != null) {
            sendPatternIndex(targetHand, selectedPatternIndex);
        }
    }

    private void updatePatternInputText() {
        if (patternInput == null) return;
        String value = Long.toString((long) selectedPatternIndex + 1L);
        if (!patternInput.getValue().equals(value)) {
            patternInput.setValue(value);
        }
    }

    private static int clampPatternIndex(long patternIndex) {
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, patternIndex));
    }

    private static void sendPatternIndex(InteractionHand hand, int patternIndex) {
        MBD2Network.NETWORK.sendToServer(new C2SSetBuilderPatternPacket(hand == InteractionHand.OFF_HAND ? 1 : 0, patternIndex));
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
         * Selects the builder pattern index through the adjacent controls.
         */
        BUILDER_PATTERN
    }

    /**
     * Immutable render and action data for one radial menu entry.
     *
     * @param type         action type
     * @param modeDamage   gadget damage value for mode entries
     * @param builderSlow  current builder build mode for toggle entries
     * @param label        text drawn under the icon
     * @param icon         single-item icon stack used for rendering
     */
    private record Entry(EntryType type, int modeDamage, boolean builderSlow, Component label, ItemStack icon) {
        /**
         * Creates a base gadget-mode entry and stamps the icon with the target damage value.
         */
        private static Entry mode(int modeDamage, Component label, ItemStack icon) {
            icon.setDamageValue(modeDamage);
            return new Entry(EntryType.MODE, modeDamage, false, label, icon);
        }

        /**
         * Creates a builder-mode toggle entry.
         */
        private static Entry builderToggle(Component label, boolean slow, ItemStack icon) {
            icon.setDamageValue(0);
            return new Entry(EntryType.BUILDER_TOGGLE, -1, slow, label, icon);
        }

        /**
         * Creates a builder-pattern selection entry.
         */
        private static Entry builderPattern(Component label, ItemStack icon) {
            icon.setDamageValue(0);
            return new Entry(EntryType.BUILDER_PATTERN, -1, false, label, icon);
        }
    }
}
