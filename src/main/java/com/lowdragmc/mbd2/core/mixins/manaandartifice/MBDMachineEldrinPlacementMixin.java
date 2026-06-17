package com.lowdragmc.mbd2.core.mixins.manaandartifice;

import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.integration.manaandartifice.trait.ManaAndArtificeEldrinCapabilityTrait;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records Mana and Artifice ownership data when an MBD machine with Eldrin traits is placed.
 *
 * <p>The placement hook runs after normal MBD placement handling so all additional traits are
 * already available. Only {@link ManaAndArtificeEldrinCapabilityTrait} instances receive the
 * owner update.</p>
 */
@Mixin(value = MBDMachine.class, remap = false)
public abstract class MBDMachineEldrinPlacementMixin {
    /**
     * Forwards the placer entity to every Eldrin capability trait on the machine.
     *
     * @param entity placing entity, typically a player
     * @param stack  item stack used to place the machine
     * @param ci     mixin callback info
     */
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
