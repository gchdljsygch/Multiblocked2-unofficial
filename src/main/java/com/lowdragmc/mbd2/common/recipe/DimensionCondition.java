package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.SearchComponentConfigurator;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author KilaBash
 * @date 2022/05/27
 * @implNote DimensionCondition, specific dimension
 */
@Getter
@Setter
@NoArgsConstructor
public class DimensionCondition extends RecipeCondition {

    public final static DimensionCondition INSTANCE = new DimensionCondition();
    private static final ResourceLocation DUMMY_DIMENSION = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:dummy"));
    private ResourceLocation dimension = DUMMY_DIMENSION;

    /**
     * Creates a recipe condition that requires the machine to be in a specific dimension.
     * <p>
     * The dimension id is compared with {@link Level#dimension()} during recipe testing. The condition fails when the
     * machine has no level.
     *
     * @param dimension dimension registry id to require
     */
    public DimensionCondition(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    @Override
    public String getType() {
        return "dimension";
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.dimension.tooltip", dimension);
    }

    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        Level level = recipeLogic.machine.getLevel();
        return level != null && dimension.equals(level.dimension().location());
    }

    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("dim", dimension.toString());
        return config;
    }

    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        dimension = parseDimension(GsonHelper.getAsString(config, "dim", "dummy"));
        return this;
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        dimension = parseDimension(buf.readUtf());
        return this;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeUtf(dimension.toString());
    }

    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putString("dim", dimension.toString());
        return tag;
    }

    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        dimension = parseDimension(tag.getString("dim"));
        return this;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        var selector = new SearchComponentConfigurator<>(getTranslationKey(),
                () -> this.dimension,
                d -> this.dimension = d,
                DUMMY_DIMENSION,
                true,
                this::search,
                ResourceLocation::toString
        );
        selector.setUp(false);
        selector.setTips("config.recipe.condition.dimension.tooltip");
        father.addConfigurators(selector);
    }

    private static ResourceLocation parseDimension(String raw) {
        var parsed = ResourceLocation.tryParse(raw);
        return parsed == null ? DUMMY_DIMENSION : parsed;
    }

    /**
     * Searches loaded dimension-type registry keys for the editor selector.
     * <p>
     * This method is client-only because it reads {@link Minecraft#getInstance()} registry access. It cooperates with
     * asynchronous search cancellation by returning when the current thread is interrupted.
     *
     * @param word case-insensitive substring to match against dimension ids
     * @param find callback receiving matching dimension ids
     */
    protected void search(String word, Consumer<ResourceLocation> find) {
        var wordLower = word.toLowerCase();
        for (var biomeEntry : Minecraft.getInstance().level.registryAccess().registry(Registries.DIMENSION_TYPE).get().keySet()) {
            if (Thread.currentThread().isInterrupted()) return;
            if (biomeEntry.toString().contains(wordLower)) {
                find.accept(biomeEntry);
            }
        }
    }
}
