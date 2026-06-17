package com.lowdragmc.mbd2.integration.jade;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.util.ConfigInventory;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.fluid.FluidTankCapabilityTrait;
import com.lowdragmc.mbd2.integration.ae2.trait.MEInterfaceTrait;
import com.lowdragmc.mbd2.integration.ae2.trait.MEPatternInputTrait;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Jade fluid storage provider for MBD tanks and AE2-backed fluid buffers.
 */
public class LongFluidStorageProvider implements IServerExtensionProvider<BlockEntity, CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
    public static final LongFluidStorageProvider INSTANCE = new LongFluidStorageProvider();
    private static final ResourceLocation UID = Objects.requireNonNull(ResourceLocation.tryParse("mbd2:long_fluid_storage"));
    private static final String TITLE_KEY = "Title";

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel level, BlockEntity target, boolean showDetails) {
        return IMachine.ofMachine(target)
                .filter(MBDMachine.class::isInstance)
                .map(MBDMachine.class::cast)
                .map(this::createGroups)
                .orElse(null);
    }

    private List<ViewGroup<CompoundTag>> createGroups(MBDMachine machine) {
        var groups = new ArrayList<ViewGroup<CompoundTag>>();
        for (var trait : machine.getAdditionalTraits()) {
            if (trait instanceof FluidTankCapabilityTrait fluidTrait) {
                var views = new ArrayList<CompoundTag>();
                for (var storage : fluidTrait.storages) {
                    addFluidView(views, storage.getFluid(), storage.getCapacity());
                }
                addGroup(groups, fluidTrait.getDefinition().getName(), views);
            }
            if (MBD2.isAE2Loaded()) {
                AE2Support.addGroups(this, groups, trait);
            }
        }
        return groups.isEmpty() ? null : groups;
    }

    private void addFluidView(List<CompoundTag> views, FluidStack fluid, long capacity) {
        if (capacity <= 0) return;
        views.add(FluidView.writeDefault(JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(), fluid.getTag()), capacity));
    }

    private void addGroup(List<ViewGroup<CompoundTag>> groups, String id, List<CompoundTag> views) {
        if (views.isEmpty()) return;
        var group = new ViewGroup<>(views);
        group.id = id;
        group.getExtraData().putString(TITLE_KEY, id);
        groups.add(group);
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return ClientViewGroup.map(groups, FluidView::readDefault, (serverGroup, clientGroup) -> {
            if (groups.size() > 1 && serverGroup.getExtraData().contains(TITLE_KEY)) {
                clientGroup.title = Component.literal(serverGroup.getExtraData().getString(TITLE_KEY));
            }
        });
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 500;
    }

    private static final class AE2Support {
        private static void addGroups(LongFluidStorageProvider provider, List<ViewGroup<CompoundTag>> groups, Object trait) {
            if (trait instanceof MEInterfaceTrait interfaceTrait) {
                addMEInterfaceGroup(provider, groups, interfaceTrait);
            } else if (trait instanceof MEPatternInputTrait patternInputTrait) {
                addMEPatternInputGroup(provider, groups, patternInputTrait);
            }
        }

        private static void addMEInterfaceGroup(LongFluidStorageProvider provider, List<ViewGroup<CompoundTag>> groups, MEInterfaceTrait interfaceTrait) {
            var views = new ArrayList<CompoundTag>();
            ConfigInventory storage = interfaceTrait.getInterfaceLogic().getStorage();
            long capacity = storage.getCapacity(AEKeyType.fluids());
            for (int slot = 0; slot < storage.size(); slot++) {
                var stack = storage.getStack(slot);
                if (stack != null && stack.what() instanceof AEFluidKey fluidKey) {
                    provider.addFluidView(views, FluidStack.create(fluidKey.getFluid(), stack.amount(), fluidKey.copyTag()), capacity);
                }
            }
            provider.addGroup(groups, interfaceTrait.getDefinition().getName(), views);
        }

        private static void addMEPatternInputGroup(LongFluidStorageProvider provider, List<ViewGroup<CompoundTag>> groups, MEPatternInputTrait patternInputTrait) {
            var views = new ArrayList<CompoundTag>();
            for (var storage : patternInputTrait.getFluidStorage().getStorages()) {
                provider.addFluidView(views, storage.getFluid(), storage.getCapacity());
            }
            provider.addGroup(groups, patternInputTrait.getDefinition().getName(), views);
        }
    }
}
