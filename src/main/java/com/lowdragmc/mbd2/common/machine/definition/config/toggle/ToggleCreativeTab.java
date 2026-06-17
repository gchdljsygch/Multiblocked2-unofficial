package com.lowdragmc.mbd2.common.machine.definition.config.toggle;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.SelectorConfigurator;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;

/**
 * Toggle wrapper for the creative-mode tab assigned to a machine item.
 * <p>
 * Disabled creative tab settings hide or omit the item from configured creative
 * tab placement, depending on the item registration code using this value. The
 * stored id must be a key from {@link BuiltInRegistries#CREATIVE_MODE_TAB}.
 */
public class ToggleCreativeTab extends ToggleObject<ResourceLocation> {
    /**
     * Default creative tab id used when the setting is enabled without a custom
     * tab.
     */
    public static final ResourceLocation DEFAULT = ResourceLocation.tryParse("redstone_blocks");
    @Getter
    @Setter
    @Persisted
    private ResourceLocation value;

    /**
     * Creates a creative-tab toggle.
     *
     * @param value  creative tab registry id
     * @param enable whether the item should use the stored tab id
     */
    public ToggleCreativeTab(ResourceLocation value, boolean enable) {
        setValue(value);
        this.enable = enable;
    }

    /**
     * Creates an enabled creative-tab toggle.
     *
     * @param value creative tab registry id
     */
    public ToggleCreativeTab(ResourceLocation value) {
        this(value, true);
    }

    /**
     * Creates a toggle using {@link #DEFAULT}.
     *
     * @param enable initial enabled state
     */
    public ToggleCreativeTab(boolean enable) {
        this(DEFAULT, enable);
    }

    /**
     * Creates a disabled creative-tab toggle.
     */
    public ToggleCreativeTab() {
        this(false);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        father.addConfigurators(new SelectorConfigurator<>("value", this::getValue, this::setValue, DEFAULT, true,
                new ArrayList<>(BuiltInRegistries.CREATIVE_MODE_TAB.keySet()), ResourceLocation::toString));
    }
}
