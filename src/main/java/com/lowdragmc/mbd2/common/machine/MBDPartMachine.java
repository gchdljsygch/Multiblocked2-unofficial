package com.lowdragmc.mbd2.common.machine;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.capability.recipe.*;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.machine.IMultiPart;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.ConfigPartSettings;
import com.lowdragmc.mbd2.common.trait.ICapabilityProviderTrait;
import com.lowdragmc.mbd2.common.trait.IProxyAutoIOTrait;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.item.ItemSlotCapabilityTrait;
import com.lowdragmc.mbd2.common.trait.redstone.RedstoneSignalCapabilityTrait;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Part-machine runtime that can join one or more multiblock controllers.
 * <p>
 * A part keeps the controller positions it currently belongs to, exposes its
 * own recipe handlers to formed controllers, and can proxy selected controller
 * capabilities through this block according to {@link ConfigPartSettings}. It
 * also mirrors controller item-trait contents to clients so hidden/proxy parts
 * can render representative inventory items.
 * <p>
 * Thread safety: controller membership and proxied render data are mutated from
 * normal level/block-entity callbacks on the logical server or client thread.
 * The collections are not safe for arbitrary concurrent access.
 */
public class MBDPartMachine extends MBDMachine implements IMultiPart {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MBDPartMachine.class, MBDMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @DescSynced
    @Persisted
    @RequireRerender
    protected final Set<BlockPos> controllerPositions = new HashSet<>();
    @Getter
    @DescSynced
    @RequireRerender
    protected boolean disableRendering = false;
    private final Map<String, List<ItemStack>> proxiedTraitRenderItems = new HashMap<>();
    private CompoundTag lastProxiedTraitRenderItemsTag = new CompoundTag();

    /**
     * Creates a part machine bound to a block entity holder.
     *
     * @param machineHolder block entity holder that owns this part
     * @param definition    part-capable machine definition
     * @param args          optional subclass-specific creation arguments
     */
    public MBDPartMachine(IMachineBlockEntity machineHolder, MBDMachineDefinition definition, Object... args) {
        super(machineHolder, definition, args);
    }

    @Override
    public boolean isPartEnabled() {
        return Optional.ofNullable(getDefinition().partSettings()).map(ConfigPartSettings::isEnable).orElse(false);
    }

    /**
     * Whether it belongs to the specified controller.
     */
    @Override
    public boolean hasController(BlockPos controllerPos) {
        return controllerPositions.contains(controllerPos);
    }

    /**
     * Whether it belongs to a formed Multiblock.
     */
    @Override
    public boolean isFormed() {
        return !controllerPositions.isEmpty();
    }

    /**
     * Get all attached controllers
     */
    @Override
    public List<IMultiController> getControllers() {
        List<IMultiController> result = new ArrayList<>();
        for (var blockPos : controllerPositions) {
            IMultiController.ofController(getLevel(), blockPos).ifPresent(result::add);
        }
        return result;
    }

    /**
     * Get all available traits for recipe logic. It is only used for controller recipe logic.
     * <br>
     * For self recipe logic, use {@link IRecipeCapabilityHolder#getRecipeCapabilitiesProxy()} to get recipe handlers.
     */
    @Override
    public List<IRecipeHandlerTrait<?>> getRecipeHandlers() {
        var handlers = new ArrayList<IRecipeHandlerTrait<?>>();
        for (ITrait additionalTrait : getAdditionalTraits()) {
            handlers.addAll(additionalTrait.getRecipeHandlerTraits());
        }
        return handlers;
    }

    /**
     * on machine invalid in the chunk.
     * <br>
     * You should call it in yourselves {@link BlockEntity#setRemoved()}.
     */
    @Override
    public void onUnload() {
        super.onUnload();
        var level = getLevel();
        for (BlockPos pos : List.copyOf(controllerPositions)) {
            if (level instanceof ServerLevel && level.isLoaded(pos)) {
                IMultiController.ofController(getLevel(), pos).ifPresent(IMultiController::onPartUnload);
            }
        }
        controllerPositions.clear();
        proxiedTraitRenderItems.clear();
        lastProxiedTraitRenderItemsTag = new CompoundTag();
    }

    /**
     * Called when it was added to a multiblock.
     */
    @Override
    public void removedFromController(IMultiController controller) {
        var changed = controllerPositions.remove(controller.getPos());
        checkDisabledRendering();
        if (!isFormed()) {
            setMachineState(getDefinition().stateMachine().getRootState().name());
            proxiedTraitRenderItems.clear();
            syncProxiedTraitRenderItems();
        }
        if (changed) {
            markDirty();
        }
        notifyBlockUpdate();
    }

    @Override
    public void addedToController(IMultiController controller) {
        var changed = controllerPositions.add(controller.getPos());
        checkDisabledRendering();
        if (isFormed()) {
            setMachineState("formed");
        }
        if (changed) {
            markDirty();
        }
        notifyBlockUpdate();
    }

    /**
     * check if there is any controller ask the part to disable rendering.
     */
    public void checkDisabledRendering() {
        var result = false;
        for (var controller : getControllers()) {
            if (controller instanceof MBDMultiblockMachine machine) {
                if (machine.getRenderingDisabledPositions().contains(getPos())) {
                    result = true;
                    break;
                }
            }
        }
        disableRendering = result;
    }

    /**
     * Can it be shared among multi multiblock.
     */
    @Override
    public boolean canShared() {
        return Optional.ofNullable(getDefinition().partSettings()).filter(ConfigPartSettings::isEnable).map(ConfigPartSettings::canShare).orElse(false);
    }

    /**
     * Called when controller recipe logic status changed
     */
    @Override
    public void notifyControllerRecipeStatusChanged(IMultiController controller, RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        IMultiPart.super.notifyControllerRecipeStatusChanged(controller, oldStatus, newStatus);
        if (isFormed()) {
            switch (newStatus) {
                case WORKING -> setMachineState("working");
                case IDLE -> {
                    if (getDefinition().stateMachine().hasState("formed")) {
                        setMachineState("formed");
                    } else {
                        setMachineState(getDefinition().stateMachine().getRootState().name());
                    }
                }
                case WAITING -> setMachineState("waiting");
                case SUSPEND -> setMachineState("suspend");
            }
        } else {
            setMachineState(getDefinition().stateMachine().getRootState().name());
        }
    }

    /**
     * Override it to modify controller recipe on the fly e.g. applying overclock, change chance, etc
     * <br>
     * We will apply part recipe modifiers here. see {@link ConfigPartSettings#recipeModifiers()}.
     *
     * @param recipe                recipe from detected from MBDRecipeType
     * @param controllerRecipeLogic controller recipe logic
     * @return modified recipe.
     * null -- this recipe is unavailable
     */
    @Override
    public MBDRecipe modifyControllerRecipe(@Nonnull MBDRecipe recipe, RecipeLogic controllerRecipeLogic) {
        if (getDefinition().partSettings() != null) {
            return getDefinition().partSettings().recipeModifiers().applyModifiers(controllerRecipeLogic, recipe);
        }
        return recipe;
    }

    @Override
    public ContentModifier getMaxControllerParallel(@NotNull MBDRecipe recipe, RecipeLogic controllerRecipeLogic) {
        if (getDefinition().partSettings() != null) {
            return getDefinition().partSettings().recipeModifiers().getMaxParallel(controllerRecipeLogic, recipe);
        }
        return ContentModifier.IDENTITY;
    }

    @Override
    public boolean alwaysTryModifyControllerRecipe() {
        if (getDefinition().partSettings() != null) {
            return !getDefinition().partSettings().recipeModifiers().recipeModifiers.isEmpty();
        }
        return false;
    }

    /**
     * Checks whether this part proxies the given controller redstone trait on
     * any side.
     *
     * @param redstoneTrait redstone trait attached to a controller that this
     *                      part belongs to
     * @return {@code true} when at least one side exposes input or output for
     * that trait
     */
    public boolean isProxyingControllerRedstone(RedstoneSignalCapabilityTrait redstoneTrait) {
        if (getControllerRedstoneProxyPartSettings(redstoneTrait) == null) {
            return false;
        }
        for (var side : Direction.values()) {
            if (getControllerRedstoneProxyIO(redstoneTrait, side) != IO.NONE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the effective IO permission for proxying a controller redstone
     * trait through this part.
     *
     * @param redstoneTrait controller trait being proxied
     * @param side          world side queried by redstone logic; {@code null} means an
     *                      internal/unsided query
     * @return merged IO permission for all matching proxy rules, or
     * {@link IO#NONE} when the trait is not proxied through this part
     */
    public IO getControllerRedstoneProxyIO(RedstoneSignalCapabilityTrait redstoneTrait, @Nullable Direction side) {
        var partSettings = getControllerRedstoneProxyPartSettings(redstoneTrait);
        if (partSettings == null) {
            return IO.NONE;
        }
        var front = getFrontFacing().orElse(Direction.NORTH);
        var result = IO.NONE;
        for (var proxyControllerCapability : partSettings.proxyControllerCapabilities()) {
            if (proxyControllerCapability.matchesTraitName(redstoneTrait.getDefinition().getName())) {
                result = mergeProxyIO(result, proxyControllerCapability.capabilityIO().getIO(front, side));
                if (result == IO.BOTH) {
                    return result;
                }
            }
        }
        return result;
    }

    @Nullable
    private ConfigPartSettings getControllerRedstoneProxyPartSettings(RedstoneSignalCapabilityTrait redstoneTrait) {
        var partSettings = getDefinition().partSettings();
        if (partSettings == null ||
                redstoneTrait == null ||
                !(redstoneTrait.getMachine() instanceof MBDMultiblockMachine controller) ||
                !hasController(controller.getPos())) {
            return null;
        }
        return partSettings;
    }

    private static IO mergeProxyIO(IO first, IO second) {
        if (first == second) return first;
        if (first == IO.NONE) return second;
        if (second == IO.NONE) return first;
        return IO.BOTH;
    }

    @Override
    public boolean canConnectRedstone(Direction direction) {
        if (super.canConnectRedstone(direction)) {
            return true;
        }
        for (var controller : getControllers()) {
            if (controller instanceof MBDMultiblockMachine proxyController) {
                for (var trait : proxyController.getAdditionalTraits()) {
                    if (trait instanceof RedstoneSignalCapabilityTrait redstoneTrait &&
                            getControllerRedstoneProxyIO(redstoneTrait, direction) != IO.NONE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int getOutputSignal(Direction direction) {
        var signal = super.getOutputSignal(direction);
        for (var controller : getControllers()) {
            if (controller instanceof MBDMultiblockMachine proxyController) {
                for (var trait : proxyController.getAdditionalTraits()) {
                    if (trait instanceof RedstoneSignalCapabilityTrait redstoneTrait &&
                            getControllerRedstoneProxyIO(redstoneTrait, direction).support(IO.OUT)) {
                        signal = Math.max(signal, redstoneTrait.getOutputSignal(direction));
                    }
                }
            }
        }
        return signal;
    }

    /**
     * Runs this part's own server tick and any enabled auto-IO rules that proxy
     * controller traits.
     * <p>
     * Auto-IO is executed from the part position toward the configured sides,
     * then proxied item render contents are synchronized to tracking clients
     * when they change.
     */
    @Override
    public void internalServerTick() {
        super.internalServerTick();
        for (var proxy : Objects.requireNonNull(getDefinition().partSettings()).proxyControllerCapabilities()) {
            if (proxy.autoIO().isEnable()) {
                var front = getFrontFacing().orElse(Direction.NORTH);
                var pos = getPos();
                for (var controller : getControllers()) {
                    if (controller instanceof MBDMultiblockMachine proxyController) {
                        for (var trait : proxyController.getAdditionalTraits()) {
                            if (trait instanceof IProxyAutoIOTrait autoIOTrait && proxy.matchesTraitName(trait.getDefinition().getName())) {
                                for (var side : Direction.values()) {
                                    var io = proxy.autoIO().getIO(front, side);
                                    if (io != IO.NONE) {
                                        autoIOTrait.handleAutoIO(pos, side, io);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        syncProxiedTraitRenderItems();
    }

    /**
     * Returns the item stacks currently mirrored from a proxied controller item
     * trait for client rendering.
     *
     * @param traitName controller trait definition name
     * @return immutable empty list when no mirrored items exist; otherwise the
     * current mirrored stacks for rendering only
     */
    @NotNull
    public List<ItemStack> getProxiedTraitRenderItems(String traitName) {
        return proxiedTraitRenderItems.getOrDefault(traitName, Collections.emptyList());
    }

    private void syncProxiedTraitRenderItems() {
        if (isRemote()) return;
        var tag = serializeProxiedTraitRenderItems();
        if (!tag.equals(lastProxiedTraitRenderItemsTag)) {
            lastProxiedTraitRenderItemsTag = tag.copy();
            applyProxiedTraitRenderItems(tag);
            rpcToTracking("applyProxiedTraitRenderItems", tag);
        }
    }

    private CompoundTag serializeProxiedTraitRenderItems() {
        var tag = new CompoundTag();
        var proxiedTraits = new ListTag();
        var partSettings = getDefinition().partSettings();
        if (partSettings != null) {
            for (var controller : getControllers()) {
                if (controller instanceof MBDMultiblockMachine proxyController) {
                    collectProxiedTraitRenderItems(proxyController, partSettings, proxiedTraits);
                }
            }
        }
        tag.put("Traits", proxiedTraits);
        return tag;
    }

    private void collectProxiedTraitRenderItems(MBDMultiblockMachine proxyController, ConfigPartSettings partSettings, ListTag proxiedTraits) {
        for (var proxyControllerCapability : partSettings.proxyControllerCapabilities()) {
            for (var trait : proxyController.getAdditionalTraits()) {
                if (trait instanceof ItemSlotCapabilityTrait itemTrait && proxyControllerCapability.matchesTraitName(trait.getDefinition().getName())) {
                    var items = new ListTag();
                    for (var slot = 0; slot < itemTrait.storage.getSlots(); slot++) {
                        var stack = itemTrait.storage.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            var itemTag = new CompoundTag();
                            itemTag.putInt("Slot", slot);
                            stack.save(itemTag);
                            itemTag.putInt("CountInt", stack.getCount());
                            items.add(itemTag);
                        }
                    }
                    if (!items.isEmpty()) {
                        var traitTag = new CompoundTag();
                        traitTag.putString("Name", trait.getDefinition().getName());
                        traitTag.put("Items", items);
                        proxiedTraits.add(traitTag);
                    }
                }
            }
        }
    }

    /**
     * RPC entry point used to apply server-collected proxied item render data on
     * clients.
     *
     * @param tag serialized map of trait names to item stacks
     */
    @RPCMethod
    public void applyProxiedTraitRenderItems(CompoundTag tag) {
        proxiedTraitRenderItems.clear();
        var traits = tag.getList("Traits", Tag.TAG_COMPOUND);
        for (var i = 0; i < traits.size(); i++) {
            var traitTag = traits.getCompound(i);
            var items = new ArrayList<ItemStack>();
            var itemTags = traitTag.getList("Items", Tag.TAG_COMPOUND);
            for (var j = 0; j < itemTags.size(); j++) {
                var itemTag = itemTags.getCompound(j);
                var stack = ItemStack.of(itemTag);
                if (!stack.isEmpty() && itemTag.contains("CountInt", Tag.TAG_INT)) {
                    stack.setCount(itemTag.getInt("CountInt"));
                }
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            if (!items.isEmpty()) {
                proxiedTraitRenderItems.computeIfAbsent(traitTag.getString("Name"), ignored -> new ArrayList<>()).addAll(items);
            }
        }
        if (isRemote()) {
            scheduleRenderUpdate();
        }
    }

    /**
     * Returns this part's own capability or a configured proxy of a controller
     * capability.
     * <p>
     * When multiple matching controller contents are found, the controller
     * trait's provider is asked to merge them. The returned capability respects
     * the part's side-relative proxy IO settings.
     */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        var result = super.getCapability(cap, side);
        if (result.isPresent() || Objects.requireNonNull(getDefinition().partSettings())
                .proxyControllerCapabilities().isEmpty()) return result;
        var front = getFrontFacing().orElse(Direction.NORTH);

        for (var controller : getControllers()) {
            if (controller instanceof MBDMultiblockMachine proxyController) {
                List<T> results = new ArrayList<>();
                // get proxy capabilities from controller
                for (var proxyControllerCapability : getDefinition().partSettings().proxyControllerCapabilities()) {
                    var io = proxyControllerCapability.capabilityIO().getIO(front, side);
                    for (var trait : proxyController.getAdditionalTraits()) {
                        if (proxyControllerCapability.matchesTraitName(trait.getDefinition().getName())) {
                            for (var capabilityProviderTrait : trait.getCapabilityProviderTraits()) {
                                if (capabilityProviderTrait.getCapability() == cap) {
                                    results.add((T) capabilityProviderTrait.getCapContent(io));
                                }
                            }
                        }
                    }
                }
                if (results.size() == 1) {
                    return LazyOptional.of(() -> results.get(0));
                } else if (results.size() > 1) {
                    for (var trait : proxyController.getAdditionalTraits()) {
                        for (var capabilityProviderTrait : trait.getCapabilityProviderTraits()) {
                            if (capabilityProviderTrait.getCapability() == cap) {
                                return LazyOptional.of(() -> (T) ((ICapabilityProviderTrait) capabilityProviderTrait).mergeContents(results));
                            }
                        }
                    }
                    return LazyOptional.of(() -> results.get(0));
                }
            }
        }
        return result;
    }

}
