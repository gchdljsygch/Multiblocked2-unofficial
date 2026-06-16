package com.lowdragmc.mbd2.api.recipe;

import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.recipe.*;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.integration.kubejs.recipe.MBDRecipeSchema;
import com.mojang.datafixers.util.Pair;
import dev.latvian.mods.rhino.util.HideFromJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import lombok.Getter;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

/**
 * Runtime recipe definition used by MBD machine logic, viewers, serializers,
 * and KubeJS integration.
 *
 * <p>The business goal is to describe one machine recipe in terms of typed
 * capability inputs, typed capability outputs, optional conditions, duration,
 * priority, fuel behavior, visibility, and recipe-group routing. Matching
 * methods simulate against capability handlers; handling methods may mutate
 * inventories, tanks, energy stores, world-facing traits, and consumption
 * recorders. Public fields reflect the existing mutable recipe model and should
 * be changed on the game/server thread that owns the recipe type.</p>
 */
@SuppressWarnings({"ConstantValue", "rawtypes", "unchecked"})
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@RemapPrefixForJS("kjs$")
public class MBDRecipe implements net.minecraft.world.item.crafting.Recipe<Container> {
    public MBDRecipeType recipeType;
    public final ResourceLocation id;
    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final List<RecipeCondition> conditions;
    public CompoundTag data;
    public int duration;
    public int priority;
    public boolean isFuel;
    public boolean isXEIHidden;
    @Nullable
    public String recipeGroup;
    private Boolean hasTick;

    /**
     * Creates a recipe without an explicit recipe group.
     *
     * @param recipeType  owning MBD recipe type; must be non-null
     * @param id          unique recipe id inside the recipe manager
     * @param inputs      capability input map keyed by capability; lists may contain
     *                    normal and per-tick content
     * @param outputs     capability output map keyed by capability
     * @param conditions  conditions that gate recipe execution
     * @param data        mutable custom NBT payload for recipe-specific metadata
     * @param duration    base processing duration in ticks; negative values are
     *                    preserved here but serializers may write an absolute value
     * @param isFuel      {@code true} when this recipe is used as fuel for another
     *                    recipe flow
     * @param isXEIHidden {@code true} to hide this recipe from recipe viewers
     * @param priority    ordering hint used by recipe lookup; higher priority is
     *                    handled by the recipe type
     */
    public MBDRecipe(MBDRecipeType recipeType, ResourceLocation id, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, List<RecipeCondition> conditions, CompoundTag data, int duration, boolean isFuel, boolean isXEIHidden, int priority) {
        this(recipeType, id, inputs, outputs, conditions, data, duration, isFuel, isXEIHidden, priority, null);
    }

    /**
     * Creates a recipe with optional recipe-group routing.
     *
     * <p>Preconditions: collection and map arguments should be non-null and
     * owned by the caller; this constructor stores references rather than
     * defensive copies. Side effects: validates and normalizes the optional
     * recipe group.</p>
     *
     * @param recipeType  owning MBD recipe type; must be non-null for normal
     *                    runtime use
     * @param id          unique recipe id inside the recipe manager
     * @param inputs      capability input map keyed by capability
     * @param outputs     capability output map keyed by capability
     * @param conditions  conditions that gate recipe execution
     * @param data        custom NBT payload for recipe-specific metadata
     * @param duration    base processing duration in ticks
     * @param isFuel      {@code true} when this is a fuel recipe
     * @param isXEIHidden {@code true} when recipe viewers should hide it
     * @param priority    ordering hint used by recipe lookup
     * @param recipeGroup optional four-character group; {@code null} or empty
     *                    means automatic group matching, malformed values throw
     *                    {@link IllegalArgumentException}
     */
    public MBDRecipe(MBDRecipeType recipeType, ResourceLocation id, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, List<RecipeCondition> conditions, CompoundTag data, int duration, boolean isFuel, boolean isXEIHidden, int priority, @Nullable String recipeGroup) {
        this.recipeType = recipeType;
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.conditions = conditions;
        this.data = data;
        this.duration = duration;
        this.isFuel = isFuel;
        this.isXEIHidden = isXEIHidden;
        this.priority = priority;
        this.recipeGroup = RecipeGroup.normalizeOptional(recipeGroup);
    }

    /**
     * Copies capability content lists for recipe duplication or scaling.
     *
     * <p>Preconditions: {@code contents} must be non-null. Empty lists are
     * omitted from the returned map. Side effects: none except invoking content
     * copy hooks, which may copy mutable capability payloads.</p>
     *
     * @param contents source capability map
     * @param deep     {@code true} to deep-copy capability payloads, {@code false}
     *                 to use the cheaper content copy path
     * @param modifier optional amount/duration modifier applied by content copy
     *                 methods; {@code null} keeps original amounts
     * @return new mutable map containing copied content lists
     */
    public Map<RecipeCapability<?>, List<Content>> copyContents(Map<RecipeCapability<?>, List<Content>> contents, boolean deep, @Nullable ContentModifier modifier) {
        Map<RecipeCapability<?>, List<Content>> copyContents = new HashMap<>();
        for (var entry : contents.entrySet()) {
            var contentList = entry.getValue();
            var cap = entry.getKey();
            if (contentList != null && !contentList.isEmpty()) {
                List<Content> contentsCopy = new ArrayList<>();
                for (Content content : contentList) {
                    if (deep) {
                        contentsCopy.add(content.deepCopy(cap, modifier));
                    } else {
                        contentsCopy.add(content.copy(cap, modifier));
                    }
                }
                copyContents.put(entry.getKey(), contentsCopy);
            }
        }
        return copyContents;
    }

    /**
     * Creates a shallow content copy with a new id.
     *
     * @param id id assigned to the copied recipe
     * @return recipe copy that shares condition list and custom data reference
     * but copies input/output content wrappers
     */
    public MBDRecipe copy(ResourceLocation id) {
        return new MBDRecipe(recipeType, id, copyContents(inputs, false, null), copyContents(outputs, false, null), conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
    }

    /**
     * Creates a deep content copy with a new id.
     *
     * @param id id assigned to the copied recipe
     * @return recipe copy with deep-copied input/output content payloads
     */
    public MBDRecipe deepCopied(ResourceLocation id) {
        return new MBDRecipe(recipeType, id, copyContents(inputs, true, null), copyContents(outputs, true, null), conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
    }

    /**
     * Creates a shallow content copy that keeps this recipe id.
     *
     * @return recipe copy suitable for runtime modification without changing the
     * recipe manager key
     */
    public MBDRecipe copy() {
        return copy(id);
    }

    /**
     * Creates a modified shallow copy and applies the modifier to content and
     * duration.
     *
     * @param modifier amount modifier used by content copy and duration scaling
     * @return modified recipe copy
     */
    public MBDRecipe copy(ContentModifier modifier) {
        return copy(modifier, true);
    }

    /**
     * Creates a modified shallow copy for both input and output content.
     *
     * @param modifier       amount modifier used by copied content
     * @param modifyDuration {@code true} to apply the modifier to duration
     * @return modified recipe copy
     */
    public MBDRecipe copy(ContentModifier modifier, boolean modifyDuration) {
        return copy(modifier, modifyDuration, IO.BOTH);
    }

    /**
     * Creates a modified shallow copy for selected IO content.
     *
     * <p>Business goal: scale recipes for parallel processing without changing
     * unrelated content sides. Side effects: invokes the modifier for copied
     * contents and, optionally, for duration.</p>
     *
     * @param modifier       amount modifier used by copied content
     * @param modifyDuration {@code true} to scale duration through the modifier
     * @param io             side to modify; {@link IO#IN}, {@link IO#OUT}, or
     *                       {@link IO#BOTH}. Other values leave both maps shared as-is.
     * @return recipe copy with selected content maps copied and modified
     */
    public MBDRecipe copy(ContentModifier modifier, boolean modifyDuration, IO io) {
        var copied = new MBDRecipe(recipeType, id,
                (io == IO.BOTH || io == IO.IN) ? copyContents(inputs, false, modifier) : inputs,
                (io == IO.BOTH || io == IO.OUT) ? copyContents(outputs, false, modifier) : outputs,
                conditions, data, duration, isFuel, isXEIHidden, priority, recipeGroup);
        if (modifyDuration) {
            copied.duration = modifier.apply(this.duration).intValue();
        }
        return copied;
    }

    /**
     * Converts this runtime recipe back to the Java builder API.
     *
     * <p>Preconditions: the recipe type must provide a builder and recipe data
     * must be non-null. Side effects: none; builder state is populated from deep
     * copies of mutable content, conditions, and data.</p>
     *
     * @return builder initialized to recreate this recipe
     */
    @HideFromJS
    public MBDRecipeBuilder toBuilder() {
        var builder = recipeType.getRecipeBuilder()
                .id(id)
                .duration(duration)
                .isFuel(isFuel)
                .isXEIHidden(isXEIHidden)
                .priority(priority);
        builder.data = data.copy();
        builder.input.putAll(copyContents(inputs, true, null));
        builder.output.putAll(copyContents(outputs, true, null));
        for (RecipeCondition condition : conditions) {
            builder.addCondition(condition.copy());
        }
        return builder;
    }

    /**
     * Converts this recipe to the KubeJS builder representation.
     *
     * <p>Preconditions: KubeJS must be loaded. Side effects: none.</p>
     *
     * @return KubeJS recipe builder object populated from this recipe
     * @throws UnsupportedOperationException when KubeJS is not loaded
     */
    public Object kjs$toBuilder() {
        if (LDLib.isKubejsLoaded()) {
            var builder = new MBDRecipeSchema.MBDRecipeJS(recipeType);
            builder.id(id);
            builder.duration(duration);
            builder.isFuel(isFuel);
            builder.priority(priority);
            builder.data = data.copy();
            builder.inputs.putAll(copyContents(inputs, true, null));
            builder.outputs.putAll(copyContents(outputs, true, null));
            for (RecipeCondition condition : conditions) {
                builder.addCondition(condition.copy());
            }
            return builder;
        }
        throw new UnsupportedOperationException("KubeJS is not loaded");
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MBDRecipeSerializer.SERIALIZER;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return recipeType;
    }

    @Override
    public boolean matches(@NotNull Container pContainer, @NotNull Level pLevel) {
        return false;
    }

    @Override
    public ItemStack assemble(Container inventory, RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    ///////////////////////////////////////////////////////////////
    // **********************Internal Logic********************* //
    ///////////////////////////////////////////////////////////////

    /**
     * Returns input content for one capability.
     *
     * @param capability capability key to query
     * @return configured input content list, or an immutable empty list when the
     * capability is absent
     */
    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, Collections.emptyList());
    }

    /**
     * Returns output content for one capability.
     *
     * @param capability capability key to query
     * @return configured output content list, or an immutable empty list when the
     * capability is absent
     */
    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, Collections.emptyList());
    }

    /**
     * Flattens all input content lists.
     *
     * @return new list containing every configured input content wrapper
     */
    public List<Content> getInputContents() {
        return flattenContents(inputs);
    }

    /**
     * Flattens all output content lists.
     *
     * @return new list containing every configured output content wrapper
     */
    public List<Content> getOutputContents() {
        return flattenContents(outputs);
    }

    /**
     * Flattens raw input payloads across all capabilities.
     *
     * @return new list of raw content objects
     */
    public List<Object> getInputList() {
        return getContentList(getInputContents());
    }

    /**
     * Flattens raw output payloads across all capabilities.
     *
     * @return new list of raw content objects
     */
    public List<Object> getOutputList() {
        return getContentList(getOutputContents());
    }

    /**
     * Returns raw input payloads for one typed capability.
     *
     * @param capability capability whose payload type defines {@code T}
     * @param <T>        raw payload type handled by the capability
     * @return new list of raw input payloads
     */
    public <T> List<T> getInputList(RecipeCapability<T> capability) {
        return getContentList(getInputContents(capability));
    }

    /**
     * Returns raw output payloads for one typed capability.
     *
     * @param capability capability whose payload type defines {@code T}
     * @param <T>        raw payload type handled by the capability
     * @return new list of raw output payloads
     */
    public <T> List<T> getOutputList(RecipeCapability<T> capability) {
        return getContentList(getOutputContents(capability));
    }

    private static List<Content> flattenContents(Map<RecipeCapability<?>, List<Content>> contents) {
        return contents.values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getContentList(List<Content> contents) {
        return contents.stream()
                .map(content -> (T) content.getContent())
                .toList();
    }

    /**
     * Simulates whether this recipe's non-per-tick inputs and outputs can be
     * handled by a capability holder.
     *
     * <p>Business goal: decide whether a machine may start a recipe without
     * consuming resources. Preconditions: {@code holder} must expose its current
     * recipe capability proxies on the calling game thread. Side effects: none
     * beyond invoking handler simulation methods.</p>
     *
     * @param holder machine or trait aggregate to test against
     * @return success when all normal inputs can be supplied and all normal
     * outputs can be accepted; failure includes a lazy display reason when
     * available
     */
    public ActionResult matchRecipe(IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies()) return ActionResult.FAIL_NO_REASON;
        var result = matchRecipe(false, IO.IN, holder, inputs, false);
        if (!result.isSuccess()) return result;
        result = matchRecipe(false, IO.OUT, holder, outputs, false);
        if (!result.isSuccess()) return result;
        return ActionResult.SUCCESS;
    }

    /**
     * Simulates whether this recipe's per-tick content can be handled.
     *
     * <p>If the recipe has no per-tick content this returns success without
     * querying handlers. Side effects are limited to handler simulation.</p>
     *
     * @param holder machine or trait aggregate to test against
     * @return success when every per-tick input/output can be handled
     */
    public ActionResult matchTickRecipe(IRecipeCapabilityHolder holder) {
        if (hasTick()) {
            if (!holder.hasProxies()) return ActionResult.FAIL_NO_REASON;
            var result = matchRecipe(true, IO.IN, holder, inputs, false);
            if (!result.isSuccess()) return result;
            result = matchRecipe(true, IO.OUT, holder, outputs, false);
            if (!result.isSuccess()) return result;
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Simulates one recipe side against holder capabilities.
     *
     * <p>Preconditions: {@code io} should be {@link IO#IN} or {@link IO#OUT};
     * {@link IO#BOTH} and {@link IO#NONE} cannot produce a meaningful side
     * match and fail when content remains. Input matching tries compatible
     * recipe groups before returning failure. Side effects: none beyond handler
     * simulation.</p>
     *
     * @param perTick                {@code true} to match only per-tick content,
     *                               {@code false} to match startup/finish content
     * @param io                     side being matched
     * @param holder                 capability holder supplying handlers
     * @param contents               capability content map for the selected side
     * @param calculateExpectingRate reserved flag for future failure-rate
     *                               calculation; currently does not change the result
     * @return success when all matching content can be handled
     */
    public ActionResult matchRecipe(boolean perTick, IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents, boolean calculateExpectingRate) {
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies = holder.getRecipeCapabilitiesProxy();
        if (io == IO.IN) {
            ActionResult result = ActionResult.FAIL_NO_REASON;
            for (var recipeGroupContext : getInputRecipeGroupContexts(capabilityProxies, contents)) {
                result = matchRecipe(perTick, io, capabilityProxies, contents, calculateExpectingRate, recipeGroupContext);
                if (result.isSuccess()) {
                    return result;
                }
            }
            return result;
        }
        return matchRecipe(perTick, io, capabilityProxies, contents, calculateExpectingRate, null);
    }

    private ActionResult matchRecipe(boolean perTick, IO io, Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
                                     Map<RecipeCapability<?>, List<Content>> contents, boolean calculateExpectingRate,
                                     @Nullable RecipeGroupMatchContext recipeGroupContext) {
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            Set<IRecipeHandler<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            Map<String, List> contentSlot = new HashMap<>();
            for (Content cont : entry.getValue()) {
                if (cont.perTick != perTick) continue;
                if (cont.slotName.isEmpty()) {
                    content.add(cont.content);
                } else {
                    contentSlot.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                }
            }
            RecipeCapability<?> capability = entry.getKey();
            content = content.stream().map(capability::copyContent).toList();
            if (content.isEmpty() && contentSlot.isEmpty()) continue;
            if (content.isEmpty()) content = null;

            var result = handlerContentsInternal(io, io, capabilityProxies, capability, used, content, contentSlot, content, contentSlot, true, recipeGroupContext);
            if (result.getA() == null && result.getB().isEmpty()) continue;
            result = handlerContentsInternal(IO.BOTH, io, capabilityProxies, capability, used, result.getA(), result.getB(), content, contentSlot, true, recipeGroupContext);

            if (result.getA() != null || !result.getB().isEmpty()) {
                var expectingRate = 0f;
                // TODO calculateExpectingRate
//                if (calculateExpectingRate) {
//                    if (result.getA() != null) {
//                        expectingRate = Math.max(capability.calculateAmount(result.getA()), expectingRate);
//                    }
//                    if (!result.getB().isEmpty()) {
//                        for (var c : result.getB().values()) {
//                            expectingRate = Math.max(capability.calculateAmount(c), expectingRate);
//                        }
//                    }
//                }
                if (io == IO.NONE || io == IO.BOTH) return ActionResult.FAIL_NO_REASON;
                var finalResult = result;
                return ActionResult.fail(() -> {
                    var reason = Component.translatable(io == IO.IN ? "mbd2.recipe_logic.insufficient_in" : "mbd2.recipe_logic.insufficient_out");
                    if (perTick) {
                        reason.append("/t : ");
                    } else {
                        reason.append(": ");
                    }
                    reason.append(capability.getTraslateComponent());
                    if (finalResult.getA() != null) {
                        reason.append("| miss: ");
                        reason.append(capability.getLeftErrorInfo(finalResult.getA()));
                    }
                    if (!finalResult.getB().isEmpty()) {
                        for (var tuple : finalResult.getB().entrySet()) {
                            reason.append("| slot (%s) miss: ".formatted(tuple.getKey()));
                            reason.append(capability.getLeftErrorInfo(tuple.getValue()));
                        }
                    }
                    return reason;
                }, expectingRate);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Applies per-tick input or output handling for this recipe.
     *
     * <p>Preconditions: {@code io} must be {@link IO#IN} or {@link IO#OUT}; a
     * holder without proxies cannot handle content. Side effects: capability
     * handlers may consume inputs, insert outputs, drain energy, or mutate
     * trait state.</p>
     *
     * @param io     side to handle
     * @param holder machine or trait aggregate owning handlers
     * @return {@code true} when all per-tick content for the side was handled
     */
    public boolean handleTickRecipeIO(IO io, IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies() || io == IO.BOTH) return false;
        return handleRecipe(true, io, holder, io == IO.IN ? inputs : outputs);
    }

    /**
     * Applies non-per-tick input or output handling for this recipe.
     *
     * <p>Business goal: perform the real resource transfer after a previous
     * successful match. Preconditions and side effects match
     * {@link #handleTickRecipeIO(IO, IRecipeCapabilityHolder)}.</p>
     *
     * @param io     side to handle
     * @param holder machine or trait aggregate owning handlers
     * @return {@code true} when all normal content for the side was handled
     */
    public boolean handleRecipeIO(IO io, IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies() || io == IO.BOTH) return false;
        return handleRecipe(false, io, holder, io == IO.IN ? inputs : outputs);
    }

    /**
     * Applies non-per-tick IO and captures concrete inputs consumed by handlers.
     *
     * <p>Preconditions: intended for {@link IO#IN} or {@link IO#OUT}. Side
     * effects: same mutations as {@link #handleRecipeIO(IO,
     * IRecipeCapabilityHolder)} plus a temporary thread-local consumption
     * recorder. The recorder is closed before this method returns.</p>
     *
     * @param io     side to handle
     * @param holder machine or trait aggregate owning handlers
     * @return handling success flag and immutable consumption snapshot; invalid
     * IO or missing proxies return failure with {@link RecipeConsumption#EMPTY}
     */
    public HandleResult handleRecipeIOWithResult(IO io, IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies() || io == IO.BOTH) {
            return new HandleResult(false, RecipeConsumption.EMPTY);
        }
        try (var recorder = RecipeConsumptionTracker.start()) {
            var success = handleRecipe(false, io, holder, io == IO.IN ? inputs : outputs);
            return new HandleResult(success, recorder.snapshot());
        }
    }

    /**
     * Applies selected content to matching capability handlers.
     *
     * <p>Input handling first finds a compatible recipe group by simulation,
     * then executes against that same group. Output handling executes without a
     * recipe group context. Side effects are the actual handler mutations for
     * the selected content.</p>
     *
     * @param perTick  {@code true} to handle per-tick content only
     * @param io       side to handle; should be {@link IO#IN} or {@link IO#OUT}
     * @param holder   capability holder supplying handlers
     * @param contents capability content map for the selected side
     * @return {@code true} when all selected content was handled
     */
    public boolean handleRecipe(boolean perTick, IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents) {
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies = holder.getRecipeCapabilitiesProxy();
        if (io == IO.IN) {
            for (var recipeGroupContext : getInputRecipeGroupContexts(capabilityProxies, contents)) {
                if (matchRecipe(perTick, io, capabilityProxies, contents, false, recipeGroupContext).isSuccess()) {
                    return handleRecipe(perTick, io, holder, capabilityProxies, contents, recipeGroupContext);
                }
            }
            return false;
        }
        return handleRecipe(perTick, io, holder, capabilityProxies, contents, null);
    }

    private boolean handleRecipe(boolean perTick, IO io, IRecipeCapabilityHolder holder,
                                 Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
                                 Map<RecipeCapability<?>, List<Content>> contents, @Nullable RecipeGroupMatchContext recipeGroupContext) {
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            Set<IRecipeHandler<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            Map<String, List> contentSlot = new HashMap<>();
            List contentSearch = new ArrayList<>();
            Map<String, List> contentSlotSearch = new HashMap<>();
            for (Content cont : entry.getValue()) {
                if (cont.perTick != perTick) continue;
                if (cont.slotName.isEmpty()) {
                    contentSearch.add(cont.content);
                } else {
                    contentSlotSearch.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                }
                if (cont.chance >= 1 || MBD2.RND.nextFloat() < (cont.chance + holder.getChanceTier() * cont.tierChanceBoost)) { // chance input
                    if (cont.slotName.isEmpty()) {
                        content.add(cont.content);
                    } else {
                        contentSlot.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                    }
                }
            }
            RecipeCapability<?> capability = entry.getKey();
            content = content.stream().map(capability::copyContent).toList();
            if (content.isEmpty() && contentSlot.isEmpty()) continue;
            if (content.isEmpty()) content = null;

            var result = handlerContentsInternal(io, io, capabilityProxies, capability, used, content, contentSlot, contentSearch, contentSlotSearch, false, recipeGroupContext);
            if (result.getA() == null && result.getB().isEmpty()) continue;
            result = handlerContentsInternal(IO.BOTH, io, capabilityProxies, capability, used, result.getA(), result.getB(), contentSearch, contentSlotSearch, false, recipeGroupContext);

            if (result.getA() != null || !result.getB().isEmpty()) {
                MBD2.LOGGER.warn("io error while handling a recipe {} outputs. holder: {}", id, holder);
                return false;
            }
        }
        return true;
    }

    private Tuple<List, Map<String, List>> handlerContentsInternal(
            IO capIO, IO io, Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
            RecipeCapability<?> capability, Set<IRecipeHandler<?>> used,
            List content, Map<String, List> contentSlot,
            List contentSearch, Map<String, List> contentSlotSearch,
            boolean simulate, @Nullable RecipeGroupMatchContext recipeGroupContext) {
        if (capabilityProxies.contains(capIO, capability)) {
            var handlers = capabilityProxies.get(capIO, capability);
            // handle distinct first
            for (IRecipeHandler<?> handler : handlers) {
                if (recipeGroupContext != null && !recipeGroupContext.isCompatible(handler)) continue;
                if (!handler.isDistinct()) continue;
                var slotNames = handler.getSlotNames();
                var result = handleRecipeWithGroup(handler, io, contentSearch, null, true, recipeGroupContext);
                if (result == null) {
                    // check distinct slot handler
                    if (slotNames.containsAll(contentSlotSearch.keySet())) {
                        boolean success = true;
                        for (var entry : contentSlotSearch.entrySet()) {
                            List<?> left = handleRecipeWithGroup(handler, io, entry.getValue(), entry.getKey(), true, recipeGroupContext);
                            if (left != null) {
                                success = false;
                                break;
                            }
                        }
                        if (success) {
                            if (!simulate) {
                                for (var entry : contentSlot.entrySet()) {
                                    handleRecipeWithGroup(handler, io, entry.getValue(), entry.getKey(), false, recipeGroupContext);
                                }
                            }
                            contentSlot.clear();
                        }
                    }
                    if (contentSlot.isEmpty()) {
                        if (!simulate) {
                            handleRecipeWithGroup(handler, io, content, null, false, recipeGroupContext);
                        }
                        content = null;
                    }
                }
                if (content == null && contentSlot.isEmpty()) {
                    break;
                }
            }
            if (content != null || !contentSlot.isEmpty()) {
                // handle undistinct later
                for (IRecipeHandler<?> proxy : handlers) {
                    if (recipeGroupContext != null && !recipeGroupContext.isCompatible(proxy)) continue;
                    if (used.contains(proxy) || proxy.isDistinct()) continue;
                    used.add(proxy);
                    if (content != null) {
                        content = handleRecipeWithGroup(proxy, io, content, null, simulate, recipeGroupContext);
                    }
                    var slotNames = proxy.getSlotNames();
                    if (!slotNames.isEmpty()) {
                        Iterator<String> iterator = contentSlot.keySet().iterator();
                        while (iterator.hasNext()) {
                            String key = iterator.next();
                            if (slotNames.contains(key)) {
                                List<?> left = handleRecipeWithGroup(proxy, io, contentSlot.get(key), key, simulate, recipeGroupContext);
                                if (left == null) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                    if (content == null && contentSlot.isEmpty()) break;
                }
            }
        }
        return new Tuple<>(content, contentSlot);
    }

    private List handleRecipeWithGroup(IRecipeHandler<?> handler, IO io, List<?> left, @Nullable String slotName,
                                       boolean simulate, @Nullable RecipeGroupMatchContext recipeGroupContext) {
        return handler.handleRecipe(io, this, left, slotName, simulate, recipeGroupContext == null ? null : recipeGroupContext.recipeGroup);
    }

    private List<RecipeGroupMatchContext> getInputRecipeGroupContexts(Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
                                                                      Map<RecipeCapability<?>, List<Content>> contents) {
        var normalizedRecipeGroup = RecipeGroup.normalizeOptional(recipeGroup);
        if (normalizedRecipeGroup != null) {
            return List.of(new RecipeGroupMatchContext(normalizedRecipeGroup));
        }

        Set<String> groups = new LinkedHashSet<>();
        for (RecipeCapability<?> capability : contents.keySet()) {
            collectRecipeGroups(groups, capabilityProxies, IO.IN, capability);
            collectRecipeGroups(groups, capabilityProxies, IO.BOTH, capability);
        }
        if (groups.isEmpty()) {
            groups.add(RecipeGroup.DEFAULT);
        }
        return groups.stream().map(RecipeGroupMatchContext::new).toList();
    }

    private void collectRecipeGroups(Set<String> groups, Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
                                     IO io, RecipeCapability<?> capability) {
        if (!capabilityProxies.contains(io, capability)) return;
        for (IRecipeHandler<?> handler : capabilityProxies.get(io, capability)) {
            for (var handlerGroup : handler.getRecipeGroups()) {
                handlerGroup = RecipeGroup.normalizeOrDefault(handlerGroup);
                if (!RecipeGroup.ANY.equals(handlerGroup)) {
                    groups.add(handlerGroup);
                }
            }
        }
    }

    private static class RecipeGroupMatchContext {
        private final String recipeGroup;

        private RecipeGroupMatchContext(String recipeGroup) {
            this.recipeGroup = RecipeGroup.normalize(recipeGroup);
        }

        private boolean isCompatible(IRecipeHandler<?> handler) {
            for (var handlerGroup : handler.getRecipeGroups()) {
                if (RecipeGroup.matches(recipeGroup, handlerGroup)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks whether the recipe contains per-tick input or output content.
     *
     * <p>The result is cached after the first scan. Side effects: initializes the
     * private cache field.</p>
     *
     * @return {@code true} when any input or output content has
     * {@code perTick == true}
     */
    public boolean hasTick() {
        if (hasTick == null) {
            for (List<Content> contents : inputs.values()) {
                for (Content content : contents) {
                    if (content.perTick) {
                        hasTick = true;
                        return true;
                    }
                }
            }
            for (List<Content> contents : outputs.values()) {
                for (Content content : contents) {
                    if (content.perTick) {
                        hasTick = true;
                        return true;
                    }
                }
            }
            hasTick = false;
        }
        return hasTick;
    }

    /**
     * Notifies relevant handlers before recipe work begins.
     *
     * <p>Business goal: let capabilities allocate transient state or prepare UI
     * before a recipe starts. Side effects are defined by each handler's
     * {@code preWorking} implementation.</p>
     *
     * @param holder capability holder whose handlers receive callbacks
     */
    public void preWorking(IRecipeCapabilityHolder holder) {
        handlePre(inputs, holder, IO.IN);
        handlePre(outputs, holder, IO.OUT);
    }

    /**
     * Notifies relevant handlers after recipe work finishes or stops.
     *
     * @param holder capability holder whose handlers receive callbacks
     */
    public void postWorking(IRecipeCapabilityHolder holder) {
        handlePost(inputs, holder, IO.IN);
        handlePost(outputs, holder, IO.OUT);
    }

    /**
     * Dispatches pre-work callbacks for handlers referenced by a content map.
     *
     * @param contents input or output content map used to select capabilities
     * @param holder   capability holder supplying handlers
     * @param io       side whose handlers should be notified
     */
    public void handlePre(Map<RecipeCapability<?>, List<Content>> contents, IRecipeCapabilityHolder holder, IO io) {
        contents.forEach(((capability, tuples) -> {
            if (holder.getRecipeCapabilitiesProxy().contains(io, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getRecipeCapabilitiesProxy().get(io, capability)) {
                    capabilityProxy.preWorking(holder, io, this);
                }
            } else if (holder.getRecipeCapabilitiesProxy().contains(IO.BOTH, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getRecipeCapabilitiesProxy().get(IO.BOTH, capability)) {
                    capabilityProxy.preWorking(holder, io, this);
                }
            }
        }));
    }

    /**
     * Dispatches post-work callbacks for handlers referenced by a content map.
     *
     * @param contents input or output content map used to select capabilities
     * @param holder   capability holder supplying handlers
     * @param io       side whose handlers should be notified
     */
    public void handlePost(Map<RecipeCapability<?>, List<Content>> contents, IRecipeCapabilityHolder holder, IO io) {
        contents.forEach(((capability, tuples) -> {
            if (holder.getRecipeCapabilitiesProxy().contains(io, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getRecipeCapabilitiesProxy().get(io, capability)) {
                    capabilityProxy.postWorking(holder, io, this);
                }
            } else if (holder.getRecipeCapabilitiesProxy().contains(IO.BOTH, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getRecipeCapabilitiesProxy().get(IO.BOTH, capability)) {
                    capabilityProxy.postWorking(holder, io, this);
                }
            }
        }));
    }

    /**
     * Evaluates every configured recipe condition against a running machine.
     *
     * <p>Business rule: non-OR conditions fail immediately when their test
     * result equals their reverse flag; OR conditions are grouped by type and a
     * group fails only when every condition in that group is effectively false.
     * Side effects are whatever the condition implementations perform.</p>
     *
     * @param recipeLogic machine recipe logic used by conditions
     * @return success when all condition groups pass; otherwise a failure result
     * with a localized reason supplier
     */
    public ActionResult checkConditions(@Nonnull RecipeLogic recipeLogic) {
        if (conditions.isEmpty()) return ActionResult.SUCCESS;
        Map<String, List<RecipeCondition>> or = new HashMap<>();
        for (RecipeCondition condition : conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getType(), type -> new ArrayList<>()).add(condition);
            } else if (condition.test(this, recipeLogic) == condition.isReverse()) {
                return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.condition_fails").append(": ").append(condition.getTooltips()));
            }
        }
        for (List<RecipeCondition> conditions : or.values()) {
            if (conditions.stream().allMatch(condition -> condition.test(this, recipeLogic) == condition.isReverse())) {
                return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.condition_fails"));
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Result of matching or condition evaluation.
     *
     * @param isSuccess     {@code true} when the action passed
     * @param reason        optional lazy failure message; {@code null} means no display
     *                      reason is available
     * @param expectingRate optional multiplier estimate for missing resources;
     *                      {@code 0} when not calculated
     */
    public static record ActionResult(boolean isSuccess, @Nullable Supplier<Component> reason, float expectingRate) {

        public final static ActionResult SUCCESS = new ActionResult(true, null, 0);
        public final static ActionResult FAIL_NO_REASON = new ActionResult(true, null, 0);

        /**
         * Creates a failure result without a resource expectation estimate.
         *
         * @param component lazy failure message supplier, or {@code null}
         * @return failed action result
         */
        public static ActionResult fail(@Nullable Supplier<Component> component) {
            return new ActionResult(false, component, 0);
        }

        /**
         * Creates a failure result with a resource expectation estimate.
         *
         * @param component     lazy failure message supplier, or {@code null}
         * @param expectingRate non-negative estimate used by callers that can
         *                      scale a recipe toward available resources
         * @return failed action result
         */
        public static ActionResult fail(@Nullable Supplier<Component> component, float expectingRate) {
            return new ActionResult(false, component, expectingRate);
        }
    }

    /**
     * Result of applying recipe IO while recording concrete consumption.
     *
     * @param success     {@code true} when all selected content was handled
     * @param consumption immutable snapshot of content consumed during handling
     */
    public static record HandleResult(boolean success, RecipeConsumption consumption) {
    }

    /**
     * Finds the highest automatically parallelized recipe that still matches.
     *
     * <p>Business goal: scale recipe content up to the largest valid parallel
     * amount within a configured limit. The search simulates recipe and tick
     * matching only; it does not consume resources. Recipes whose inputs are all
     * marked non-scaling are returned unchanged.</p>
     *
     * @param recipeCapabilityHolder holder used to simulate available resources
     * @param recipe                 source recipe to scale
     * @param maxParallel            upper bound for parallel amount; values less than or
     *                               equal to {@code 1} return the original recipe with amount {@code 1}
     * @param modifyDuration         {@code true} to scale duration with the same
     *                               modifier used for content
     * @return pair of scaled recipe and chosen parallel amount in
     * {@code [1, maxParallel]}
     */
    public static Pair<MBDRecipe, Integer> accurateParallel(IRecipeCapabilityHolder recipeCapabilityHolder, @Nonnull MBDRecipe recipe, int maxParallel, boolean modifyDuration) {
        if (maxParallel == 1 || recipe.hasOnlyNonScalingInputsForAutomaticParallel()) {
            return Pair.of(recipe, 1);
        }
        var parallel = tryParallel(recipeCapabilityHolder, recipe, 1, maxParallel, modifyDuration);
        return parallel == null ? Pair.of(recipe, 1) : parallel;
    }

    private boolean hasOnlyNonScalingInputsForAutomaticParallel() {
        boolean hasNonScalingInput = false;
        for (var entry : inputs.entrySet()) {
            var contents = entry.getValue();
            if (contents == null || contents.isEmpty()) continue;
            if (entry.getKey().scalesForAutomaticParallel()) {
                return false;
            }
            hasNonScalingInput = true;
        }
        return hasNonScalingInput;
    }

    @Nullable
    private static Pair<MBDRecipe, Integer> tryParallel(IRecipeCapabilityHolder recipeCapabilityHolder, MBDRecipe original, int min, int max, boolean modifyDuration) {
        if (min > max) return null;

        int mid = (min + max) / 2;

        var copied = original.copy(ContentModifier.multiplier(mid), modifyDuration);
        if (!copied.matchRecipe(recipeCapabilityHolder).isSuccess() || !copied.matchTickRecipe(recipeCapabilityHolder).isSuccess()) {
            // tried too many
            return tryParallel(recipeCapabilityHolder, original, min, mid - 1, modifyDuration);
        } else {
            // at max parallels
            if (mid == max) {
                return Pair.of(copied, mid);
            }
            // matches, but try to do more
            var tryMore = tryParallel(recipeCapabilityHolder, original, mid + 1, max, modifyDuration);
            return tryMore != null ? tryMore : Pair.of(copied, mid);
        }
    }

}
