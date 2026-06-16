package com.lowdragmc.mbd2.api.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.mbd2.api.recipe.ingredient.SizedIngredient;
import com.lowdragmc.mbd2.common.capability.recipe.FluidRecipeCapability;
import com.lowdragmc.mbd2.common.capability.recipe.ItemRecipeCapability;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignal;
import com.lowdragmc.mbd2.common.capability.recipe.RedstoneSignalRecipeCapability;
import com.lowdragmc.mbd2.common.recipe.*;
import com.lowdragmc.mbd2.utils.TagUtil;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mutable fluent builder for MBD recipes, datagen recipes, and builtin recipe
 * registration.
 *
 * <p>The business goal is to collect capability inputs, outputs, conditions,
 * custom NBT data, viewer flags, and timing into a single {@link MBDRecipe}.
 * Builder methods mutate this instance and return it for chaining. Instances
 * are not thread-safe; create and save them on the datagen, reload, or script
 * thread that owns the current recipe-building operation.</p>
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Accessors(chain = true, fluent = true)
public class MBDRecipeBuilder {
    public final Map<RecipeCapability<?>, List<Content>> input = new HashMap<>();
    public final Map<RecipeCapability<?>, List<Content>> output = new HashMap<>();
    public CompoundTag data = new CompoundTag();
    public final List<RecipeCondition> conditions = new ArrayList<>();
    @Setter
    public ResourceLocation id;
    @Setter
    public MBDRecipeType recipeType;
    @Setter
    public int duration = 100;
    @Setter
    public boolean perTick;
    @Setter
    public String slotName;
    @Setter
    public String uiName;
    @Setter
    public float chance = 1;
    @Setter
    public float tierChanceBoost = 0;
    @Setter
    public boolean isFuel = false;
    @Setter
    public boolean isXEIHidden = false;
    @Setter
    public int priority = 0;
    @Setter
    public BiConsumer<MBDRecipeBuilder, Consumer<FinishedRecipe>> onSave;

    /**
     * Creates an empty builder for a recipe type.
     *
     * @param id recipe id used for raw and builtin recipes; datagen output
     * prefixes it with the recipe type path
     * @param recipeType owning type; may be {@code null} only for raw temporary
     * builders that will be assigned a type before normal build/save operations
     */
    public MBDRecipeBuilder(ResourceLocation id, MBDRecipeType recipeType) {
        this.id = id;
        this.recipeType = recipeType;
    }

    /**
     * Creates a builder initialized from an existing recipe.
     *
     * <p>Side effects: none on the source recipe; capability lists and NBT data
     * are copied into mutable builder state.</p>
     *
     * @param toCopy recipe whose content should seed this builder
     * @param recipeType type to assign to the new builder
     */
    public MBDRecipeBuilder(MBDRecipe toCopy, MBDRecipeType recipeType) {
        this.id = toCopy.id;
        this.recipeType = recipeType;
        toCopy.inputs.forEach((k, v) -> this.input.put(k, new ArrayList<>(v)));
        toCopy.outputs.forEach((k, v) -> this.output.put(k, new ArrayList<>(v)));
        this.conditions.addAll(toCopy.conditions);
        this.data = toCopy.data.copy();
        this.duration = toCopy.duration;
        this.isFuel = toCopy.isFuel;
        this.isXEIHidden = toCopy.isXEIHidden;
    }

    /**
     * Creates an empty builder for the supplied recipe type.
     *
     * @param id recipe id
     * @param recipeType owning recipe type
     * @return new mutable builder
     */
    public static MBDRecipeBuilder of(ResourceLocation id, MBDRecipeType recipeType) {
        return new MBDRecipeBuilder(id, recipeType);
    }

    /**
     * Creates a raw temporary builder without an owning recipe type.
     *
     * @return mutable builder that should receive a recipe type before save or
     * normal runtime use
     */
    public static MBDRecipeBuilder ofRaw() {
        return new MBDRecipeBuilder(MBD2.id("raw"), null);
    }

    /**
     * Copies this builder using an id under the MBD namespace helper.
     *
     * @param id path passed through {@link MBD2#id(String)}
     * @return independent mutable copy
     */
    public MBDRecipeBuilder copy(String id) {
        return copy(MBD2.id(id));
    }

    /**
     * Copies this builder under a new id.
     *
     * <p>Side effects: none on this builder. The returned builder receives
     * copied content lists, conditions, data, timing, chance settings, UI names,
     * and save callback.</p>
     *
     * @param id id for the returned builder
     * @return independent mutable copy
     */
    public MBDRecipeBuilder copy(ResourceLocation id) {
        MBDRecipeBuilder copy = new MBDRecipeBuilder(id, this.recipeType);
        this.input.forEach((k, v) -> copy.input.put(k, new ArrayList<>(v)));
        this.output.forEach((k, v) -> copy.output.put(k, new ArrayList<>(v)));
        copy.conditions.addAll(this.conditions);
        copy.data = this.data.copy();
        copy.duration = this.duration;
        copy.chance = this.chance;
        copy.perTick = this.perTick;
        copy.isFuel = this.isFuel;
        copy.uiName = this.uiName;
        copy.slotName = this.slotName;
        copy.onSave = this.onSave;
        return copy;
    }

    /**
     * Copies another builder into this builder's recipe type.
     *
     * <p>Side effects: none on either source builder. The copied builder clears
     * its save callback so datagen hooks from the source type are not reused by
     * accident.</p>
     *
     * @param builder builder to copy
     * @return independent mutable copy assigned to this builder's recipe type
     */
    public MBDRecipeBuilder copyFrom(MBDRecipeBuilder builder) {
        return builder.copy(builder.id).onSave(null).recipeType(recipeType);
    }

    /**
     * Adds typed input contents using the capability's strongly typed converter.
     *
     * <p>Side effects: appends to this builder's input map using the current
     * {@link #perTick}, {@link #chance}, {@link #tierChanceBoost},
     * {@link #slotName}, and {@link #uiName} settings.</p>
     *
     * @param capability capability that owns the content type
     * @param obj values accepted by the capability converter
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder input(RecipeCapability<T> capability, T... obj) {
        input.computeIfAbsent(capability, c -> new ArrayList<>()).addAll(Arrays.stream(obj)
                .map(capability::of)
                .map(o -> new Content(o, perTick, chance, tierChanceBoost, slotName, uiName)).toList());
        return this;
    }

    /**
     * Adds typed output contents using the capability's strongly typed converter.
     *
     * @param capability capability that owns the content type
     * @param obj values accepted by the capability converter
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder output(RecipeCapability<T> capability, T... obj) {
        output.computeIfAbsent(capability, c -> new ArrayList<>()).addAll(Arrays.stream(obj)
                .map(capability::of)
                .map(o -> new Content(o, perTick, chance, tierChanceBoost, slotName, uiName)).toList());
        return this;
    }

    /**
     * Removes all input contents for a capability.
     *
     * @param capability capability whose inputs should be removed
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder removeInputs(RecipeCapability<T> capability) {
        input.remove(capability);
        return this;
    }

    /**
     * Removes all output contents for a capability.
     *
     * @param capability capability whose outputs should be removed
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder removeOutputs(RecipeCapability<T> capability) {
        output.remove(capability);
        return this;
    }

    /**
     * Adds input contents using the capability's generic object converter.
     *
     * @param capability capability that owns the content type
     * @param obj values accepted by {@link RecipeCapability#of(Object)}
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder inputs(RecipeCapability<T> capability, Object... obj) {
        input.computeIfAbsent(capability, c -> new ArrayList<>()).addAll(Arrays.stream(obj)
                .map(capability::of)
                .map(o -> new Content(o, perTick, chance, tierChanceBoost, slotName, uiName)).toList());
        return this;
    }

    /**
     * Adds output contents using the capability's generic object converter.
     *
     * @param capability capability that owns the content type
     * @param obj values accepted by {@link RecipeCapability#of(Object)}
     * @param <T> capability value type
     * @return this builder for chaining
     */
    public <T> MBDRecipeBuilder outputs(RecipeCapability<T> capability, Object... obj) {
        output.computeIfAbsent(capability, c -> new ArrayList<>()).addAll(Arrays.stream(obj)
                .map(capability::of)
                .map(o -> new Content(o, perTick, chance, tierChanceBoost, slotName, uiName)).toList());
        return this;
    }

    /**
     * Adds an execution condition to the recipe.
     *
     * @param condition condition evaluated by {@link MBDRecipe#checkConditions(RecipeLogic)}
     * before work advances
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addCondition(RecipeCondition condition) {
        conditions.add(condition);
        return this;
    }


    /**
     * Adds item ingredients as inputs.
     *
     * @param inputs non-null ingredients accepted by the item capability
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(Ingredient... inputs) {
        return input(ItemRecipeCapability.CAP, inputs);
    }

    /**
     * Adds concrete item stacks as inputs.
     *
     * <p>Preconditions: no stack may be empty. Side effects: logs and throws
     * {@link IllegalArgumentException} when an empty stack is supplied.</p>
     *
     * @param inputs stacks whose item and count become sized ingredients
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(ItemStack... inputs) {
        for (ItemStack itemStack : inputs) {
            if (itemStack.isEmpty()) {
                LDLib.LOGGER.error("gt recipe {} input items is empty", id);
                throw new IllegalArgumentException(id + ": input items is empty");
            }
        }
        return input(ItemRecipeCapability.CAP, Arrays.stream(inputs).map(SizedIngredient::create).toArray(Ingredient[]::new));
    }

    /**
     * Adds a tagged item input with a required amount.
     *
     * @param tag item tag accepted by the recipe
     * @param amount required stack size; expected to be positive
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(TagKey<Item> tag, int amount) {
        return inputItems(SizedIngredient.create(tag, amount));
    }

    /**
     * Adds one item from a tag as an input.
     *
     * @param tag item tag accepted by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(TagKey<Item> tag) {
        return inputItems(tag, 1);
    }

    /**
     * Adds a concrete item input with a required amount.
     *
     * @param input item accepted by the recipe
     * @param amount required stack size; expected to be positive
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(Item input, int amount) {
        return inputItems(new ItemStack(input, amount));
    }

    /**
     * Adds one concrete item input.
     *
     * @param input item accepted by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(Item input) {
        return inputItems(SizedIngredient.create(new ItemStack(input)));
    }

    /**
     * Adds one item input from a supplier.
     *
     * @param input supplier that must return a non-null item
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(Supplier<? extends Item> input) {
        return inputItems(input.get());
    }

    /**
     * Adds an item input from a supplier with a required amount.
     *
     * @param input supplier that must return a non-null item
     * @param amount required stack size; expected to be positive
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputItems(Supplier<? extends Item> input, int amount) {
        return inputItems(new ItemStack(input.get(), amount));
    }


    // for kjs
    /**
     * KubeJS alias for {@link #outputItems(ItemStack...)}.
     *
     * @param outputs non-empty output stacks
     * @return this builder for chaining
     */
    public MBDRecipeBuilder itemOutputs(ItemStack... outputs) {
        return outputItems(outputs);
    }

    /**
     * Adds concrete item stacks as outputs.
     *
     * <p>Preconditions: no stack may be empty. Side effects: logs and throws
     * {@link IllegalArgumentException} when an empty stack is supplied.</p>
     *
     * @param outputs stacks whose item and count become sized outputs
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputItems(ItemStack... outputs) {
        for (ItemStack itemStack : outputs) {
            if (itemStack.isEmpty()) {
                LDLib.LOGGER.error("gt recipe {} output items is empty", id);
                throw new IllegalArgumentException(id + ": output items is empty");
            }
        }
        return output(ItemRecipeCapability.CAP, Arrays.stream(outputs).map(SizedIngredient::create).toArray(Ingredient[]::new));
    }

    /**
     * Adds a concrete item output with an amount.
     *
     * @param input item produced by the recipe
     * @param amount produced stack size; expected to be positive
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputItems(Item input, int amount) {
        return outputItems(new ItemStack(input, amount));
    }

    /**
     * Adds one concrete item output.
     *
     * @param input item produced by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputItems(Item input) {
        return outputItems(new ItemStack(input));
    }

    /**
     * Adds one item output from a supplier.
     *
     * @param input supplier that must return a non-null item-like object
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputItems(Supplier<? extends ItemLike> input) {
        return outputItems(new ItemStack(input.get().asItem()));
    }

    /**
     * Adds an item output from a supplier with an amount.
     *
     * @param input supplier that must return a non-null item-like object
     * @param amount produced stack size; expected to be positive
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputItems(Supplier<? extends ItemLike> input, int amount) {
        return outputItems(new ItemStack(input.get().asItem(), amount));
    }

    /**
     * Adds an item input that is required but not consumed.
     *
     * <p>Side effects: temporarily sets {@link #chance} to {@code 0}, adds the
     * input, and restores the previous chance.</p>
     *
     * @param itemStack non-empty stack to require
     * @return this builder for chaining
     */
    public MBDRecipeBuilder notConsumable(ItemStack itemStack) {
        float lastChance = this.chance;
        this.chance = 0;
        inputItems(itemStack);
        this.chance = lastChance;
        return this;
    }

    /**
     * Adds a single item input that is required but not consumed.
     *
     * @param item item to require
     * @return this builder for chaining
     */
    public MBDRecipeBuilder notConsumable(Item item) {
        float lastChance = this.chance;
        this.chance = 0;
        inputItems(item);
        this.chance = lastChance;
        return this;
    }

    /**
     * Adds a supplied item input that is required but not consumed.
     *
     * @param item supplier that must return a non-null item
     * @return this builder for chaining
     */
    public MBDRecipeBuilder notConsumable(Supplier<? extends Item> item) {
        float lastChance = this.chance;
        this.chance = 0;
        inputItems(item);
        this.chance = lastChance;
        return this;
    }

    /**
     * Adds fluid stacks as inputs.
     *
     * <p>Business goal: normalize concrete fluids into tag-based ingredients on
     * Forge while preserving Fabric water behavior. Amounts are interpreted in
     * the units used by {@link FluidStack#getAmount()}.</p>
     *
     * @param inputs fluid stacks accepted by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputFluids(FluidStack... inputs) {
        return input(FluidRecipeCapability.CAP, Arrays.stream(inputs).map(fluid -> {
            if (!Platform.isForge() && fluid.getFluid() == Fluids.WATER) { // Special case for fabric, because there all fluids have to be tagged as water to function as water when placed.
                return FluidIngredient.of(fluid);
            } else {
                return FluidIngredient.of(TagUtil.createFluidTag(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath()), fluid.getAmount());
            }
        }).toArray(FluidIngredient[]::new));
    }

    /**
     * Adds fluid ingredients as inputs.
     *
     * @param inputs fluid ingredients accepted by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputFluids(FluidIngredient... inputs) {
        return input(FluidRecipeCapability.CAP, inputs);
    }

    /**
     * Adds fluid stacks as outputs.
     *
     * @param outputs fluid stacks produced by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputFluids(FluidStack... outputs) {
        return output(FluidRecipeCapability.CAP, Arrays.stream(outputs).map(FluidIngredient::of).toArray(FluidIngredient[]::new));
    }

    /**
     * Adds fluid ingredients as outputs.
     *
     * @param outputs fluid ingredients produced by the recipe
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputFluids(FluidIngredient... outputs) {
        return output(FluidRecipeCapability.CAP, outputs);
    }

    /**
     * Requires an exact redstone input strength.
     *
     * @param strength requested signal strength; clamped to {@code [0, 15]} by
     * {@link RedstoneSignal}
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputRedstone(int strength) {
        return input(RedstoneSignalRecipeCapability.CAP, RedstoneSignal.input(strength));
    }

    /**
     * Requires a redstone input strength range.
     *
     * @param minStrength inclusive lower bound; clamped to {@code [0, 15]}
     * @param maxStrength exclusive upper bound; clamped to {@code [0, 16]} and
     * adjusted to remain above the lower bound
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputRedstone(int minStrength, int maxStrength) {
        return input(RedstoneSignalRecipeCapability.CAP, RedstoneSignal.input(minStrength, maxStrength));
    }

    /**
     * Adds a redstone input parsed by the redstone capability.
     *
     * @param signal textual signal expression accepted by
     * {@link RedstoneSignalRecipeCapability}
     * @return this builder for chaining
     */
    public MBDRecipeBuilder inputRedstone(String signal) {
        return input(RedstoneSignalRecipeCapability.CAP, RedstoneSignalRecipeCapability.CAP.of(signal));
    }

    /**
     * Emits a redstone signal output.
     *
     * @param strength signal strength; clamped to {@code [0, 15]}
     * @param duration output duration in ticks; negative values are clamped to
     * {@code 0}
     * @return this builder for chaining
     */
    public MBDRecipeBuilder outputRedstone(int strength, int duration) {
        return output(RedstoneSignalRecipeCapability.CAP, RedstoneSignal.output(strength, duration));
    }

    //////////////////////////////////////
    //**********     DATA    ***********//
    //////////////////////////////////////
    /**
     * Adds custom NBT data to the recipe.
     *
     * @param key data key; should be stable for consumers that read the payload
     * @param data tag value stored directly under the key
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, Tag data) {
        this.data.put(key, data);
        return this;
    }

    /**
     * Adds integer custom data.
     *
     * @param key data key
     * @param data integer value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, int data) {
        this.data.putInt(key, data);
        return this;
    }

    /**
     * Adds long custom data.
     *
     * @param key data key
     * @param data long value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, long data) {
        this.data.putLong(key, data);
        return this;
    }

    /**
     * Adds string custom data.
     *
     * @param key data key
     * @param data string value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, String data) {
        this.data.putString(key, data);
        return this;
    }

    /**
     * Adds float custom data.
     *
     * @param key data key
     * @param data float value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, Float data) {
        this.data.putFloat(key, data);
        return this;
    }

    /**
     * Adds boolean custom data.
     *
     * @param key data key
     * @param data boolean value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder addData(String key, boolean data) {
        this.data.putBoolean(key, data);
        return this;
    }

    /**
     * Stores the electric blast furnace temperature requirement.
     *
     * @param blastTemp temperature value consumed by recipe-specific logic
     * @return this builder for chaining
     */
    public MBDRecipeBuilder blastFurnaceTemp(int blastTemp) {
        return addData("ebf_temp", blastTemp);
    }

    /**
     * Stores the required amount of explosives.
     *
     * @param explosivesAmount recipe-specific amount value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder explosivesAmount(int explosivesAmount) {
        return addData("explosives_amount", explosivesAmount);
    }

    /**
     * Stores the required explosive item stack.
     *
     * @param explosivesType stack serialized into custom NBT data
     * @return this builder for chaining
     */
    public MBDRecipeBuilder explosivesType(ItemStack explosivesType) {
        return addData("explosives_type", explosivesType.save(new CompoundTag()));
    }

    /**
     * Stores solder multiplier metadata.
     *
     * @param multiplier recipe-specific multiplier value
     * @return this builder for chaining
     */
    public MBDRecipeBuilder solderMultiplier(int multiplier) {
        return addData("solderMultiplier", multiplier);
    }

    /**
     * Stores whether distillery recipe generation should be disabled.
     *
     * @param flag {@code true} to disable derived distillery recipes
     * @return this builder for chaining
     */
    public MBDRecipeBuilder disableDistilleryRecipes(boolean flag) {
        return addData("disable_distillery", flag);
    }

    /**
     * Stores fusion startup energy metadata.
     *
     * @param eu energy value consumed by fusion-specific logic
     * @return this builder for chaining
     */
    public MBDRecipeBuilder fusionStartEU(long eu) {
        return addData("eu_to_start", eu);
    }

    //////////////////////////////////////
    //*******     CONDITIONS    ********//
    //////////////////////////////////////

    /**
     * Adds a dimension condition.
     *
     * @param dimension dimension id that should match the machine level
     * @param reverse {@code true} to invert the condition
     * @return this builder for chaining
     */
    public MBDRecipeBuilder dimension(ResourceLocation dimension, boolean reverse) {
        return addCondition(new DimensionCondition(dimension).setReverse(reverse));
    }

    /**
     * Adds a non-inverted dimension condition.
     *
     * @param dimension dimension id that should match the machine level
     * @return this builder for chaining
     */
    public MBDRecipeBuilder dimension(ResourceLocation dimension) {
        return dimension(dimension, false);
    }

    /**
     * Adds a biome condition.
     *
     * @param biome biome id that should match the machine position
     * @param reverse {@code true} to invert the condition
     * @return this builder for chaining
     */
    public MBDRecipeBuilder biome(ResourceLocation biome, boolean reverse) {
        return addCondition(new BiomeCondition(biome).setReverse(reverse));
    }

    /**
     * Adds a non-inverted biome condition.
     *
     * @param biome biome id that should match the machine position
     * @return this builder for chaining
     */
    public MBDRecipeBuilder biome(ResourceLocation biome) {
        return biome(biome, false);
    }

    /**
     * Adds a rain-level condition.
     *
     * @param minLevel inclusive minimum rain level, normally in {@code [0, 1]}
     * @param maxLevel inclusive maximum rain level, normally in {@code [0, 1]}
     * @param reverse {@code true} to invert the condition
     * @return this builder for chaining
     */
    public MBDRecipeBuilder rain(float minLevel, float maxLevel, boolean reverse) {
        return addCondition(new RainingCondition(minLevel, maxLevel).setReverse(reverse));
    }

    /**
     * Adds a non-inverted rain-level condition.
     *
     * @param minLevel inclusive minimum rain level, normally in {@code [0, 1]}
     * @param maxLevel inclusive maximum rain level, normally in {@code [0, 1]}
     * @return this builder for chaining
     */
    public MBDRecipeBuilder rain(float minLevel, float maxLevel) {
        return rain(minLevel, maxLevel, false);
    }

    /**
     * Adds a thunder-level condition.
     *
     * @param minLevel inclusive minimum thunder level, normally in {@code [0, 1]}
     * @param maxLevel inclusive maximum thunder level, normally in {@code [0, 1]}
     * @param reverse {@code true} to invert the condition
     * @return this builder for chaining
     */
    public MBDRecipeBuilder thunder(float minLevel, float maxLevel, boolean reverse) {
        return addCondition(new ThunderCondition(minLevel, maxLevel).setReverse(reverse));
    }

    /**
     * Adds a non-inverted thunder-level condition.
     *
     * @param minLevel inclusive minimum thunder level, normally in {@code [0, 1]}
     * @param maxLevel inclusive maximum thunder level, normally in {@code [0, 1]}
     * @return this builder for chaining
     */
    public MBDRecipeBuilder thunder(float minLevel, float maxLevel) {
        return thunder(minLevel, maxLevel, false);
    }

    /**
     * Adds a machine Y-position condition.
     *
     * @param min inclusive minimum block Y coordinate
     * @param max inclusive maximum block Y coordinate
     * @param reverse {@code true} to invert the condition
     * @return this builder for chaining
     */
    public MBDRecipeBuilder posY(int min, int max, boolean reverse) {
        return addCondition(new PositionYCondition(min, max).setReverse(reverse));
    }

    /**
     * Adds a non-inverted machine Y-position condition.
     *
     * @param min inclusive minimum block Y coordinate
     * @param max inclusive maximum block Y coordinate
     * @return this builder for chaining
     */
    public MBDRecipeBuilder posY(int min, int max) {
        return posY(min, max, false);
    }

    /**
     * Builds a datagen recipe wrapper.
     *
     * <p>Preconditions: {@link #recipeType} must be non-null. Side effects: none
     * until the returned wrapper is serialized by datagen, which builds a raw
     * recipe view from current builder state.</p>
     *
     * @return finished recipe wrapper using {@link MBDRecipeSerializer#SERIALIZER}
     */
    public FinishedRecipe build() {
        return new FinishedRecipe() {
            @Override
            public void serializeRecipeData(JsonObject pJson) {
                MBDRecipeSerializer.SERIALIZER.toJson(buildRawRecipe());
            }

            @Override
            public ResourceLocation getId() {
                return new ResourceLocation(id.getNamespace(), recipeType.getRegistryName().getPath() + "/" + id.getPath());
            }

            @Override
            public RecipeSerializer<?> getType() {
                return MBDRecipeSerializer.SERIALIZER;
            }

            @Nullable
            @Override
            public JsonObject serializeAdvancement() {
                return null;
            }

            @Nullable
            @Override
            public ResourceLocation getAdvancementId() {
                return null;
            }
        };
    }

    /**
     * Emits this builder as a datagen recipe.
     *
     * <p>Side effects: invokes {@link #onSave} when present and passes the built
     * recipe to {@code consumer}.</p>
     *
     * @param consumer datagen output consumer
     */
    public void save(Consumer<FinishedRecipe> consumer) {
        if (onSave != null) {
            onSave.accept(this, consumer);
        }
        consumer.accept(build());
    }

    /**
     * Builds and registers this recipe as a builtin recipe on its recipe type.
     *
     * <p>Preconditions: {@link #recipeType} must be non-null. Side effects:
     * inserts the built recipe into {@link MBDRecipeType#builtinRecipes} under
     * {@link #id}, replacing any existing builtin with the same id.</p>
     *
     * @return raw recipe that was inserted into the type
     */
    public MBDRecipe saveAsBuiltinRecipe() {
        MBDRecipe recipe = buildRawRecipe();
        recipeType.builtinRecipes.put(id, recipe);
        return recipe;
    }

    /**
     * Builds the runtime recipe object from the current mutable builder state.
     *
     * <p>Side effects: none directly, but the resulting recipe receives the
     * builder's current maps, condition list, and data tag by reference.</p>
     *
     * @return runtime recipe representation
     */
    public MBDRecipe buildRawRecipe() {
        return new MBDRecipe(recipeType, id, input, output, conditions, data, duration, isFuel, isXEIHidden, priority);
    }

}
