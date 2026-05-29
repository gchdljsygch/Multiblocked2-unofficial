package com.lowdragmc.mbd2.core.mixins.manaandartifice;

import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.integration.manaandartifice.trait.ManaAndArtificeEldrinCapabilityTrait;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MBDMachine.class, remap = false)
public abstract class MBDMachineEldrinPlacementMixin {
    @Inject(method = "onMachinePlaced", at = @At("TAIL"))
    private void mbd2$setEldrinOwner(LivingEntity entity, ItemStack stack, CallbackInfo ci) {
        var machine = (MBDMachine) (Object) this;
        for (var trait : machine.getAdditionalTraits()) {
            if (trait instanceof ManaAndArtificeEldrinCapabilityTrait eldrinTrait) {
                eldrinTrait.onMachinePlaced(entity);
            }
        }
    }
}
