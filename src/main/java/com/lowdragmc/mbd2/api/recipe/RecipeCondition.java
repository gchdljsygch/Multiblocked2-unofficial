package com.lowdragmc.mbd2.api.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.mbd2.MBD2;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base class for configurable conditions that gate whether an MBD recipe may
 * run.
 *
 * <p>The business goal is to let recipe types attach reusable checks such as
 * environment, machine, or resource predicates to a recipe. Implementations are
 * created through a no-argument constructor, configured from JSON/network/NBT,
 * and evaluated on the recipe logic's game thread. Instances are mutable during
 * configuration because {@link #isReverse} is deserialized into the object.</p>
 */
@Accessors(chain = true)
public abstract class RecipeCondition implements IConfigurable {

    /**
     * Creates a condition instance from its registered implementation class.
     *
     * <p>Preconditions: implementations must expose a no-argument constructor.
     * Side effects: logs an error when construction fails.</p>
     *
     * @param clazz condition implementation class; {@code null} is accepted
     * @return new condition instance, or {@code null} when no class is supplied
     * or construction fails
     */
    @Nullable
    public static RecipeCondition create(Class<? extends RecipeCondition> clazz) {
        if (clazz == null) return null;
        try {
            return clazz.newInstance();
        } catch (Exception ignored) {
            MBD2.LOGGER.error("condition {} has no NonArgsConstructor", clazz);
            return null;
        }
    }

    @Configurable(name = "config.recipe.condition.reverse", tips = "config.recipe.condition.reverse.tooltip")
    @Setter
    @Getter
    protected boolean isReverse;

    /**
     * Returns the condition type key used for translation and texture lookup.
     *
     * @return non-empty registry-style type string supplied by the implementation
     */
    public abstract String getType();

    /**
     * Builds the translation key for this condition's display name.
     *
     * @return key in the form {@code recipe.condition.<type>}
     */
    public String getTranslationKey() {
        return "recipe.condition." + getType();
    }

    /**
     * Defines how this condition participates in condition aggregation.
     *
     * @return {@code true} when this condition is evaluated as part of an OR
     * group; implementations may return {@code false} for stricter grouping
     */
    public boolean isOr() {
        return true;
    }

    /**
     * Builds the tooltip shown by recipe viewers or editors.
     *
     * @return localized tooltip component describing the configured condition
     */
    public abstract Component getTooltips();

    /**
     * Evaluates whether the condition passes for a recipe and machine logic.
     *
     * <p>Preconditions: both arguments are non-null and should represent the
     * recipe currently being matched or processed. Implementations should avoid
     * cross-thread world access and should document any state mutation they
     * perform.</p>
     *
     * @param recipe      recipe being checked
     * @param recipeLogic runtime logic of the machine checking the recipe
     * @return {@code true} when the condition allows the recipe before reverse
     * handling by the caller
     */
    public abstract boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic);

    /**
     * Returns the default icon texture for this condition type.
     *
     * @return GUI texture at {@code mbd2:textures/gui/condition/<type>.png}
     */
    public IGuiTexture getIcon() {
        return new ResourceTexture("mbd2:textures/gui/condition/" + getType() + ".png");
    }

    /**
     * Creates a configured copy of this condition.
     *
     * <p>Preconditions: the concrete class must be creatable by
     * {@link #create(Class)}. Side effects: serializes this instance and
     * deserializes the data into the new instance.</p>
     *
     * @return copied condition with the same serialized configuration
     */
    public RecipeCondition copy() {
        return create(getClass()).deserialize(serialize());
    }

    /**
     * Serializes this condition to JSON.
     *
     * <p>Subclasses should extend the returned object when they have additional
     * configuration. Side effects: none.</p>
     *
     * @return JSON object containing shared condition settings
     */
    @Nonnull
    public JsonObject serialize() {
        JsonObject jsonObject = new JsonObject();
        if (isReverse) {
            jsonObject.addProperty("reverse", true);
        }
        return jsonObject;
    }

    /**
     * Applies JSON configuration to this condition.
     *
     * <p>Preconditions: {@code config} must be non-null. Side effects: updates
     * this instance's mutable configuration.</p>
     *
     * @param config JSON object previously produced by {@link #serialize()} or a
     *               compatible editor
     * @return this instance for chaining
     */
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        isReverse = GsonHelper.getAsBoolean(config, "reverse", false);
        return this;
    }

    /**
     * Writes shared condition configuration to a network buffer.
     *
     * @param buf destination buffer; must be non-null
     */
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeBoolean(isReverse);
    }

    /**
     * Reads shared condition configuration from a network buffer.
     *
     * <p>Side effects: updates this instance's mutable configuration.</p>
     *
     * @param buf source buffer; must be positioned at this condition's payload
     * @return this instance for chaining
     */
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        isReverse = buf.readBoolean();
        return this;
    }

    /**
     * Writes shared condition configuration to NBT.
     *
     * @return tag containing shared condition settings
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("reverse", isReverse);
        return tag;
    }

    /**
     * Reads shared condition configuration from NBT.
     *
     * <p>Side effects: updates this instance's mutable configuration.</p>
     *
     * @param tag source tag; missing keys fall back to vanilla NBT defaults
     * @return this instance for chaining
     */
    public RecipeCondition fromNBT(CompoundTag tag) {
        isReverse = tag.getBoolean("reverse");
        return this;
    }
}
