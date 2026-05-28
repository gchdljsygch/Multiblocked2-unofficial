package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.gui.widget.PhantomTankWidget;
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

@Mixin(PhantomTankWidget.class)
public abstract class PhantomTankWidgetMixin extends TankWidget {
    @Unique
    private static final int MBD2TOOLS_ACTION_CLICK = 101;

    @Shadow(remap = false)
    private Consumer<FluidStack> fluidStackUpdater;

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

    @Unique
    private void mbd2$handlePhantomClick(int button, boolean shiftDown) {
        if (fluidTank == null) {
            return;
        }

        if (button == 2) {
            mbd2$applyPhantomFluid(null);
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

        FluidStack current = fluidTank.getFluidInTank(tank);
        if (current == null || current.isEmpty()) {
            return;
        }

        long updatedAmount = mbd2$adjustAmount(current.getAmount(), button, shiftDown, fluidTank.getTankCapacity(tank));
        if (updatedAmount <= 0L) {
            mbd2$applyPhantomFluid(null);
            return;
        }

        mbd2$applyPhantomFluid(current.copy(updatedAmount));
    }

    @Unique
    private long mbd2$adjustAmount(long currentAmount, int button, boolean shiftDown, long capacity) {
        long maxAmount = Math.max(capacity, 0L);
        if (shiftDown) {
            if (button == 0) {
                return Math.min(currentAmount / 2L + currentAmount % 2L, maxAmount);
            }
            if (button == 1) {
                return currentAmount > maxAmount / 2L ? maxAmount : currentAmount * 2L;
            }
            return currentAmount;
        }

        long step = Math.max(1L, FluidHelper.getBucket());
        if (button == 0) {
            return Math.max(0L, currentAmount - step);
        }
        if (button == 1) {
            return currentAmount >= maxAmount - step ? maxAmount : currentAmount + step;
        }
        return currentAmount;
    }

    @Unique
    private void mbd2$applyPhantomFluid(@Nullable FluidStack stack) {
        fluidTank.drain(fluidTank.getTankCapacity(tank), false);
        if (stack != null && !stack.isEmpty()) {
            fluidTank.fill(stack.copy(), false);
        }
        if (fluidStackUpdater != null) {
            fluidStackUpdater.accept(stack);
        }
    }
}
