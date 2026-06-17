package com.lowdragmc.mbd2.common.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.RecipeCondition;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Recipe condition that counts selected block types inside a formed multiblock structure.
 *
 * <p>The business goal is to gate recipes on the controller's cached multiblock composition, for example requiring a
 * minimum number of casing or coil blocks. The condition only observes machines that implement {@link IMultiController};
 * non-multiblock machines count as zero matching blocks. Matching reads world block states on the recipe logic's
 * owning server thread and has no side effects.</p>
 */
@Getter
@Setter
public class BlockCondition extends RecipeCondition {

    /**
     * Prototype instance used by the recipe-condition registry and deserializers.
     */
    public final static BlockCondition INSTANCE = new BlockCondition();
    @Configurable(name = "config.recipe.condition.block.min")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int minCount;
    @Configurable(name = "config.recipe.condition.block.max")
    @NumberRange(range = {0, Integer.MAX_VALUE})
    private int maxCount;
    @Configurable(name = "config.recipe.condition.block.blocks", collapse = false)
    private Block[] blocks;

    /**
     * Creates a condition that accepts only multiblocks with zero matching blocks.
     */
    public BlockCondition() {
        this(0, 0);
    }

    /**
     * Creates a block-count condition.
     *
     * @param minLevel minimum accepted count, inclusive; expected range is {@code [0, Integer.MAX_VALUE]}
     * @param maxLevel maximum accepted count, inclusive; expected range is {@code [0, Integer.MAX_VALUE]}
     * @param blocks   block types counted in the multiblock cache; an empty array means no block can match
     */
    public BlockCondition(int minLevel, int maxLevel, Block... blocks) {
        this.minCount = minLevel;
        this.maxCount = maxLevel;
        this.blocks = blocks;
    }

    /**
     * Returns the serialized recipe-condition type id.
     *
     * @return {@code block}
     */
    @Override
    public String getType() {
        return "block";
    }

    /**
     * Builds the tooltip describing the accepted block count and block names.
     *
     * @return localized tooltip component
     */
    @Override
    public Component getTooltips() {
        var blockNames = Component.empty();
        for (int i = 0; i < blocks.length; i++) {
            blockNames.append(blocks[i].getName());
            if (i < blocks.length - 1) {
                blockNames.append(Component.literal(" || "));
            }
        }
        return Component.translatable("recipe.condition.block.tooltip", blockNames, minCount, maxCount);
    }

    /**
     * Tests the current multiblock composition against the configured count range.
     *
     * @param recipe      recipe being checked
     * @param recipeLogic recipe logic supplying the machine and level
     * @return {@code true} when the count of matching blocks is between {@code minCount} and {@code maxCount},
     * inclusive
     */
    @Override
    public boolean test(@Nonnull MBDRecipe recipe, @Nonnull RecipeLogic recipeLogic) {
        var amount = 0;
        if (recipeLogic.machine instanceof IMultiController controller) {
            var level = controller.getLevel();
            for (var pos : controller.getMultiblockState().getCache()) {
                if (ArrayUtils.contains(blocks, level.getBlockState(pos).getBlock())) {
                    amount++;
                }
            }
        }
        return amount >= minCount && amount <= maxCount;
    }

    /**
     * Serializes the condition to JSON.
     *
     * @return JSON object with {@code minCount}, {@code maxCount}, and {@code blocks} registry ids
     */
    @Nonnull
    @Override
    public JsonObject serialize() {
        JsonObject config = super.serialize();
        config.addProperty("minCount", minCount);
        config.addProperty("maxCount", maxCount);
        var array = new JsonArray();
        for (Block block : blocks) {
            var key = ForgeRegistries.BLOCKS.getKey(block);
            if (key != null) {
                array.add(key.toString());
            }
        }
        config.add("blocks", array);
        return config;
    }

    /**
     * Loads the condition from JSON.
     *
     * <p>Unknown block ids fall back to air so deserialization can continue with a visible placeholder.</p>
     *
     * @param config JSON object produced by {@link #serialize()}
     * @return this condition instance
     */
    @Override
    public RecipeCondition deserialize(@Nonnull JsonObject config) {
        super.deserialize(config);
        minCount = GsonHelper.getAsInt(config, "minCount", 0);
        maxCount = GsonHelper.getAsInt(config, "maxCount", 0);
        var array = GsonHelper.getAsJsonArray(config, "blocks", new JsonArray());
        blocks = new Block[array.size()];
        for (int i = 0; i < array.size(); i++) {
            var key = array.get(i).getAsString();
            blocks[i] = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(key));
            if (blocks[i] == null) {
                blocks[i] = Blocks.AIR;
            }
        }
        return this;
    }

    /**
     * Reads this condition from the network buffer.
     *
     * @param buf source buffer
     * @return this condition instance
     */
    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        minCount = buf.readVarInt();
        maxCount = buf.readVarInt();
        int size = buf.readVarInt();
        blocks = new Block[size];
        for (int i = 0; i < size; i++) {
            blocks[i] = ForgeRegistries.BLOCKS.getValue(buf.readResourceLocation());
        }
        return this;
    }

    /**
     * Writes this condition to the network buffer.
     *
     * @param buf destination buffer
     */
    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeVarInt(minCount);
        buf.writeVarInt(maxCount);
        buf.writeVarInt(blocks.length);
        for (Block block : blocks) {
            buf.writeResourceLocation(Optional.ofNullable(ForgeRegistries.BLOCKS.getKey(block)).orElse(ResourceLocation.parse("minecraft:air")));
        }
    }

    /**
     * Serializes this condition to NBT.
     *
     * @return NBT tag with count bounds and block registry ids
     */
    @Override
    public CompoundTag toNBT() {
        var tag = super.toNBT();
        tag.putInt("minCount", minCount);
        tag.putInt("maxCount", maxCount);
        var array = new ListTag();
        for (Block block : blocks) {
            var key = ForgeRegistries.BLOCKS.getKey(block);
            if (key != null) {
                array.add(StringTag.valueOf(key.toString()));
            }
        }
        tag.put("blocks", array);
        return tag;
    }

    /**
     * Loads this condition from NBT.
     *
     * @param tag source tag produced by {@link #toNBT()}
     * @return this condition instance
     */
    @Override
    public RecipeCondition fromNBT(CompoundTag tag) {
        super.fromNBT(tag);
        minCount = tag.getInt("minCount");
        maxCount = tag.getInt("maxCount");
        var array = tag.getList("blocks", Tag.TAG_STRING);
        blocks = new Block[array.size()];
        for (int i = 0; i < array.size(); i++) {
            var key = array.getString(i);
            blocks[i] = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(key));
            if (blocks[i] == null) {
                blocks[i] = Blocks.AIR;
            }
        }
        return this;
    }

}
