package com.lowdragmc.mbd2.api.machine;

import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeCapabilityHolder;
import com.lowdragmc.mbd2.api.capability.recipe.IRecipeHandlerTrait;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Machine contract for a multiblock part that can attach to one or more
 * controllers.
 *
 * <p>The business goal is to let controller pattern checks claim parts, expose
 * part recipe handlers to controller recipe logic, and let parts influence
 * controller recipes. Membership and recipe callbacks should run on the logical
 * server thread that owns the controller unless an implementation explicitly
 * documents an async-safe read-only path.</p>
 */
public interface IMultiPart extends IMachine {

    /**
     * Resolves a multiblock part capability from a block entity.
     *
     * @param blockEntity block entity to inspect; {@code null} yields an empty
     *                    optional
     * @return resolved part when the machine capability implements this interface
     */
    static Optional<IMultiPart> ofPart(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? Optional.empty() : blockEntity.getCapability(MBDCapabilities.CAPABILITY_MACHINE).resolve()
                .filter(IMultiPart.class::isInstance)
                .map(IMultiPart.class::cast);
    }

    /**
     * Resolves a multiblock part capability at a world position.
     *
     * @param level block getter used to read the block entity
     * @param pos   position to inspect
     * @return resolved part when present
     */
    static Optional<IMultiPart> ofPart(@Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return ofPart(level.getBlockEntity(pos));
    }

    /**
     * Returns whether this part may attach to multiple formed controllers.
     *
     * @return {@code true} when sharing is allowed
     */
    default boolean canShared() {
        return true;
    }

    /**
     * Checks whether this part is attached to a controller position.
     *
     * @param controllerPos controller block position
     * @return {@code true} when this part currently belongs to that controller
     */
    boolean hasController(BlockPos controllerPos);

    /**
     * Returns whether this part belongs to at least one formed multiblock.
     *
     * @return {@code true} when attached to a formed controller
     */
    boolean isFormed();

    /**
     * Returns attached controllers.
     *
     * @return controller list; callers should treat it as implementation-owned
     * unless documented otherwise
     */
    List<IMultiController> getControllers();

    /**
     * Handles removal from a controller's multiblock.
     *
     * <p>Side effects: implementations should remove controller membership and
     * update sync/render state as needed.</p>
     *
     * @param controller controller that no longer owns this part
     */
    void removedFromController(IMultiController controller);

    /**
     * Handles addition to a controller's multiblock.
     *
     * <p>Side effects: implementations should record controller membership and
     * update sync/render state as needed.</p>
     *
     * @param controller controller that now owns this part
     */
    void addedToController(IMultiController controller);

    /**
     * Returns recipe handlers exposed to controller recipe logic.
     *
     * <p>Business goal: let controller recipes consume or produce through part
     * traits. For this part's own recipe logic, use
     * {@link IRecipeCapabilityHolder#getRecipeCapabilitiesProxy()} instead.</p>
     *
     * @return handlers available to attached controllers
     */
    List<IRecipeHandlerTrait<?>> getRecipeHandlers();

    /**
     * Called when an attached controller's recipe status changes.
     *
     * @param controller controller whose status changed
     * @param oldStatus  previous status
     * @param newStatus  new status
     */
    default void notifyControllerRecipeStatusChanged(IMultiController controller, RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
    }

    /**
     * Called once per active controller recipe tick.
     *
     * <p>Side effects: implementation-specific. Return {@code true} to interrupt
     * controller work.</p>
     *
     * @param controller controller that is ticking recipe work
     * @return {@code true} to interrupt work; {@code false} to continue
     */
    default boolean onControllerWorking(IMultiController controller) {
        return false;
    }

    /**
     * Called when controller recipe logic enters or remains in waiting state.
     *
     * @param controller controller whose recipe is waiting
     */
    default void onControllerWaiting(IMultiController controller) {

    }

    /**
     * Called during controller recipe completion before outputs are produced.
     *
     * @param controller controller finishing or cleaning up recipe work
     */
    default void afterControllerWorking(IMultiController controller) {

    }

    /**
     * Called before controller recipe setup commits to working state.
     *
     * @param controller controller about to start a recipe
     * @return {@code true} to veto setup; {@code false} to continue
     */
    default boolean beforeControllerWorking(IMultiController controller) {
        return false;
    }

    /**
     * Modifies an attached controller's candidate recipe.
     *
     * <p>Business goal: let parts contribute tier checks, overclocking, chance
     * changes, or other recipe transforms. Side effects should be avoided because
     * controller recipe logic calls this during candidate validation.</p>
     *
     * @param recipe                candidate recipe detected by the controller's recipe type
     * @param controllerRecipeLogic controller recipe logic performing validation
     * @return modified recipe, original recipe, or {@code null} when unavailable
     */
    default @Nullable MBDRecipe modifyControllerRecipe(@Nonnull MBDRecipe recipe, RecipeLogic controllerRecipeLogic) {
        return recipe;
    }

    /**
     * Returns this part's controller-recipe parallelization contribution.
     *
     * @param recipe                candidate controller recipe
     * @param controllerRecipeLogic controller recipe logic performing validation
     * @return modifier merged into the controller's max parallel calculation
     */
    default ContentModifier getMaxControllerParallel(@Nonnull MBDRecipe recipe, RecipeLogic controllerRecipeLogic) {
        return ContentModifier.IDENTITY;
    }

    /**
     * Returns whether the controller should remodify recipes before each cycle.
     *
     * @return {@code true} when this part's dynamic state can change the modified
     * recipe between completions
     */
    default boolean alwaysTryModifyControllerRecipe() {
        return false;
    }
}
