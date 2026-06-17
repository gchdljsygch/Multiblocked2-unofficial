package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorSelectorConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * To modify the controller recipe on the fly. You can use it to make a upgrade/plugin part.
 */
public class RecipeModifier implements IConfigurable, IPersistedSerializable {
    @Configurable(name = "config.recipe.content_modifier", subConfigurable = true, tips = {"config.recipe.content_modifier.tooltip"}, collapse = false)
    public final ContentModifier contentModifier = ContentModifier.of(1, 0);
    @Configurable(name = "config.recipe.target_content", tips = {"config.recipe.target_content.tooltip"})
    public final IO targetContent = IO.BOTH;
    @Configurable(name = "config.recipe.duration_modifier", subConfigurable = true, tips = {"config.recipe.duration_modifier.tooltip"}, collapse = false)
    public final ContentModifier durationModifier = ContentModifier.of(1, 0);
    public final List<RecipeCondition> recipeConditions = new ArrayList<>();
    @Configurable(name = "config.machine_settings.max_parallel", subConfigurable = true, tips = "config.machine_settings.max_parallel.tooltip", collapse = false)
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private ContentModifier maxParallel = ContentModifier.identity();

    /**
     * Sets this modifier's max-parallel multiplier from a fixed integer.
     * <p>
     * Values below {@code 1} are clamped to {@code 1}. Side effect: replaces the current max-parallel
     * {@link ContentModifier}; recipe content and duration modifiers are unchanged.
     *
     * @param maxParallel requested max parallel multiplier
     * @return this modifier for chaining
     */
    public RecipeModifier setMaxParallel(int maxParallel) {
        return setMaxParallel(ContentModifier.multiplier(Math.max(1, maxParallel)));
    }

    /**
     * Sets this modifier's max-parallel transformation.
     * <p>
     * A {@code null} value resets the max-parallel contribution to identity. The modifier is evaluated only when its
     * recipe conditions pass.
     *
     * @param maxParallel content modifier applied to max parallel values
     * @return this modifier for chaining
     */
    public RecipeModifier setMaxParallel(ContentModifier maxParallel) {
        this.maxParallel = maxParallel == null ? ContentModifier.identity() : maxParallel;
        return this;
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = IPersistedSerializable.super.serializeNBT();
        ListTag conditions = new ListTag();
        for (RecipeCondition condition : recipeConditions) {
            CompoundTag conditionTag = new CompoundTag();
            conditionTag.putString("type", MBDRegistries.RECIPE_CONDITIONS.getKey(condition.getClass()));
            conditionTag.put("data", condition.toNBT());
            conditions.add(conditionTag);
        }
        tag.put("recipeConditions", conditions);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        IPersistedSerializable.super.deserializeNBT(tag);
        recipeConditions.clear();
        ListTag conditions = tag.getList("recipeConditions", Tag.TAG_COMPOUND);
        for (int i = 0; i < conditions.size(); i++) {
            CompoundTag conditionTag = conditions.getCompound(i);
            var condition = RecipeCondition.create(MBDRegistries.RECIPE_CONDITIONS.get(conditionTag.getString("type")));
            if (condition != null) {
                condition.fromNBT(conditionTag.getCompound("data"));
                recipeConditions.add(condition);
            }
        }
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        IConfigurable.super.buildConfigurator(father);
        var conditions = new ArrayConfiguratorGroup<>("config.recipe.recipe_conditions", false,
                () -> recipeConditions, (getter, setter) -> new ConfiguratorSelectorConfigurator<>("config.recipe.recipe_condition.type", false,
                () -> getter.get().getType(), type -> {
            var current = getter.get();
            var condition = RecipeCondition.create(MBDRegistries.RECIPE_CONDITIONS.get(type));
            if (condition != null) {
                recipeConditions.set(recipeConditions.indexOf(current), condition);
            }
        }, "rain", true, MBDRegistries.RECIPE_CONDITIONS.registry().keySet().stream().toList(),
                String::toString, (type, container) -> {
            var current = getter.get();
            current.buildConfigurator(container);
        }), true);
        conditions.setTips("config.recipe.recipe_conditions.tooltip");
        conditions.setAddDefault(() -> RecipeCondition.create(MBDRegistries.RECIPE_CONDITIONS.get("rain")));
        conditions.setOnAdd(recipeConditions::add);
        conditions.setOnRemove(recipeConditions::remove);
        conditions.setOnUpdate(list -> {
            recipeConditions.clear();
            recipeConditions.addAll(list);
        });
        father.addConfigurators(conditions);
    }

    public static class RecipeModifiers implements ITagSerializable<ListTag>, IConfigurable {
        public final List<RecipeModifier> recipeModifiers = new ArrayList<>();

        @Override
        public ListTag serializeNBT() {
            var modifiers = new ListTag();
            for (RecipeModifier modifier : recipeModifiers) {
                modifiers.add(modifier.serializeNBT());
            }
            return modifiers;
        }

        @Override
        public void deserializeNBT(ListTag modifiers) {
            recipeModifiers.clear();
            for (int i = 0; i < modifiers.size(); i++) {
                var modifier = new RecipeModifier();
                modifier.deserializeNBT(modifiers.getCompound(i));
                recipeModifiers.add(modifier);
            }
        }

        @Override
        public void buildConfigurator(ConfiguratorGroup father) {
            var modifiers = new ArrayConfiguratorGroup<>("config.recipe.recipe_modifiers", true,
                    () -> recipeModifiers, (getter, setter) -> {
                var recipeModifier = getter.get();
                var group = new ConfiguratorGroup("config.recipe.content_modifier", false);
                recipeModifier.buildConfigurator(group);
                return group;
            }, true);
            modifiers.setTips("config.recipe.recipe_modifiers.tooltip");
            modifiers.setAddDefault(RecipeModifier::new);
            modifiers.setOnAdd(recipeModifiers::add);
            modifiers.setOnRemove(recipeModifiers::remove);
            modifiers.setOnUpdate(list -> {
                recipeModifiers.clear();
                recipeModifiers.addAll(list);
            });
            father.addConfigurators(modifiers);
        }

        /**
         * Sets a global max-parallel multiplier across this modifier list.
         * <p>
         * The method reuses an existing condition-free modifier that changes only max parallel, or creates one when none
         * exists. Values below {@code 1} are clamped by {@link RecipeModifier#setMaxParallel(int)}.
         *
         * @param maxParallel requested max parallel multiplier
         * @return this modifier list for chaining
         */
        public RecipeModifiers setMaxParallel(int maxParallel) {
            getOrCreateGlobalMaxParallelModifier().setMaxParallel(maxParallel);
            return this;
        }

        /**
         * Sets a global max-parallel transformation across this modifier list.
         * <p>
         * The method reuses an existing condition-free modifier that changes only max parallel, or creates one when none
         * exists. A {@code null} modifier resets that global contribution to identity.
         *
         * @param maxParallel content modifier applied to max parallel values
         * @return this modifier list for chaining
         */
        public RecipeModifiers setMaxParallel(ContentModifier maxParallel) {
            getOrCreateGlobalMaxParallelModifier().setMaxParallel(maxParallel);
            return this;
        }

        private RecipeModifier getOrCreateGlobalMaxParallelModifier() {
            for (var modifier : recipeModifiers) {
                if (isGlobalMaxParallelOnly(modifier)) {
                    return modifier;
                }
            }
            var modifier = new RecipeModifier();
            recipeModifiers.add(modifier);
            return modifier;
        }

        private boolean isGlobalMaxParallelOnly(RecipeModifier modifier) {
            return modifier.recipeConditions.isEmpty() &&
                    modifier.contentModifier.isIdentity() &&
                    modifier.durationModifier.isIdentity() &&
                    modifier.targetContent == IO.BOTH;
        }

        /**
         * Applies matching content and duration modifiers to a recipe.
         * <p>
         * Only modifiers whose conditions pass for the supplied logic/recipe pair contribute. Input and output content
         * modifiers are merged independently according to {@link IO}; duration modifiers are merged and applied to the
         * copied recipe duration. The original recipe is returned unchanged when no modifier applies.
         *
         * @param recipeLogic recipe logic providing runtime context for conditions
         * @param recipe      original recipe to inspect and copy as needed
         * @return original recipe or a modified copy
         */
        public @Nonnull MBDRecipe applyModifiers(RecipeLogic recipeLogic, @Nonnull MBDRecipe recipe) {
            if (recipeModifiers.isEmpty()) return recipe;
            var contentModifiers = new ArrayList<Pair<ContentModifier, IO>>();
            var durationModifiers = new ArrayList<ContentModifier>();

            for (var modifier : recipeModifiers) {
                if (checkConditions(recipeLogic, recipe, modifier)) {
                    if (!modifier.contentModifier.isIdentity() && modifier.targetContent != IO.NONE) {
                        contentModifiers.add(Pair.of(modifier.contentModifier, modifier.targetContent));
                    }
                    if (!modifier.durationModifier.isIdentity()) {
                        durationModifiers.add(modifier.durationModifier);
                    }
                }
            }
            if (!contentModifiers.isEmpty()) {
                var inputModifiers = contentModifiers.stream().filter(pair -> pair.getSecond() == IO.IN || pair.getSecond() == IO.BOTH).map(Pair::getFirst).toList();
                var outputModifiers = contentModifiers.stream().filter(pair -> pair.getSecond() == IO.OUT || pair.getSecond() == IO.BOTH).map(Pair::getFirst).toList();
                if (!inputModifiers.isEmpty()) {
                    recipe = recipe.copy(inputModifiers.stream().reduce(ContentModifier.IDENTITY, ContentModifier::merge), false, IO.IN);
                }
                if (!outputModifiers.isEmpty()) {
                    recipe = recipe.copy(outputModifiers.stream().reduce(ContentModifier.IDENTITY, ContentModifier::merge), false, IO.OUT);
                }
            }
            if (!durationModifiers.isEmpty()) {
                if (contentModifiers.isEmpty()) {
                    recipe = recipe.copy();
                }
                recipe.duration = durationModifiers.stream().reduce(ContentModifier.IDENTITY, ContentModifier::merge).apply(recipe.duration).intValue();
            }
            return recipe;
        }

        /**
         * Resolves the merged max-parallel contribution for a recipe.
         * <p>
         * Only modifiers whose conditions pass and whose max-parallel modifier is not identity contribute. The return
         * value is a modifier rather than the final integer so callers can merge it with dynamic runtime contributions.
         *
         * @param recipeLogic recipe logic providing runtime context for conditions
         * @param recipe      recipe being evaluated
         * @return merged max-parallel modifier, or {@link ContentModifier#IDENTITY} when none apply
         */
        public ContentModifier getMaxParallel(RecipeLogic recipeLogic, @Nonnull MBDRecipe recipe) {
            if (recipeModifiers.isEmpty()) return ContentModifier.IDENTITY;
            var maxParallel = ContentModifier.IDENTITY;
            for (var modifier : recipeModifiers) {
                if (!modifier.maxParallel.isIdentity() && checkConditions(recipeLogic, recipe, modifier)) {
                    maxParallel = maxParallel.merge(modifier.maxParallel);
                }
            }
            return maxParallel;
        }

        private boolean checkConditions(RecipeLogic recipeLogic, @Nonnull MBDRecipe recipe, RecipeModifier modifier) {
            var or = new HashMap<String, List<RecipeCondition>>();
            var success = true;
            for (RecipeCondition condition : modifier.recipeConditions) {
                if (condition.isOr()) {
                    or.computeIfAbsent(condition.getType(), type -> new ArrayList<>()).add(condition);
                } else if (condition.test(recipe, recipeLogic) == condition.isReverse()) {
                    success = false;
                    break;
                }
            }
            for (List<RecipeCondition> conditions : or.values()) {
                MBDRecipe finalRecipe = recipe;
                if (conditions.stream().allMatch(condition -> condition.test(finalRecipe, recipeLogic) == condition.isReverse())) {
                    success = false;
                    break;
                }
            }
            return success;
        }
    }
}
