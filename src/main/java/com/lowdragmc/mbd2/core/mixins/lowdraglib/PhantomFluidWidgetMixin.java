package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.widget.PhantomFluidWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.FluidTransferHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Replaces LDLib phantom-fluid clicks with long amount editing semantics.
 *
 * <p>The widget can sample fluid from the carried container item, clear with middle click, adjust
 * by bucket steps, and halve or double with shift-click. Server-backed widgets send the compact
 * click action to the server so recipe and editor data stay authoritative.</p>
 */
@Mixin(PhantomFluidWidget.class)
public abstract class PhantomFluidWidgetMixin extends TankWidget {
    @Unique
    private static final int MBD2TOOLS_ACTION_CLICK = 101;

    @Shadow(remap = false)
    private Consumer<FluidStack> phantomFluidSetter;

    @Shadow(remap = false)
    private Supplier<FluidStack> phantomFluidGetter;

    /**
     * Handles the custom synchronized click action sent by non-client-side widgets.
     *
     * @param id     LDLib client-action id
     * @param buffer action payload containing button and shift state
     * @param ci     callback cancelled when the id belongs to MBD
     */
    @Inject(method = "handleClientAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$handleCustomPhantomClickAction(int id, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (id != MBD2TOOLS_ACTION_CLICK) {
            return;
        }

        int button = buffer.readVarInt();
        boolean shiftDown = buffer.readBoolean();
        mbd2$handlePhantomClick(button, shiftDown);
        ci.cancel();
    }

    /**
     * Replaces LDLib mouse handling for clicks inside this phantom-fluid widget.
     *
     * @param mouseX mouse X in widget coordinates
     * @param mouseY mouse Y in widget coordinates
     * @param button mouse button id
     * @param cir    callback receiving {@code true} when this widget consumes the click
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$replaceMouseClickBehavior(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!isMouseOverElement(mouseX, mouseY)) {
            return;
        }

        if (isClientSideWidget) {
            mbd2$handlePhantomClick(button, isShiftDown());
        } else {
            writeClientAction(MBD2TOOLS_ACTION_CLICK, buffer -> {
                buffer.writeVarInt(button);
                buffer.writeBoolean(isShiftDown());
            });
        }

        cir.setReturnValue(true);
    }

    /**
     * Applies MBD phantom-fluid click rules.
     *
     * @param button    mouse button id; middle click clears the phantom fluid
     * @param shiftDown whether shift was held for halve/double behavior
     */
    @Unique
    private void mbd2$handlePhantomClick(int button, boolean shiftDown) {
        if (button == 2) {
            mbd2$applyPhantomFluid(FluidStack.empty());
            return;
        }

        ItemStack carried = gui.getModularUIContainer().getCarried().copy();
        if (!carried.isEmpty()) {
            carried.setCount(1);
            var handler = FluidTransferHelper.getFluidTransfer(gui.entityPlayer, gui.getModularUIContainer());
            if (handler != null) {
                FluidStack sampled = handler.drain(Long.MAX_VALUE, true);
                if (!sampled.isEmpty()) {
                    long amount = button == 0 ? sampled.getAmount() : Math.min(sampled.getAmount(), FluidHelper.getBucket());
                    mbd2$applyPhantomFluid(sampled.copy(amount));
                }
            }
            return;
        }

        FluidStack current = phantomFluidGetter.get();
        if (current == null || current.isEmpty()) {
            return;
        }

        long updatedAmount = mbd2$adjustAmount(current.getAmount(), button, shiftDown);
        if (updatedAmount <= 0L) {
            mbd2$applyPhantomFluid(FluidStack.empty());
            return;
        }

        mbd2$applyPhantomFluid(current.copy(updatedAmount));
    }

    /**
     * Computes the next phantom amount from the current amount and click gesture.
     *
     * @param currentAmount current fluid amount
     * @param button        mouse button id
     * @param shiftDown     whether shift was held
     * @return next amount, clamped to zero or {@link Long#MAX_VALUE}
     */
    @Unique
    private long mbd2$adjustAmount(long currentAmount, int button, boolean shiftDown) {
        if (shiftDown) {
            if (button == 0) {
                return currentAmount / 2L + currentAmount % 2L;
            }
            if (button == 1) {
                return currentAmount > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : currentAmount * 2L;
            }
            return currentAmount;
        }

        long step = Math.max(1L, FluidHelper.getBucket());
        if (button == 0) {
            return Math.max(0L, currentAmount - step);
        }
        if (button == 1) {
            return currentAmount >= Long.MAX_VALUE - step ? Long.MAX_VALUE : currentAmount + step;
        }
        return currentAmount;
    }

    /**
     * Writes the chosen phantom fluid through LDLib's configured setter.
     *
     * @param stack selected fluid stack, or {@code null} to clear
     */
    @Unique
    private void mbd2$applyPhantomFluid(@Nullable FluidStack stack) {
        if (phantomFluidSetter != null) {
            phantomFluidSetter.accept(stack == null ? FluidStack.empty() : stack);
        }
    }
}
