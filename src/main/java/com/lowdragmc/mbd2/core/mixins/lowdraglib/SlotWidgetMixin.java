package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks unsafe LDLib slot-widget cursor swaps for oversized transfer stacks.
 *
 * <p>This is the client/widget-side guard that pairs with the container guards. It consumes mouse
 * clicks before LDLib can ask vanilla slot logic to swap an oversized machine-storage stack onto
 * a normal cursor stack.</p>
 */
@Mixin(SlotWidget.class)
public abstract class SlotWidgetMixin {
    @Shadow(remap = false)
    protected Slot slotReference;

    /**
     * Consumes left/right clicks on oversized widget transfer stacks while the cursor is occupied.
     *
     * @param mouseX mouse X in widget coordinates
     * @param mouseY mouse Y in widget coordinates
     * @param button mouse button id
     * @param cir    callback receiving {@code true} when the click is consumed
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$blockOversizedSwapToCursor(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 && button != 1) {
            return;
        }
        SlotWidget self = (SlotWidget) (Object) this;
        ModularUI gui = self.getGui();
        if (slotReference == null || gui == null || !self.isMouseOverElement(mouseX, mouseY)) {
            return;
        }

        ItemStack slotStack = slotReference.getItem();
        ItemStack carried = gui.getModularUIContainer().getCarried();
        if (slotStack.isEmpty() || carried.isEmpty()) {
            return;
        }

        if (mbd2$isWidgetTransferSlot(slotReference)
                && slotStack.getCount() > slotStack.getMaxStackSize()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Identifies LDLib widget transfer slots by implementation class name.
     *
     * @param slot slot instance to test
     * @return {@code true} for LDLib widget item-transfer slots
     */
    private boolean mbd2$isWidgetTransferSlot(Slot slot) {
        return slot.getClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.SlotWidget$WidgetSlotItemTransfer");
    }
}
