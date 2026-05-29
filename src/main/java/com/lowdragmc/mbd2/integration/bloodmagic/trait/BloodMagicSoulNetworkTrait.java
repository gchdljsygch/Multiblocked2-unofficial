package com.lowdragmc.mbd2.integration.bloodmagic.trait;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeConsumptionTracker;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTrait;
import com.lowdragmc.mbd2.integration.bloodmagic.BloodMagicSoulNetworkRecipeCapability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import wayoftime.bloodmagic.common.item.IBindable;
import wayoftime.bloodmagic.common.item.IBloodOrb;
import wayoftime.bloodmagic.core.data.Binding;
import wayoftime.bloodmagic.core.data.SoulNetwork;
import wayoftime.bloodmagic.core.data.SoulTicket;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

import java.util.List;

public class BloodMagicSoulNetworkTrait extends RecipeCapabilityTrait implements IRecipeHandlerTrait<Integer> {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(BloodMagicSoulNetworkTrait.class);

    @Persisted
    @DescSynced
    public final ItemStackTransfer orbSlot;

    public BloodMagicSoulNetworkTrait(MBDMachine machine, BloodMagicSoulNetworkTraitDefinition definition) {
        super(machine, definition);
        this.orbSlot = createOrbSlot();
        this.orbSlot.setOnContentsChanged(this::notifyListeners);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public BloodMagicSoulNetworkTraitDefinition getDefinition() {
        return (BloodMagicSoulNetworkTraitDefinition) super.getDefinition();
    }

    protected ItemStackTransfer createOrbSlot() {
        var transfer = new ItemStackTransfer(1) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        transfer.setFilter(stack -> stack.getItem() instanceof IBloodOrb);
        return transfer;
    }

    @Override
    public void onMachineDrop(Entity entity, List<ItemStack> drops) {
        var orb = getOrbStack();
        if (!orb.isEmpty()) {
            drops.add(orb);
        }
    }

    public ItemStack getOrbStack() {
        return orbSlot.getStackInSlot(0);
    }

    public boolean hasBoundOrb() {
        return getBinding() != null && getOrbCapacity() > 0;
    }

    public int getCurrentEssence() {
        var network = getSoulNetwork();
        return network == null ? 0 : network.getCurrentEssence();
    }

    public int getOrbCapacity() {
        var stack = getOrbStack();
        if (stack.getItem() instanceof IBloodOrb bloodOrb) {
            var orb = bloodOrb.getOrb(stack);
            return orb == null ? 0 : Math.max(0, orb.getCapacity());
        }
        return 0;
    }

    @Nullable
    protected Binding getBinding() {
        var stack = getOrbStack();
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof IBindable bindable) {
            return bindable.getBinding(stack);
        }
        return Binding.fromStack(stack);
    }

    @Nullable
    protected SoulNetwork getSoulNetwork() {
        var binding = getBinding();
        if (binding == null || binding.getOwnerId() == null) {
            return null;
        }
        var network = NetworkHelper.getSoulNetwork(binding);
        var stack = getOrbStack();
        if (network != null && stack.getItem() instanceof IBloodOrb bloodOrb) {
            var orb = bloodOrb.getOrb(stack);
            if (orb != null) {
                NetworkHelper.setMaxOrb(network, orb.getTier());
            }
        }
        return network;
    }

    @Override
    public List<Integer> handleRecipeInner(IO io, MBDRecipe recipe, List<Integer> left, @Nullable String slotName, boolean simulate) {
        if (!compatibleWith(io)) return left;
        var network = getSoulNetwork();
        var capacity = getOrbCapacity();
        if (network == null || capacity <= 0) {
            return left;
        }
        var required = left.stream().mapToInt(Integer::intValue).sum();
        if (required <= 0) {
            return null;
        }

        var handled = 0;
        if (io == IO.IN) {
            handled = Math.min(required, Math.min(network.getCurrentEssence(), capacity));
            if (!simulate && handled > 0) {
                handled = network.syphon(SoulTicket.item(getOrbStack(), getMachine().getLevel(), getMachine().getPos(), handled));
                if (handled > 0) {
                    RecipeConsumptionTracker.record(BloodMagicSoulNetworkRecipeCapability.CAP, handled, slotName);
                }
            }
        } else if (io == IO.OUT) {
            handled = Math.min(required, Math.max(0, capacity - network.getCurrentEssence()));
            if (!simulate && handled > 0) {
                handled = network.add(SoulTicket.item(getOrbStack(), getMachine().getLevel(), getMachine().getPos(), handled), capacity);
            }
        }
        if (!simulate && handled > 0) {
            notifyListeners();
        }
        required -= handled;
        return required > 0 ? List.of(required) : null;
    }

    @Override
    public RecipeCapability<Integer> getRecipeCapability() {
        return BloodMagicSoulNetworkRecipeCapability.CAP;
    }

    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return List.of(this);
    }
}
