package com.lowdragmc.mbd2.common.entity;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.common.gui.factory.EntityMachineUIFactory;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.event.MachineOpenUIEvent;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.lowdragmc.mbd2.common.trait.entity.EntityHandlerTraitDefinition;
import com.lowdragmc.mbd2.common.trait.fluid.FluidTankCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.ForgeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.forgeenergy.LongFeEnergyCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTraitDefinition;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTraitDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class MBDEntityMachine extends MBDMachine {
    public MBDEntityMachine(IMachineBlockEntity machineHolder, EntityMachineDefinition definition, Object... args) {
        super(machineHolder, definition, args);
    }

    @Override
    public EntityMachineDefinition getDefinition() {
        return (EntityMachineDefinition) super.getDefinition();
    }

    @Override
    protected boolean canLoadTrait(TraitDefinition traitDefinition) {
        return getDefinition().isTraitSupportedOnEntity(traitDefinition);
    }

    @Override
    public Optional<Direction> getFrontFacing() {
        return entityHolderOptional()
                .flatMap(holder -> getDefinition().getEntityFrontFacing(holder.getMachineEntity().self()))
                .or(() -> super.getFrontFacing());
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        return true;
    }

    @Override
    public void setFrontFacing(Direction facing) {
        var holder = entityHolderOptional();
        if (holder.isPresent()) {
            getDefinition().setEntityFrontFacing(holder.get().getMachineEntity().self(), facing);
        } else {
            super.setFrontFacing(facing);
        }
    }

    @Override
    public void scheduleRenderUpdate() {
    }

    @Override
    public void notifyBlockUpdate() {
    }

    @Override
    public void updateState(String newValue, String oldValue) {
        if (isRemote()) {
            playStateSound(newValue);
        }
    }

    @Override
    public void updateSignal() {
    }

    @Override
    public boolean canConnectRedstone(Direction direction) {
        return false;
    }

    @Override
    public int getOutputSignal(Direction direction) {
        return 0;
    }

    @Override
    public int getOutputDirectSignal(Direction direction) {
        return 0;
    }

    @Override
    public ItemStack getDropItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void onDrops(net.minecraft.world.entity.Entity entity, List<ItemStack> drops) {
        getAdditionalTraits().forEach(trait -> trait.onMachineDrop(entity, drops));
    }

    @Override
    public boolean shouldOpenUI(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return getDefinition().machineSettings().hasUI();
    }

    @Override
    public InteractionResult openUI(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var event = new MachineOpenUIEvent(this, player);
            MinecraftForge.EVENT_BUS.post(event.postCustomEvent());
            if (event.isCanceled()) {
                return InteractionResult.PASS;
            }
            EntityMachineUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public @Nullable ModularUI createUI(Player entityPlayer) {
        return super.createUI(entityPlayer);
    }

    @Override
    public BlockState getAppearance(BlockState state, Direction side, BlockState queryState, net.minecraft.core.BlockPos queryPos) {
        return state;
    }

    public EntityMachineBlockEntity entityHolder() {
        return (EntityMachineBlockEntity) getHolder();
    }

    public Optional<EntityMachineBlockEntity> entityHolderOptional() {
        return getHolder() instanceof EntityMachineBlockEntity holder ? Optional.of(holder) : Optional.empty();
    }

    public com.lowdragmc.mbd2.api.entity.IMachineEntity getMachineEntity() {
        return entityHolder().getMachineEntity();
    }

    public static boolean isDefaultEntitySafeTrait(TraitDefinition traitDefinition) {
        return traitDefinition instanceof ItemSlotCapabilityTraitDefinition ||
                traitDefinition instanceof FluidTankCapabilityTraitDefinition ||
                traitDefinition instanceof ForgeEnergyCapabilityTraitDefinition ||
                traitDefinition instanceof LongFeEnergyCapabilityTraitDefinition ||
                traitDefinition instanceof EntityHandlerTraitDefinition ||
                traitDefinition instanceof RecipeThreadTraitDefinition;
    }
}
