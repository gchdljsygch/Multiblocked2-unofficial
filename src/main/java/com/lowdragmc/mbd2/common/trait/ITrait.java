package com.lowdragmc.mbd2.common.trait;


import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineTraitPanel;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.Capability;

import java.util.Collections;
import java.util.List;

/**
 * Runtime behavior module attached to an {@link MBDMachine}.
 *
 * <p>The business goal is to let a machine compose capabilities, storage,
 * rendering, ticking, recipe handling, and UI behavior from independent traits.
 * Trait lifecycle callbacks are normally invoked on the logical side that owns
 * the machine: server callbacks may mutate world or machine state, while client
 * callbacks should limit themselves to visual/client state. Implementations are
 * not assumed to be thread-safe unless they explicitly document an async
 * read-only path.</p>
 *
 * <p>To provide block capabilities, implement {@link ICapabilityProviderTrait}.
 * For recipe IO, expose {@link RecipeHandlerTrait}. For GUI contribution,
 * implement {@link IUIProviderTrait}.</p>
 */
public interface ITrait {
    /**
     * Returns the machine this trait is attached to.
     *
     * @return owning machine; never changes during the trait lifetime
     */
    MBDMachine getMachine();

    /**
     * Returns the definition that created this trait.
     *
     * @return trait definition containing editor/configuration data
     */
    TraitDefinition getDefinition();

    /**
     * Called when this trait is instantiated only for editor preview.
     *
     * <p>Business goal: let traits prepare temporary storage, models, or preview
     * state before {@link MachineTraitPanel#reloadAdditionalTraits()} renders the
     * editor scene. Side effects must remain local to preview state and should
     * not persist world data.</p>
     */
    default void onLoadingTraitInPreview() {
    }

    /**
     * Called when the owning machine is loaded into a level.
     *
     * <p>Server-side implementations may initialize world-facing state, attach
     * listeners, or register async logic. Client-side implementations may prepare
     * render state.</p>
     */
    default void onMachineLoad() {
    }

    /**
     * Called when the chunk containing the machine unloads.
     *
     * <p>Side effects typically include unregistering transient listeners or
     * releasing cached references that depend on the chunk being loaded.</p>
     */
    default void onChunkUnloaded() {
    }

    /**
     * Called when the machine object is being unloaded.
     *
     * <p>This is a broader lifecycle cleanup hook than
     * {@link #onChunkUnloaded()}; implementations should release runtime
     * subscriptions and temporary resources owned by this trait.</p>
     */
    default void onMachineUnLoad() {
    }

    /**
     * Called when the machine block or entity is being removed.
     *
     * <p>Side effects may include stopping active work, detaching multiblock
     * membership, or clearing world-facing capability state.</p>
     */
    default void onMachineRemoved() {
    }

    /**
     * Called while the machine is building its drop list.
     *
     * <p>Side effects: implementations may append to or mutate {@code drops} to
     * preserve stored resources. This hook should not mutate the world.</p>
     *
     * @param entity entity that caused the drop, when available
     * @param drops  mutable list of item stacks that will be dropped
     */
    default void onMachineDrop(Entity entity, List<ItemStack> drops) {

    }

    /**
     * Called once per logical server tick while the machine is loaded.
     *
     * <p>Side effects may include capability transfer, recipe handling, or
     * scheduled state updates. Keep expensive work offset or throttled when
     * possible.</p>
     */
    default void serverTick() {

    }

    /**
     * Called once per client tick while the machine is loaded.
     *
     * <p>Side effects should be limited to client visual, animation, and preview
     * state.</p>
     */
    default void clientTick() {

    }

    /**
     * Called when a neighboring block changes.
     *
     * @param block    block type that reported the neighbor update
     * @param fromPos  position of the changed neighbor
     * @param isMoving {@code true} when the change is caused by block movement
     */
    default void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {

    }

    /**
     * Returns recipe handlers exposed by this trait.
     *
     * <p>Business goal: let machine and controller recipe logic discover all
     * handlers contributed by a compound trait. The returned list is read during
     * recipe matching and should not be mutated concurrently.</p>
     *
     * @return recipe handler traits, or an empty list when this trait does not
     * participate in recipe IO
     */
    default List<IRecipeHandlerTrait<?>> getRecipeHandlerTraits() {
        return Collections.emptyList();
    }

    /**
     * Returns block capabilities exposed by this trait.
     *
     * <p>Business goal: let the owning block entity delegate Forge capability
     * lookup to trait-provided handlers. Callers pass these providers into
     * {@link net.minecraft.world.level.block.entity.BlockEntity#getCapability(Capability, Direction)}
     * style queries.</p>
     *
     * @return capability provider traits, or an empty list when no Forge
     * capability is exposed
     */
    default List<ICapabilityProviderTrait<?>> getCapabilityProviderTraits() {
        return Collections.emptyList();
    }
}
