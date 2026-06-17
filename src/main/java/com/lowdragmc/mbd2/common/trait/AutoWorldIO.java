package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.DefaultValue;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.ShapeUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration block for traits that automatically interact with the world.
 *
 * <p>The range is machine-relative and rotated according to the machine front when queried. Interval and speed are
 * positive editor-configured values whose exact meaning is defined by the consuming trait, commonly ticks between
 * transfers and maximum items/fluids/entities handled per pass.</p>
 */
@Setter
@Getter
public class AutoWorldIO implements IToggleConfigurable {
    @Persisted
    public boolean enable;
    @Configurable(name = "config.definition.trait.auto_world_io.range", tips = "config.definition.trait.auto_world_io.range.tooltip")
    @DefaultValue(numberValue = {-1, -1, -1, 2, 2, 2})
    @Accessors(chain = true)
    public AABB range = new AABB(-1, -1, -1, 2, 2, 2);
    @Configurable(name = "config.definition.trait.auto_world_io.interval", tips = "config.definition.trait.auto_world_io.interval.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    @Accessors(chain = true)
    public int interval = 20;
    @Configurable(name = "config.definition.trait.auto_world_io.speed", tips = "config.definition.trait.auto_world_io.speed.tooltip")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    @Accessors(chain = true)
    public int speed = 64;

    // runtime
    private final Map<Direction, AABB> rangeCache = new EnumMap<>(Direction.class);

    /**
     * Returns the configured range rotated for a machine front.
     *
     * <p>{@code null} and {@link Direction#NORTH} return the stored range directly. Other directions are cached after
     * rotation. The returned AABB is shared config/cache state and should be treated as immutable by callers.</p>
     *
     * @param direction machine front direction, or {@code null} for the unrotated range
     * @return machine-relative interaction range for that direction
     */
    public AABB getRotatedRange(Direction direction) {
        return (direction == Direction.NORTH || direction == null) ? range : this.rangeCache.computeIfAbsent(direction, dir -> ShapeUtils.rotate(range, dir));
    }
}
