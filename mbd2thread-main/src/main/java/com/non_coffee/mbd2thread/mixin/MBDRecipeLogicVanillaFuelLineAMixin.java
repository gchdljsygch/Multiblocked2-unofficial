package com.non_coffee.mbd2thread.mixin;

import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTrait;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.non_coffee.mbd2thread.trait.RecipeThreadTraitDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeLogic.class, remap = false)
public class MBDRecipeLogicVanillaFuelLineAMixin {
    @Shadow public int fuelTime;
    @Shadow public int fuelMaxTime;
    @Shadow public com.lowdragmc.mbd2.api.recipe.MBDRecipe lastFuelRecipe;
    @Shadow public com.lowdragmc.mbd2.api.machine.IMachine machine;

    @Inject(method = "handleFuelRecipe", at = @At("RETURN"), cancellable = true, remap = false)
    private void mbd2thread$vanillaFuelFallback(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (fuelTime > 0) return;
        if (!(machine instanceof MBDMachine mbdMachine)) return;
        RecipeThreadTraitDefinition cfg = null;
        for (var def : mbdMachine.getDefinition().machineSettings().traitDefinitions()) {
            if (def instanceof RecipeThreadTraitDefinition d) {
                cfg = d;
                break;
            }
        }
        if (cfg == null || !cfg.enableVanillaFuelLineA) return;
        String traitName = cfg.vanillaFuelItemTraitName;
        if (traitName == null || traitName.isBlank()) return;
        ItemSlotCapabilityTrait trait = mbdMachine.getTraitByName(ItemSlotCapabilityTrait.class, traitName);
        if (trait == null) return;
        ItemStackTransfer storage = trait.storage;
        if (storage == null) return;
        if (!tryBurnOneFuel(storage, mbdMachine)) return;
        lastFuelRecipe = null;
        cir.setReturnValue(true);
    }

    private boolean tryBurnOneFuel(ItemStackTransfer storage, MBDMachine machine) {
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int burn = ForgeHooks.getBurnTime(stack, null);
            if (burn <= 0) continue;
            ItemStack extracted = storage.extractItem(slot, 1, false);
            if (extracted.isEmpty()) continue;
            int finalBurn = ForgeHooks.getBurnTime(extracted, null);
            if (finalBurn <= 0) {
                ItemStack back = storage.insertItem(slot, extracted, false);
                if (!back.isEmpty()) dropItem(machine, back);
                continue;
            }
            ItemStack remain = extracted.getCraftingRemainingItem();
            if (!remain.isEmpty()) {
                ItemStack left = storage.insertItem(slot, remain, false);
                if (!left.isEmpty()) dropItem(machine, left);
            }
            fuelMaxTime = finalBurn;
            fuelTime = finalBurn;
            return true;
        }
        return false;
    }

    private void dropItem(MBDMachine machine, ItemStack stack) {
        if (stack.isEmpty()) return;
        var level = machine.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = machine.getPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.6;
        double z = pos.getZ() + 0.5;
        ItemEntity entity = new ItemEntity(serverLevel, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        serverLevel.addFreshEntity(entity);
    }
}
