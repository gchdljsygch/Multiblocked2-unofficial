package com.lowdragmc.mbd2.api.pattern;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.Builder;
import com.lowdragmc.mbd2.utils.ControllerBlockInfo;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serializable preview shape for a multiblock pattern.
 *
 * <p>The business goal is to store a block-info grid and optional description
 * lines for editor previews, recipe viewers, or documentation UIs. Instances are
 * mutable through editor configurators and should be confined to the UI/loading
 * thread that owns them.</p>
 */
@Getter
@Setter
public class MultiblockShapeInfo implements IConfigurable, ITagSerializable<CompoundTag> {

    private BlockInfo[][][] blocks;
    @Configurable(name = "editor.machine.multiblock.multiblock_shape_info.description", tips = "editor.machine.multiblock.multiblock_shape_info.description.tips")
    private List<String> description = new ArrayList<>();

    /**
     * Empty constructor for deserialization/editor creation.
     */
    protected MultiblockShapeInfo() {

    }

    /**
     * Creates shape info from serialized NBT.
     *
     * @param tag serialized shape payload
     * @return deserialized shape info
     */
    public static MultiblockShapeInfo loadFromTag(CompoundTag tag) {
        var info = new MultiblockShapeInfo();
        info.deserializeNBT(tag);
        return info;
    }

    /**
     * Creates shape info from a block grid and description.
     *
     * @param blocks block grid indexed by x/y/z
     * @param description descriptive text lines copied into this shape
     */
    public MultiblockShapeInfo(BlockInfo[][][] blocks, List<String> description) {
        this.blocks = blocks;
        this.description.addAll(description);
    }

    /**
     * Creates shape info without description lines.
     *
     * @param blocks block grid indexed by x/y/z
     */
    public MultiblockShapeInfo(BlockInfo[][][] blocks) {
        this(blocks, Collections.emptyList());
    }

    /**
     * Starts a symbolic shape-info builder.
     *
     * @return new mutable builder
     */
    public static ShapeInfoBuilder builder() {
        return new ShapeInfoBuilder();
    }

    /**
     * Serializes the block grid and description.
     *
     * <p>Side effects: none. Controller block infos are encoded with only their
     * facing marker so they can be restored as {@link ControllerBlockInfo}.</p>
     *
     * @return NBT payload containing dimensions, block data, and description
     */
    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        tag.putInt("x", blocks.length);
        tag.putInt("y", blocks[0].length);
        tag.putInt("z", blocks[0][0].length);
        var blocks = new ListTag();
        for (var block : this.blocks) {
            for (var blockInfos : block) {
                for (var blockInfo : blockInfos) {
                    if (blockInfo instanceof ControllerBlockInfo controllerBlockInfo) {
                        var facing = controllerBlockInfo.getFacing();
                        var controllerTag = new CompoundTag();
                        controllerTag.putString("facing", facing == null ? Direction.NORTH.getSerializedName() : facing.getSerializedName());
                        blocks.add(controllerTag);
                    } else {
                        blocks.add(blockInfo.serializeNBT());
                    }
                }
            }
        }
        tag.put("blocks", blocks);
        var description = new ListTag();
        for (var desc : this.description) {
            description.add(StringTag.valueOf(desc));
        }
        tag.put("description", description);
        return tag;
    }

    /**
     * Restores the block grid and description from NBT.
     *
     * <p>Preconditions: the tag must contain dimensions and a block list with
     * {@code x * y * z} entries. Side effects: replaces current blocks and
     * description.</p>
     *
     * @param tag payload produced by {@link #serializeNBT()}
     */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        var x = tag.getInt("x");
        var y = tag.getInt("y");
        var z = tag.getInt("z");
        var blocks = new BlockInfo[x][y][z];
        var blockList = (ListTag) tag.get("blocks");
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                for (int k = 0; k < z; k++) {
                    var blockInfoTag = (CompoundTag) blockList.get(i * y * z + j * z + k);
                    if (blockInfoTag.contains("facing")) {
                        var controllerBlockInfo = new ControllerBlockInfo(Direction.byName(blockInfoTag.getString("facing")));
                        blocks[i][j][k] = controllerBlockInfo;
                    } else {
                        var blockInfo = new BlockInfo();
                        blockInfo.deserializeNBT(blockInfoTag);
                        blocks[i][j][k] = blockInfo;
                    }
                }
            }
        }
        this.blocks = blocks;
        var description = new ArrayList<String>();
        var descriptionList = tag.getList("description", Tag.TAG_STRING);
        for (var desc : descriptionList) {
            description.add(desc.getAsString());
        }
        this.description = description;
    }

    /**
     * Builder for symbolic multiblock preview shapes.
     *
     * <p>The business goal is to mirror the string-based pattern-building style
     * while producing {@link BlockInfo} grids for display rather than
     * {@link TraceabilityPredicate} grids for matching.</p>
     */
    public static class ShapeInfoBuilder extends Builder<BlockInfo, ShapeInfoBuilder> {

        /**
         * Maps a symbol to a concrete block state.
         *
         * @param symbol pattern symbol
         * @param blockState block state displayed for that symbol
         * @return this builder for chaining
         */
        public ShapeInfoBuilder where(char symbol, BlockState blockState) {
            return where(symbol, BlockInfo.fromBlockState(blockState));
        }

        /**
         * Maps a symbol to a block supplied lazily.
         *
         * @param symbol pattern symbol
         * @param block supplier returning the displayed block
         * @return this builder for chaining
         */
        public ShapeInfoBuilder where(char symbol, Supplier<? extends Block> block) {
            return where(symbol, block.get());
        }

        /**
         * Maps a symbol to a block's default state.
         *
         * @param symbol pattern symbol
         * @param block displayed block
         * @return this builder for chaining
         */
        public ShapeInfoBuilder where(char symbol, Block block) {
            return where(symbol, block.defaultBlockState());
        }

        /**
         * Converts the symbolic builder contents into a dense block-info grid.
         *
         * @return baked block-info grid using {@link BlockInfo#EMPTY} for blanks
         */
        private BlockInfo[][][] bake() {
            return this.bakeArray(BlockInfo.class, BlockInfo.EMPTY);
        }

        /**
         * Builds shape info from the current symbolic grid.
         *
         * @return shape info with no description lines
         */
        public MultiblockShapeInfo build() {
            return new MultiblockShapeInfo(bake());
        }

    }

}
