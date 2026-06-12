package com.lowdragmc.mbd2.core.mixins.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.MultiCraftingTracker;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InterfaceLogic.class)
public class InterfaceLogicMixin {
    @Shadow(remap = false)
    @Final
    protected InterfaceLogicHost host;
    @Shadow(remap = false)
    @Final
    protected IManagedGridNode mainNode;
    @Shadow(remap = false)
    @Final
    protected IActionSource actionSource;
    @Shadow(remap = false)
    @Final
    protected IActionSource interfaceRequestSource;
    @Shadow(remap = false)
    @Final
    private MultiCraftingTracker craftingTracker;
    @Shadow(remap = false)
    @Final
    private IUpgradeInventory upgrades;
    @Shadow(remap = false)
    @Final
    private GenericStack[] plannedWork;
    @Shadow(remap = false)
    @Final
    private ConfigInventory storage;

    @Inject(method = "updateStorage", at = @At("HEAD"), cancellable = true, remap = false)
    private void mbd2$updateStorageWithLongAmounts(CallbackInfoReturnable<Boolean> cir) {
        boolean didSomething = false;

        for (int x = 0; x < plannedWork.length; x++) {
            var work = plannedWork[x];
            if (work != null) {
                didSomething = mbd2$usePlan(x, work.what(), work.amount()) || didSomething;
            }
        }

        cir.setReturnValue(didSomething);
    }

    @Unique
    private boolean mbd2$usePlan(int slot, AEKey what, long amount) {
        boolean changed = mbd2$tryUsePlan(slot, what, amount);
        if (changed) {
            ((InterfaceLogicAccessor) this).mbd2$updatePlan(slot);
        }
        return changed;
    }

    @Unique
    private boolean mbd2$tryUsePlan(int slot, AEKey what, long amount) {
        var grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }

        var networkInv = grid.getStorageService().getInventory();
        var energySrc = grid.getEnergyService();

        if (amount < 0) {
            amount = amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount;

            var inSlot = storage.getStack(slot);
            if (!what.matches(inSlot) || inSlot.amount() < amount) {
                return true;
            }

            var inserted = StorageHelper.poweredInsert(energySrc, networkInv, what, amount, interfaceRequestSource);
            if (inserted > 0) {
                storage.extract(slot, what, inserted, Actionable.MODULATE);
            }

            return inserted > 0;
        }

        if (((MultiCraftingTrackerAccessor) craftingTracker).mbd2$isBusy(slot)) {
            return mbd2$handleCrafting(slot, what, amount);
        } else if (amount > 0) {
            if (storage.insert(slot, what, amount, Actionable.SIMULATE) != amount) {
                return true;
            }

            if (mbd2$acquireFromNetwork(energySrc, networkInv, slot, what, amount)) {
                return true;
            }

            if (storage.getStack(slot) == null && upgrades.isInstalled(AEItems.FUZZY_CARD)) {
                FuzzyMode fuzzyMode = ((InterfaceLogic) (Object) this).getConfigManager().getSetting(Settings.FUZZY_MODE);
                for (var entry : grid.getStorageService().getCachedInventory().findFuzzy(what, fuzzyMode)) {
                    long maxAmount = storage.insert(slot, entry.getKey(), amount, Actionable.SIMULATE);
                    if (mbd2$acquireFromNetwork(energySrc, networkInv, slot, entry.getKey(), maxAmount)) {
                        return true;
                    }
                }
            }

            return mbd2$handleCrafting(slot, what, amount);
        }

        return false;
    }

    @Unique
    private boolean mbd2$acquireFromNetwork(IEnergyService energySrc, MEStorage networkInv, int slot, AEKey what,
                                            long amount) {
        var acquired = StorageHelper.poweredExtraction(energySrc, networkInv, what, amount, interfaceRequestSource);
        if (acquired > 0) {
            var inserted = storage.insert(slot, what, acquired, Actionable.MODULATE);
            if (inserted < acquired) {
                throw new IllegalStateException("bad attempt at managing inventory. Voided items: " + inserted);
            }
            return true;
        }
        return false;
    }

    @Unique
    private boolean mbd2$handleCrafting(int slot, AEKey key, long amount) {
        var grid = mainNode.getGrid();
        if (grid != null && upgrades.isInstalled(AEItems.CRAFTING_CARD) && key != null) {
            return craftingTracker.handleCrafting(slot, key, amount,
                    host.getBlockEntity().getLevel(),
                    grid.getCraftingService(),
                    actionSource);
        }

        return false;
    }
}
