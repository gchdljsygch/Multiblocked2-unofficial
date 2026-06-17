package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.configurator.AABBConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Toggle wrapper for a voxel shape stored as editable AABB boxes.
 * <p>
 * Machine states use this to override inherited collision/selection shapes.
 * The editor operates on the {@link #aabbs} list, while runtime callers receive
 * a lazily rebuilt {@link VoxelShape}. Any edit to the AABB list invalidates
 * the cached shape.
 * <p>
 * Coordinates are block-local and are typically in {@code 0..1}, although the
 * serializer preserves whatever box coordinates the editor stores.
 */
public class ToggleShape extends ToggleObject<VoxelShape> implements ITagSerializable<CompoundTag> {
    /**
     * Full block box used as the default editor cube.
     */
    public static final AABB BLOCK = new AABB(0, 0, 0, 1, 1, 1);
    private final List<AABB> aabbs = new ArrayList<>();
    // run-time
    private VoxelShape value;

    /**
     * Creates a shape toggle from a voxel shape.
     *
     * @param value  shape whose boxes should be copied into editable storage
     * @param enable whether this shape should override inherited/default shape
     */
    public ToggleShape(VoxelShape value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled shape toggle.
     *
     * @param value shape whose boxes should be copied into editable storage
     */
    public ToggleShape(VoxelShape value) {
        this(value, true);
    }

    /**
     * Creates a toggle storing a full-block shape.
     *
     * @param enable initial enabled state
     */
    public ToggleShape(boolean enable) {
        this(Shapes.block(), enable);
    }

    /**
     * Creates a disabled shape toggle.
     */
    public ToggleShape() {
        this(false);
    }

    /**
     * Returns the cached voxel shape, rebuilding it from editable boxes when
     * necessary.
     *
     * @return union of all stored AABBs, or an empty shape when no boxes exist
     */
    public VoxelShape getValue() {
        if (value == null) {
            value = aabbs.stream().map(Shapes::create).reduce(Shapes.empty(), Shapes::or);
        }
        return value;
    }

    /**
     * Replaces the editable boxes with the boxes from a voxel shape and
     * invalidates the runtime cache.
     *
     * @param value source shape to decompose into AABBs
     */
    @Override
    public void setValue(VoxelShape value) {
        this.value = null;
        this.aabbs.clear();
        this.aabbs.addAll(value.toAabbs());
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        var shapeGroup = new ArrayConfiguratorGroup<>("shapes", false, () -> new ArrayList<>(aabbs),
                (getter, setter) -> new AABBConfigurator("cube", getter, setter, BLOCK, true), true);
        shapeGroup.setAddDefault(() -> new AABB(0, 0, 0, 1, 1, 1));
        shapeGroup.setOnAdd(aabbs::add);
        shapeGroup.setOnRemove(aabbs::remove);
        shapeGroup.setOnUpdate(list -> {
            aabbs.clear();
            aabbs.addAll(list);
            value = null;
        });
        father.addConfigurators(shapeGroup);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        tag.putBoolean("enable", enable);
        var shape = new ListTag();
        for (AABB aabb : aabbs) {
            var aabbTag = new CompoundTag();
            aabbTag.putDouble("minX", aabb.minX);
            aabbTag.putDouble("minY", aabb.minY);
            aabbTag.putDouble("minZ", aabb.minZ);
            aabbTag.putDouble("maxX", aabb.maxX);
            aabbTag.putDouble("maxY", aabb.maxY);
            aabbTag.putDouble("maxZ", aabb.maxZ);
            shape.add(aabbTag);
        }
        tag.put("value", shape);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag compoundTag) {
        enable = compoundTag.getBoolean("enable");
        this.aabbs.clear();
        var shape = compoundTag.getList("value", Tag.TAG_COMPOUND);
        for (Tag tag : shape) {
            var aabbTag = (CompoundTag) tag;
            var aabb = new AABB(
                    aabbTag.getDouble("minX"),
                    aabbTag.getDouble("minY"),
                    aabbTag.getDouble("minZ"),
                    aabbTag.getDouble("maxX"),
                    aabbTag.getDouble("maxY"),
                    aabbTag.getDouble("maxZ")
            );
            this.aabbs.add(aabb);
        }
    }
}
